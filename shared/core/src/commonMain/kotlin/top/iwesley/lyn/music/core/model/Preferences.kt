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

enum class PlayerArtworkStyle {
    VINYL,
    HALF_RECORD,
    MINIMAL_COVER,
}

val DEFAULT_NAVIDROME_WIFI_AUDIO_QUALITY: NavidromeAudioQuality = NavidromeAudioQuality.Original
val DEFAULT_NAVIDROME_MOBILE_AUDIO_QUALITY: NavidromeAudioQuality = NavidromeAudioQuality.Kbps192
const val DEFAULT_ANDROID_EXTENSION_DECODER_ENABLED: Boolean = false
const val DEFAULT_MINIMIZE_WINDOW_ON_CLOSE: Boolean = true
val DEFAULT_PLAYER_ARTWORK_STYLE: PlayerArtworkStyle = PlayerArtworkStyle.VINYL

fun appDisplayScalePresetOrDefault(name: String?): AppDisplayScalePreset {
    return AppDisplayScalePreset.entries.firstOrNull { it.name == name } ?: AppDisplayScalePreset.Default
}

fun navidromeAudioQualityOrDefault(
    name: String?,
    default: NavidromeAudioQuality,
): NavidromeAudioQuality {
    return NavidromeAudioQuality.entries.firstOrNull { it.name == name } ?: default
}

fun playerArtworkStyleOrDefault(name: String?): PlayerArtworkStyle {
    return PlayerArtworkStyle.entries.firstOrNull { it.name == name } ?: DEFAULT_PLAYER_ARTWORK_STYLE
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

interface DesktopLyricsPreferencesStore {
    val showDesktopLyrics: StateFlow<Boolean>

    suspend fun setShowDesktopLyrics(enabled: Boolean)
}

interface DesktopLyricsPositionPreferencesStore {
    val desktopLyricsPosition: StateFlow<DesktopLyricsPosition?>

    suspend fun setDesktopLyricsPosition(position: DesktopLyricsPosition)
}

interface MenuBarLyricsControlsPreferencesStore {
    val showMenuBarLyricsControls: StateFlow<Boolean>

    suspend fun setShowMenuBarLyricsControls(enabled: Boolean)
}

interface AutoPlayOnStartupPreferencesStore {
    val autoPlayOnStartup: StateFlow<Boolean>

    suspend fun setAutoPlayOnStartup(enabled: Boolean)
}

interface AutoOpenPlayerOnStartupPreferencesStore {
    val autoOpenPlayerOnStartup: StateFlow<Boolean>

    suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean)
}

interface WindowClosePreferencesStore {
    val minimizeWindowOnClose: StateFlow<Boolean>

    suspend fun setMinimizeWindowOnClose(enabled: Boolean)
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

interface PlaybackDecoderPreferencesStore {
    val useAndroidExtensionDecoder: StateFlow<Boolean>

    suspend fun setUseAndroidExtensionDecoder(enabled: Boolean)
}

interface PlayerArtworkStylePreferencesStore {
    val playerArtworkStyle: StateFlow<PlayerArtworkStyle>

    suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle)
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

object UnsupportedDesktopLyricsPreferencesStore : DesktopLyricsPreferencesStore {
    private val mutableShowDesktopLyrics = MutableStateFlow(false)

    override val showDesktopLyrics: StateFlow<Boolean> = mutableShowDesktopLyrics

    override suspend fun setShowDesktopLyrics(enabled: Boolean) {
        mutableShowDesktopLyrics.value = enabled
    }
}

object UnsupportedMenuBarLyricsControlsPreferencesStore : MenuBarLyricsControlsPreferencesStore {
    private val mutableShowMenuBarLyricsControls = MutableStateFlow(false)

    override val showMenuBarLyricsControls: StateFlow<Boolean> = mutableShowMenuBarLyricsControls

    override suspend fun setShowMenuBarLyricsControls(enabled: Boolean) {
        mutableShowMenuBarLyricsControls.value = enabled
    }
}

object UnsupportedAutoPlayOnStartupPreferencesStore : AutoPlayOnStartupPreferencesStore {
    private val mutableAutoPlayOnStartup = MutableStateFlow(false)

    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        mutableAutoPlayOnStartup.value = enabled
    }
}

object UnsupportedAutoOpenPlayerOnStartupPreferencesStore : AutoOpenPlayerOnStartupPreferencesStore {
    private val mutableAutoOpenPlayerOnStartup = MutableStateFlow(false)

    override val autoOpenPlayerOnStartup: StateFlow<Boolean> = mutableAutoOpenPlayerOnStartup

    override suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean) {
        mutableAutoOpenPlayerOnStartup.value = enabled
    }
}

object UnsupportedWindowClosePreferencesStore : WindowClosePreferencesStore {
    private val mutableMinimizeWindowOnClose = MutableStateFlow(DEFAULT_MINIMIZE_WINDOW_ON_CLOSE)

    override val minimizeWindowOnClose: StateFlow<Boolean> = mutableMinimizeWindowOnClose

    override suspend fun setMinimizeWindowOnClose(enabled: Boolean) {
        mutableMinimizeWindowOnClose.value = enabled
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

object UnsupportedPlaybackDecoderPreferencesStore : PlaybackDecoderPreferencesStore {
    private val mutableUseAndroidExtensionDecoder =
        MutableStateFlow(DEFAULT_ANDROID_EXTENSION_DECODER_ENABLED)

    override val useAndroidExtensionDecoder: StateFlow<Boolean> = mutableUseAndroidExtensionDecoder

    override suspend fun setUseAndroidExtensionDecoder(enabled: Boolean) {
        mutableUseAndroidExtensionDecoder.value = enabled
    }
}

object UnsupportedPlayerArtworkStylePreferencesStore : PlayerArtworkStylePreferencesStore {
    private val mutablePlayerArtworkStyle = MutableStateFlow(DEFAULT_PLAYER_ARTWORK_STYLE)

    override val playerArtworkStyle: StateFlow<PlayerArtworkStyle> = mutablePlayerArtworkStyle

    override suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle) {
        mutablePlayerArtworkStyle.value = style
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
