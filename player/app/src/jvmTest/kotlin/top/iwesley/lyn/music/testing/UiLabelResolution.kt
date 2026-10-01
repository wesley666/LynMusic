package top.iwesley.lyn.music.testing

import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.data.repository.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.player.*
import top.iwesley.lyn.music.feature.playlists.*
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.resources.*

/** Test entry points share the production descriptors and only add suspend resolution. */
internal suspend fun libraryLoadMoreStatusLabel(count: LibraryBrowserCount): String = resolveUiText(top.iwesley.lyn.music.libraryLoadMoreStatusLabelText(count), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun libraryFolderDetailSubtitle(folder: LibraryFolderNode): String = resolveUiText(top.iwesley.lyn.music.libraryFolderDetailSubtitleText(folder), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun libraryFolderSummaryLabel(folder: LibraryFolderNode): String = resolveUiText(top.iwesley.lyn.music.libraryFolderSummaryLabelText(folder), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun remoteSourceEditorTrackCountLabel(
    indexMode: ImportSourceIndexMode,
    currentTrackCount: Int?,
    remoteTrackCount: Int?,
): String = resolveUiText(top.iwesley.lyn.music.remoteSourceEditorTrackCountLabelText(indexMode, currentTrackCount, remoteTrackCount), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun navidromeAudioQualityLabel(quality: NavidromeAudioQuality): String = resolveUiText(top.iwesley.lyn.music.navidromeAudioQualityLabelText(quality), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun deviceInfoDensityValue(
    density: Float?,
    loading: Boolean,
): String = resolveUiText(top.iwesley.lyn.music.deviceInfoDensityValueText(density, loading), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun deviceInfoDpResolutionValue(
    widthPx: Int?,
    heightPx: Int?,
    density: Float?,
    loading: Boolean,
): String = resolveUiText(top.iwesley.lyn.music.deviceInfoDpResolutionValueText(widthPx, heightPx, density, loading), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun deviceInfoFontScaleValue(fontScale: Float): String = resolveUiText(top.iwesley.lyn.music.deviceInfoFontScaleValueText(fontScale), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun artistSummaryLabel(
    trackCount: Int?,
    albumCount: Int?,
    unknownLabel: String? = null,
): String = resolveUiText(top.iwesley.lyn.music.artistSummaryLabelText(trackCount, albumCount, unknownLabel), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun navidromeDownloadMenuLabel(
    quality: NavidromeAudioQuality,
    download: OfflineDownload?,
): String = resolveUiText(top.iwesley.lyn.music.navidromeDownloadMenuLabelText(quality, download), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun offlineAvailableSpaceLabel(
    availableSpaceBytes: Long?,
    loading: Boolean,
): String = resolveUiText(top.iwesley.lyn.music.offlineAvailableSpaceLabelText(availableSpaceBytes, loading), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun compactPlayerOfflineDownloadStatusLabel(download: OfflineDownload?): String = resolveUiText(top.iwesley.lyn.music.compactPlayerOfflineDownloadStatusLabelText(download), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun formatCurrentNavidromePlaybackAudioQuality(
    track: Track,
    audioQuality: NavidromeAudioQuality?,
): String? = top.iwesley.lyn.music.formatCurrentNavidromePlaybackAudioQualityText(track, audioQuality)?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) }

internal suspend fun sleepTimerStatusText(sleepTimer: SleepTimerState): String = resolveUiText(top.iwesley.lyn.music.sleepTimerStatusTextText(sleepTimer), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun mobileLibraryHubSearchPlaceholder(tab: AppTab): String = resolveUiText(top.iwesley.lyn.music.mobileLibraryHubSearchPlaceholderText(tab), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun buildLyricsShareFontButtonLabel(
    selectedFontKey: String?,
    selectedFontDisplayName: String? = null,
    availableFonts: List<LyricsShareFontOption>,
    isAndroid: Boolean = false,
): String = resolveUiText(top.iwesley.lyn.music.buildLyricsShareFontButtonLabelText(selectedFontKey, selectedFontDisplayName, availableFonts, isAndroid), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun formatPlaylistImportFailedLineIssue(issue: PlaylistImportFailedLineIssue): String = resolveUiText(top.iwesley.lyn.music.formatPlaylistImportFailedLineIssueText(issue), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun playlistImportReportSummary(report: PlaylistImportReport): String = resolveUiText(top.iwesley.lyn.music.playlistImportReportSummaryText(report), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun formatTrackTechnicalSummary(track: Track): String = resolveUiText(top.iwesley.lyn.music.formatTrackTechnicalSummaryText(track), AppLanguageRuntime.effectiveLanguage.value)
internal suspend fun resolveMiniPlayerLyricsText(lyrics: LyricsDocument?, highlightedLineIndex: Int, isLyricsLoading: Boolean): String? =
    top.iwesley.lyn.music.resolveMiniPlayerLyricsDescription(lyrics, highlightedLineIndex, isLyricsLoading)?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) }
internal suspend fun hasMiniPlayerLyricsContent(showPortraitLyrics: Boolean, lyricsText: String?): Boolean =
    top.iwesley.lyn.music.hasMiniPlayerLyricsContent(showPortraitLyrics, lyricsText, resolveUiString(top.iwesley.lyn.music.resources.Res.string.player_preparing_lyrics))
internal suspend fun formatAndroidCurrentPlaybackAudioQuality(track: Track, audioFormat: PlaybackAudioFormat?, navidromeQuality: NavidromeAudioQuality?): String? =
    top.iwesley.lyn.music.formatAndroidCurrentPlaybackAudioQualityText(track, audioFormat, navidromeQuality)?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) }
internal suspend fun lyricsShareCopyMenuLabels(): List<String> = top.iwesley.lyn.music.lyricsShareCopyMenuTexts().map { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) }
internal suspend fun LyricsShareFontOption.uiDisplayName(): String = displayNameText?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) } ?: displayName
internal suspend fun buildLyricsShareFontMenuIndexEntries(availableFonts: List<LyricsShareFontOption>): List<LyricsShareFontMenuIndexEntry> =
    top.iwesley.lyn.music.buildLyricsShareFontMenuIndexEntries(availableFonts, availableFonts.map { it.uiDisplayName() })


internal suspend fun importScanProgressLabel(progress: ImportScanProgress): String = resolveUiText(top.iwesley.lyn.music.importScanProgressLabelText(progress), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun importSourceTrackCountLabel(
    indexMode: ImportSourceIndexMode,
    localTrackCount: Int?,
    remoteTrackCount: Int?,
): String = resolveUiText(top.iwesley.lyn.music.importSourceTrackCountLabelText(indexMode, localTrackCount, remoteTrackCount), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun phoneLibraryRootLabel(root: LibraryBrowserRootView): String = resolveUiText(top.iwesley.lyn.music.phoneLibraryRootLabelText(root), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun mobileLibraryHubTabLabel(tab: AppTab): String = resolveUiText(top.iwesley.lyn.music.mobileLibraryHubTabLabelText(tab), AppLanguageRuntime.effectiveLanguage.value)
