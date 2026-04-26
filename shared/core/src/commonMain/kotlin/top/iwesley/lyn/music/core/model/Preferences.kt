package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppDisplayScalePreset(
    val scale: Float,
) {
    Compact(0.9f),
    Default(1.0f),
    Large(1.1f),
}

enum class NavidromeAudioQuality(
    val maxBitRateKbps: Int?,
) {
    Original(null),
    Kbps320(320),
    Kbps192(192),
    Kbps128(128),
}

val DEFAULT_NAVIDROME_WIFI_AUDIO_QUALITY: NavidromeAudioQuality = NavidromeAudioQuality.Original
val DEFAULT_NAVIDROME_MOBILE_AUDIO_QUALITY: NavidromeAudioQuality = NavidromeAudioQuality.Kbps192

fun appDisplayScalePresetOrDefault(name: String?): AppDisplayScalePreset {
    return AppDisplayScalePreset.entries.firstOrNull { it.name == name } ?: AppDisplayScalePreset.Default
}

fun navidromeAudioQualityOrDefault(
    name: String?,
    default: NavidromeAudioQuality,
): NavidromeAudioQuality {
    return NavidromeAudioQuality.entries.firstOrNull { it.name == name } ?: default
}

fun effectiveAppDisplayDensity(
    baseDensity: Float,
    preset: AppDisplayScalePreset,
): Float {
    if (!baseDensity.isFinite() || baseDensity <= 0f) return baseDensity
    return baseDensity * preset.scale
}

interface SambaCachePreferencesStore {
    val useSambaCache: StateFlow<Boolean>

    suspend fun setUseSambaCache(enabled: Boolean)
}

interface ThemePreferencesStore {
    val selectedTheme: StateFlow<AppThemeId>
    val customThemeTokens: StateFlow<AppThemeTokens>
    val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences>

    suspend fun setSelectedTheme(themeId: AppThemeId)
    suspend fun setCustomThemeTokens(tokens: AppThemeTokens)
    suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette)
}

interface CompactPlayerLyricsPreferencesStore {
    val showCompactPlayerLyrics: StateFlow<Boolean>

    suspend fun setShowCompactPlayerLyrics(enabled: Boolean)
}

interface AutoPlayOnStartupPreferencesStore {
    val autoPlayOnStartup: StateFlow<Boolean>

    suspend fun setAutoPlayOnStartup(enabled: Boolean)
}

interface AppDisplayPreferencesStore {
    val appDisplayScalePreset: StateFlow<AppDisplayScalePreset>

    suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset)
}

interface NavidromeAudioQualityPreferencesStore {
    val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality>
    val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality>

    suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality)
    suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality)
}

interface LyricsShareFontPreferencesStore {
    val selectedLyricsShareFontKey: StateFlow<String?>

    suspend fun setSelectedLyricsShareFontKey(value: String?)
}

object UnsupportedCompactPlayerLyricsPreferencesStore : CompactPlayerLyricsPreferencesStore {
    private val mutableShowCompactPlayerLyrics = MutableStateFlow(false)

    override val showCompactPlayerLyrics: StateFlow<Boolean> = mutableShowCompactPlayerLyrics

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        mutableShowCompactPlayerLyrics.value = enabled
    }
}

object UnsupportedAutoPlayOnStartupPreferencesStore : AutoPlayOnStartupPreferencesStore {
    private val mutableAutoPlayOnStartup = MutableStateFlow(false)

    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        mutableAutoPlayOnStartup.value = enabled
    }
}

object UnsupportedAppDisplayPreferencesStore : AppDisplayPreferencesStore {
    private val mutableAppDisplayScalePreset = MutableStateFlow(AppDisplayScalePreset.Default)

    override val appDisplayScalePreset: StateFlow<AppDisplayScalePreset> = mutableAppDisplayScalePreset

    override suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset) {
        mutableAppDisplayScalePreset.value = preset
    }
}

object UnsupportedNavidromeAudioQualityPreferencesStore : NavidromeAudioQualityPreferencesStore {
    private val mutableWifiAudioQuality = MutableStateFlow(DEFAULT_NAVIDROME_WIFI_AUDIO_QUALITY)
    private val mutableMobileAudioQuality = MutableStateFlow(DEFAULT_NAVIDROME_MOBILE_AUDIO_QUALITY)

    override val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality> = mutableWifiAudioQuality
    override val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality> = mutableMobileAudioQuality

    override suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality) {
        mutableWifiAudioQuality.value = quality
    }

    override suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality) {
        mutableMobileAudioQuality.value = quality
    }
}

object UnsupportedLyricsShareFontPreferencesStore : LyricsShareFontPreferencesStore {
    private val mutableSelectedLyricsShareFontKey = MutableStateFlow<String?>(null)

    override val selectedLyricsShareFontKey: StateFlow<String?> = mutableSelectedLyricsShareFontKey

    override suspend fun setSelectedLyricsShareFontKey(value: String?) {
        mutableSelectedLyricsShareFontKey.value = value?.trim()?.takeIf { it.isNotBlank() }
    }
}

interface DesktopVlcPreferencesStore {
    val desktopVlcManualPath: StateFlow<String?>
    val desktopVlcAutoDetectedPath: StateFlow<String?>
    val desktopVlcEffectivePath: StateFlow<String?>

    suspend fun setDesktopVlcManualPath(path: String?)
    suspend fun setDesktopVlcAutoDetectedPath(path: String?)
}

object UnsupportedDesktopVlcPreferencesStore : DesktopVlcPreferencesStore {
    private val mutableManualPath = MutableStateFlow<String?>(null)
    private val mutableAutoDetectedPath = MutableStateFlow<String?>(null)
    private val mutableEffectivePath = MutableStateFlow<String?>(null)

    override val desktopVlcManualPath: StateFlow<String?> = mutableManualPath
    override val desktopVlcAutoDetectedPath: StateFlow<String?> = mutableAutoDetectedPath
    override val desktopVlcEffectivePath: StateFlow<String?> = mutableEffectivePath

    override suspend fun setDesktopVlcManualPath(path: String?) {
        mutableManualPath.value = path?.takeIf { it.isNotBlank() }
        mutableEffectivePath.value = mutableManualPath.value ?: mutableAutoDetectedPath.value
    }

    override suspend fun setDesktopVlcAutoDetectedPath(path: String?) {
        mutableAutoDetectedPath.value = path?.takeIf { it.isNotBlank() }
        mutableEffectivePath.value = mutableManualPath.value ?: mutableAutoDetectedPath.value
    }
}
