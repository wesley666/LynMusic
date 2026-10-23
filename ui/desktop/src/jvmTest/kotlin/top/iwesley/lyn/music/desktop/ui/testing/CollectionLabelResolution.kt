package top.iwesley.lyn.music.desktop.ui.testing

import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.desktop.ui.*
import top.iwesley.lyn.music.data.repository.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.player.*
import top.iwesley.lyn.music.feature.playlists.*
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.resources.*



internal suspend fun libraryLoadMoreStatusLabel(count: LibraryBrowserCount): String = resolveUiText(top.iwesley.lyn.music.desktop.ui.libraryLoadMoreStatusLabelText(count), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun libraryFolderDetailSubtitle(folder: LibraryFolderNode): String = resolveUiText(top.iwesley.lyn.music.desktop.ui.libraryFolderDetailSubtitleText(folder), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun libraryFolderSummaryLabel(folder: LibraryFolderNode): String = resolveUiText(top.iwesley.lyn.music.desktop.ui.libraryFolderSummaryLabelText(folder), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun artistSummaryLabel(
    trackCount: Int?,
    albumCount: Int?,
    unknownLabel: String? = null,
): String = resolveUiText(top.iwesley.lyn.music.desktop.ui.artistSummaryLabelText(trackCount, albumCount, unknownLabel), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun navidromeDownloadMenuLabel(
    quality: NavidromeAudioQuality,
    download: OfflineDownload?,
): String = resolveUiText(top.iwesley.lyn.music.desktop.ui.navidromeDownloadMenuLabelText(quality, download), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun offlineAvailableSpaceLabel(
    availableSpaceBytes: Long?,
    loading: Boolean,
): String = resolveUiText(top.iwesley.lyn.music.desktop.ui.offlineAvailableSpaceLabelText(availableSpaceBytes, loading), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun importScanProgressLabel(progress: ImportScanProgress): String = resolveUiText(top.iwesley.lyn.music.desktop.ui.importScanProgressLabelText(progress), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun importSourceTrackCountLabel(
    indexMode: ImportSourceIndexMode,
    localTrackCount: Int?,
    remoteTrackCount: Int?,
): String = resolveUiText(top.iwesley.lyn.music.desktop.ui.importSourceTrackCountLabelText(indexMode, localTrackCount, remoteTrackCount), AppLanguageRuntime.effectiveLanguage.value)
