package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
