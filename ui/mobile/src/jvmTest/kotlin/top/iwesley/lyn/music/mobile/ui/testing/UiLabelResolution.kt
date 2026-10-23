package top.iwesley.lyn.music.mobile.ui.testing

import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.data.repository.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.player.*
import top.iwesley.lyn.music.feature.playlists.*
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.resources.*


internal suspend fun compactPlayerOfflineDownloadStatusLabel(download: OfflineDownload?): String = resolveUiText(top.iwesley.lyn.music.mobile.ui.compactPlayerOfflineDownloadStatusLabelText(download), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun formatCurrentNavidromePlaybackAudioQuality(
    track: Track,
    audioQuality: NavidromeAudioQuality?,
): String? = top.iwesley.lyn.music.mobile.ui.formatCurrentNavidromePlaybackAudioQualityText(track, audioQuality)?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) }

internal suspend fun sleepTimerStatusText(sleepTimer: SleepTimerState): String = resolveUiText(top.iwesley.lyn.music.mobile.ui.sleepTimerStatusTextText(sleepTimer), AppLanguageRuntime.effectiveLanguage.value)

internal suspend fun resolveMiniPlayerLyricsText(lyrics: LyricsDocument?, highlightedLineIndex: Int, isLyricsLoading: Boolean): String? =
    top.iwesley.lyn.music.mobile.ui.resolveMiniPlayerLyricsDescription(lyrics, highlightedLineIndex, isLyricsLoading)?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) }
internal suspend fun hasMiniPlayerLyricsContent(showPortraitLyrics: Boolean, lyricsText: String?): Boolean =
    top.iwesley.lyn.music.mobile.ui.hasMiniPlayerLyricsContent(showPortraitLyrics, lyricsText, resolveUiString(top.iwesley.lyn.music.resources.Res.string.player_preparing_lyrics))
internal suspend fun formatAndroidCurrentPlaybackAudioQuality(track: Track, audioFormat: PlaybackAudioFormat?, navidromeQuality: NavidromeAudioQuality?): String? =
    top.iwesley.lyn.music.mobile.ui.formatAndroidCurrentPlaybackAudioQualityText(track, audioFormat, navidromeQuality)?.let { resolveUiText(it, AppLanguageRuntime.effectiveLanguage.value) }
