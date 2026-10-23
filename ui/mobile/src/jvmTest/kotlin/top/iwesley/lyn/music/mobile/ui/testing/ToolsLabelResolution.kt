package top.iwesley.lyn.music.mobile.ui.testing

import top.iwesley.lyn.music.mobile.ui.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.data.repository.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.player.*
import top.iwesley.lyn.music.feature.playlists.*
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.resources.*

internal suspend fun remoteSourceEditorTrackCountLabel(
    indexMode: ImportSourceIndexMode,
    currentTrackCount: Int?,
    remoteTrackCount: Int?,
): String = resolveUiText(top.iwesley.lyn.music.mobile.ui.remoteSourceEditorTrackCountLabelText(indexMode, currentTrackCount, remoteTrackCount), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun navidromeAudioQualityLabel(quality: NavidromeAudioQuality): String = resolveUiText(top.iwesley.lyn.music.mobile.ui.navidromeAudioQualityLabelText(quality), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun deviceInfoDensityValue(
    density: Float?,
    loading: Boolean,
): String = resolveUiText(top.iwesley.lyn.music.mobile.ui.deviceInfoDensityValueText(density, loading), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun deviceInfoDpResolutionValue(
    widthPx: Int?,
    heightPx: Int?,
    density: Float?,
    loading: Boolean,
): String = resolveUiText(top.iwesley.lyn.music.mobile.ui.deviceInfoDpResolutionValueText(widthPx, heightPx, density, loading), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun deviceInfoFontScaleValue(fontScale: Float): String = resolveUiText(top.iwesley.lyn.music.mobile.ui.deviceInfoFontScaleValueText(fontScale), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun buildLyricsShareFontButtonLabel(
    selectedFontKey: String?,
    selectedFontDisplayName: String? = null,
    availableFonts: List<LyricsShareFontOption>,
    isAndroid: Boolean = false,
): String = resolveUiText(top.iwesley.lyn.music.mobile.ui.buildLyricsShareFontButtonLabelText(selectedFontKey, selectedFontDisplayName, availableFonts, isAndroid), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun formatPlaylistImportFailedLineIssue(issue: PlaylistImportFailedLineIssue): String = resolveUiText(top.iwesley.lyn.music.mobile.ui.formatPlaylistImportFailedLineIssueText(issue), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun playlistImportReportSummary(report: PlaylistImportReport): String = resolveUiText(top.iwesley.lyn.music.mobile.ui.playlistImportReportSummaryText(report), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun formatTrackTechnicalSummary(track: Track): String = resolveUiText(top.iwesley.lyn.music.formatTrackTechnicalSummaryText(track), AppLanguageRuntime.effectiveLanguage.value)
internal suspend fun lyricsShareCopyMenuLabels(): List<String> = top.iwesley.lyn.music.mobile.ui.lyricsShareCopyMenuTexts().map { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) }
internal suspend fun LyricsShareFontOption.uiDisplayName(): String = displayNameText?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) } ?: displayName
internal suspend fun buildLyricsShareFontMenuIndexEntries(availableFonts: List<LyricsShareFontOption>): List<LyricsShareFontMenuIndexEntry> =
    top.iwesley.lyn.music.mobile.ui.buildLyricsShareFontMenuIndexEntries(availableFonts, availableFonts.map { it.uiDisplayName() })
