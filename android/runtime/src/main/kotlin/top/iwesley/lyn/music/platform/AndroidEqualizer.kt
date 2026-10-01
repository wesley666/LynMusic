package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.EqualizerPlatformService
import top.iwesley.lyn.music.core.model.clampEqualizerLevel
import top.iwesley.lyn.music.core.model.error
import kotlin.math.roundToInt

data class AndroidEqualizerBand(
    val index: Int,
    val centerFrequencyHz: Int,
    val levelMb: Int,
)

data class AndroidEqualizerPreset(
    val name: String,
)

data class AndroidEqualizerState(
    val supported: Boolean = true,
    val active: Boolean = false,
    val enabled: Boolean = false,
    val minLevelMb: Int = DEFAULT_EQUALIZER_MIN_LEVEL_MB,
    val maxLevelMb: Int = DEFAULT_EQUALIZER_MAX_LEVEL_MB,
    val bands: List<AndroidEqualizerBand> = emptyList(),
    val presets: List<AndroidEqualizerPreset> = emptyList(),
    val selectedPresetName: String? = null,
    val errorMessage: UiText? = uiText(Res.string.equalizer_playback_required),
)

interface AndroidEqualizerUiService {
    val state: StateFlow<AndroidEqualizerState>

    suspend fun setEnabled(enabled: Boolean)
    suspend fun selectPreset(name: String?)
    suspend fun setBandLevel(centerFrequencyHz: Int, levelMb: Int)
    suspend fun reset()
    fun close()
}

class AndroidEqualizerActivityServices internal constructor(
    val equalizerService: AndroidEqualizerUiService,
    val appDisplayScalePreset: StateFlow<AppDisplayScalePreset>,
    val selectedTheme: StateFlow<AppThemeId>,
    val customThemeTokens: StateFlow<AppThemeTokens>,
    val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences>,
    private val preferencesStore: AndroidAppPreferencesStore,
) {
    fun close() {
        equalizerService.close()
        preferencesStore.close()
    }
}

fun createAndroidEqualizerActivityServices(context: Context): AndroidEqualizerActivityServices {
    val preferencesStore = AndroidAppPreferencesStore(context.applicationContext)
    return AndroidEqualizerActivityServices(
        equalizerService = AndroidEqualizerUiServiceImpl(preferencesStore),
        appDisplayScalePreset = preferencesStore.appDisplayScalePreset,
        selectedTheme = preferencesStore.selectedTheme,
        customThemeTokens = preferencesStore.customThemeTokens,
        textPalettePreferences = preferencesStore.textPalettePreferences,
        preferencesStore = preferencesStore,
    )
}

class AndroidEqualizerPlatformService(
    private val context: Context,
    private val platformName: String,
) : EqualizerPlatformService {
    override val isSupported: Boolean = true

    override fun openEqualizer() {
        val appContext = context.applicationContext
        val intent = Intent(appContext, AndroidEqualizerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_EQUALIZER_LOCK_PORTRAIT, platformName == ANDROID_EQUALIZER_PHONE_PLATFORM_NAME)
        appContext.startActivity(intent)
    }
}

internal interface AndroidEqualizerPreferencesStore {
    val equalizerEnabled: StateFlow<Boolean>
    val equalizerPresetName: StateFlow<String?>
    val equalizerBandLevels: StateFlow<Map<Int, Int>>

    suspend fun setEqualizerEnabled(enabled: Boolean)
    suspend fun setEqualizerPresetName(name: String?)
    suspend fun setEqualizerBandLevels(levels: Map<Int, Int>)
}

internal data class AndroidEqualizerPreferencesSnapshot(
    val enabled: Boolean,
    val presetName: String?,
    val bandLevels: Map<Int, Int>,
)

internal const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
internal const val KEY_EQUALIZER_PRESET_NAME = "equalizer_preset_name"
internal const val KEY_EQUALIZER_BAND_LEVELS = "equalizer_band_levels"
internal const val EXTRA_EQUALIZER_LOCK_PORTRAIT = "top.iwesley.lyn.music.extra.EQUALIZER_LOCK_PORTRAIT"
internal const val ANDROID_EQUALIZER_PHONE_PLATFORM_NAME = "Android"
internal const val ANDROID_EQUALIZER_AUTOMOTIVE_PLATFORM_NAME = "Android Automotive"

internal fun encodeAndroidEqualizerBandLevels(levels: Map<Int, Int>): String {
    return levels.toSortedMap()
        .entries
        .joinToString(",") { (frequency, level) -> "$frequency:$level" }
}

internal fun decodeAndroidEqualizerBandLevels(raw: String?): Map<Int, Int> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(',')
        .mapNotNull { entry ->
            val frequency = entry.substringBefore(':', missingDelimiterValue = "").toIntOrNull()
                ?: return@mapNotNull null
            val level = entry.substringAfter(':', missingDelimiterValue = "").toIntOrNull()
                ?: return@mapNotNull null
            frequency.takeIf { it > 0 }?.let { it to level }
        }
        .toMap()
}

internal class AndroidEqualizerController(
    private val preferencesStore: AndroidEqualizerPreferencesStore,
    private val logger: DiagnosticLogger,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var equalizerUnavailableForSession = false
    private var released = false
    private var currentPreferences = AndroidEqualizerPreferencesSnapshot(
        enabled = preferencesStore.equalizerEnabled.value,
        presetName = preferencesStore.equalizerPresetName.value,
        bandLevels = preferencesStore.equalizerBandLevels.value,
    )

    private val playerListener = @UnstableApi
    object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            runOnControllerThread {
                bindAudioSession(audioSessionId)
            }
        }
    }

    init {
        scope.launch {
            combine(
                preferencesStore.equalizerEnabled,
                preferencesStore.equalizerPresetName,
                preferencesStore.equalizerBandLevels,
            ) { enabled, presetName, bandLevels ->
                AndroidEqualizerPreferencesSnapshot(
                    enabled = enabled,
                    presetName = presetName,
                    bandLevels = bandLevels,
                )
            }.collect { preferences ->
                currentPreferences = preferences
                applyPreferences()
            }
        }
        publishInactive()
    }

    @OptIn(UnstableApi::class)
    fun attachPlayer(nextPlayer: ExoPlayer) {
        runOnControllerThread {
            if (released) return@runOnControllerThread
            player?.removeListener(playerListener)
            player = nextPlayer
            nextPlayer.addListener(playerListener)
            bindAudioSession(nextPlayer.audioSessionId)
        }
    }

    fun release() {
        runOnControllerThread {
            if (released) return@runOnControllerThread
            released = true
            player?.removeListener(playerListener)
            player = null
            releaseEqualizer()
            AndroidEqualizerRegistry.publish(AndroidEqualizerState(enabled = currentPreferences.enabled))
            scope.cancel()
        }
    }

    @OptIn(UnstableApi::class)
    private fun bindAudioSession(audioSessionId: Int) {
        equalizerUnavailableForSession = false
        releaseEqualizer()
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) {
            publishInactive()
            return
        }
        val nextEqualizer = runCatching {
            Equalizer(0, audioSessionId)
        }.onFailure { throwable ->
            logger.error(ANDROID_EQUALIZER_LOG_TAG, throwable) {
                "equalizer-create-failed sessionId=$audioSessionId"
            }
            equalizerUnavailableForSession = true
            publishUnavailable()
        }.getOrNull() ?: return
        equalizer = nextEqualizer
        applyPreferences()
    }

    private fun applyPreferences() {
        val eq = equalizer ?: run {
            if (equalizerUnavailableForSession) {
                publishUnavailable()
            } else {
                publishInactive()
            }
            return
        }
        runCatching {
            if (currentPreferences.enabled) {
                eq.setEnabled(true)
                val presetName = currentPreferences.presetName
                val presetIndex = if (presetName != null) findPresetIndex(eq, presetName) else null
                if (presetIndex != null) {
                    eq.usePreset(presetIndex.toShort())
                } else {
                    applyManualBandLevels(eq)
                }
            } else {
                eq.setEnabled(false)
            }
            AndroidEqualizerRegistry.publish(readState(eq, currentPreferences, errorMessage = null))
        }.onFailure { throwable ->
            logger.error(ANDROID_EQUALIZER_LOG_TAG, throwable) {
                "equalizer-apply-failed"
            }
            AndroidEqualizerRegistry.publish(
                readState(
                    eq = eq,
                    preferences = currentPreferences,
                    errorMessage = uiText(Res.string.equalizer_apply_failed),
                ),
            )
        }
    }

    private fun applyManualBandLevels(eq: Equalizer) {
        val range = eq.bandLevelRange
        val minLevel = range.getOrNull(0)?.toInt() ?: DEFAULT_EQUALIZER_MIN_LEVEL_MB
        val maxLevel = range.getOrNull(1)?.toInt() ?: DEFAULT_EQUALIZER_MAX_LEVEL_MB
        val bandCount = eq.numberOfBands.toInt().coerceAtLeast(0)
        for (index in 0 until bandCount) {
            val band = index.toShort()
            val centerFrequencyHz = eq.getCenterFreq(band).milliHzToHz()
            val level = currentPreferences.bandLevels[centerFrequencyHz] ?: 0
            eq.setBandLevel(band, clampEqualizerLevel(level, minLevel, maxLevel).toShort())
        }
    }

    private fun readState(
        eq: Equalizer,
        preferences: AndroidEqualizerPreferencesSnapshot,
        errorMessage: UiText?,
    ): AndroidEqualizerState {
        val range = eq.bandLevelRange
        val minLevel = range.getOrNull(0)?.toInt() ?: DEFAULT_EQUALIZER_MIN_LEVEL_MB
        val maxLevel = range.getOrNull(1)?.toInt() ?: DEFAULT_EQUALIZER_MAX_LEVEL_MB
        val bandCount = eq.numberOfBands.toInt().coerceAtLeast(0)
        val bands = (0 until bandCount).map { index ->
            val band = index.toShort()
            AndroidEqualizerBand(
                index = index,
                centerFrequencyHz = eq.getCenterFreq(band).milliHzToHz(),
                levelMb = runCatching { eq.getBandLevel(band).toInt() }.getOrDefault(0),
            )
        }
        val presets = (0 until eq.numberOfPresets.toInt().coerceAtLeast(0)).mapNotNull { index ->
            runCatching { AndroidEqualizerPreset(eq.getPresetName(index.toShort())) }.getOrNull()
        }
        val selectedPresetName = preferences.presetName?.takeIf { presetName ->
            presets.any { it.name == presetName }
        }
        return AndroidEqualizerState(
            supported = true,
            active = true,
            enabled = preferences.enabled,
            minLevelMb = minLevel,
            maxLevelMb = maxLevel,
            bands = bands,
            presets = presets,
            selectedPresetName = selectedPresetName,
            errorMessage = errorMessage,
        )
    }

    private fun publishInactive() {
        AndroidEqualizerRegistry.publish(
            AndroidEqualizerState(
                supported = true,
                active = false,
                enabled = currentPreferences.enabled,
                selectedPresetName = currentPreferences.presetName,
                errorMessage = uiText(Res.string.equalizer_playback_required),
            ),
        )
    }

    private fun publishUnavailable() {
        AndroidEqualizerRegistry.publish(
            AndroidEqualizerState(
                supported = false,
                active = false,
                enabled = currentPreferences.enabled,
                selectedPresetName = currentPreferences.presetName,
                errorMessage = uiText(Res.string.equalizer_device_unsupported),
            ),
        )
    }

    private fun releaseEqualizer() {
        equalizer?.release()
        equalizer = null
    }

    private fun findPresetIndex(eq: Equalizer, name: String): Int? {
        val count = eq.numberOfPresets.toInt().coerceAtLeast(0)
        return (0 until count).firstOrNull { index ->
            runCatching { eq.getPresetName(index.toShort()) == name }.getOrDefault(false)
        }
    }

    private fun runOnControllerThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            handler.post(block)
        }
    }
}

private class AndroidEqualizerUiServiceImpl(
    private val preferencesStore: AndroidEqualizerPreferencesStore,
) : AndroidEqualizerUiService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(
        AndroidEqualizerRegistry.state.value.copy(
            enabled = preferencesStore.equalizerEnabled.value,
            selectedPresetName = preferencesStore.equalizerPresetName.value,
        ),
    )

    override val state: StateFlow<AndroidEqualizerState> = mutableState.asStateFlow()

    init {
        scope.launch {
            combine(
                AndroidEqualizerRegistry.state,
                preferencesStore.equalizerEnabled,
                preferencesStore.equalizerPresetName,
            ) { runtimeState, enabled, presetName ->
                runtimeState.copy(
                    enabled = enabled,
                    selectedPresetName = presetName?.takeIf { name ->
                        runtimeState.presets.any { it.name == name }
                    },
                )
            }.collect { state ->
                mutableState.value = state
            }
        }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        preferencesStore.setEqualizerEnabled(enabled)
    }

    override suspend fun selectPreset(name: String?) {
        preferencesStore.setEqualizerPresetName(name)
    }

    override suspend fun setBandLevel(centerFrequencyHz: Int, levelMb: Int) {
        val state = mutableState.value
        val clampedLevel = clampEqualizerLevel(levelMb, state.minLevelMb, state.maxLevelMb)
        val nextLevels = preferencesStore.equalizerBandLevels.value.toMutableMap().apply {
            put(centerFrequencyHz, clampedLevel)
        }
        preferencesStore.setEqualizerPresetName(null)
        preferencesStore.setEqualizerBandLevels(nextLevels)
    }

    override suspend fun reset() {
        preferencesStore.setEqualizerPresetName(null)
        preferencesStore.setEqualizerBandLevels(
            mutableState.value.bands.associate { band -> band.centerFrequencyHz to 0 },
        )
    }

    override fun close() {
        scope.cancel()
    }
}

private object AndroidEqualizerRegistry {
    private val mutableState = MutableStateFlow(AndroidEqualizerState())
    val state: StateFlow<AndroidEqualizerState> = mutableState.asStateFlow()

    fun publish(state: AndroidEqualizerState) {
        mutableState.value = state
    }
}

private fun Int.milliHzToHz(): Int {
    return (this / 1_000f).roundToInt()
}

private const val DEFAULT_EQUALIZER_MIN_LEVEL_MB = -1_500
private const val DEFAULT_EQUALIZER_MAX_LEVEL_MB = 1_500
private const val ANDROID_EQUALIZER_LOG_TAG = "AndroidEqualizer"
