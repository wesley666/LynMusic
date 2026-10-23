package top.iwesley.lyn.music

import top.iwesley.lyn.music.feature.online.OnlineLibraryIntent
import top.iwesley.lyn.music.feature.online.OnlineFavoritesIntent
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsIntent

fun OnlineLibraryIntent.isPassiveLibrarySourceClear(): Boolean {
    return this is OnlineLibraryIntent.SelectSource && persist && sourceId.isNullOrBlank()
}

fun OnlineLibraryIntent.shouldClearRememberedOnlineLibrarySource(
    currentOnlineSourceId: String?,
    rememberedOnlineSourceId: String?,
): Boolean {
    return isPassiveLibrarySourceClear() &&
        currentOnlineSourceId == null &&
        !rememberedOnlineSourceId.isNullOrBlank()
}

fun OnlineLibraryIntent.shouldStartOnlineLibraryStore(): Boolean {
    return when (this) {
        is OnlineLibraryIntent.SelectSource -> !sourceId.isNullOrBlank()
        OnlineLibraryIntent.ClearError -> false
        else -> true
    }
}

fun OnlineFavoritesIntent.isPassiveFavoritesSourceClear(): Boolean {
    return this is OnlineFavoritesIntent.SelectSource && persist && sourceId.isNullOrBlank()
}

fun OnlineFavoritesIntent.shouldClearRememberedOnlineFavoritesSource(
    currentOnlineSourceId: String?,
    rememberedOnlineSourceId: String?,
): Boolean {
    return isPassiveFavoritesSourceClear() &&
        currentOnlineSourceId == null &&
        !rememberedOnlineSourceId.isNullOrBlank()
}

fun OnlineFavoritesIntent.shouldStartOnlineFavoritesStore(): Boolean {
    return when (this) {
        is OnlineFavoritesIntent.SelectSource -> !sourceId.isNullOrBlank()
        OnlineFavoritesIntent.ClearMessage -> false
        else -> true
    }
}

fun OnlinePlaylistsIntent.isPassivePlaylistsSourceClear(): Boolean {
    return this is OnlinePlaylistsIntent.SelectSource && persist && sourceId.isNullOrBlank()
}

fun OnlinePlaylistsIntent.shouldClearRememberedOnlinePlaylistsSource(
    currentOnlineSourceId: String?,
    rememberedOnlineSourceId: String?,
): Boolean {
    return isPassivePlaylistsSourceClear() &&
        currentOnlineSourceId == null &&
        !rememberedOnlineSourceId.isNullOrBlank()
}

fun OnlinePlaylistsIntent.shouldStartOnlinePlaylistsStore(): Boolean {
    return when (this) {
        is OnlinePlaylistsIntent.SelectSource -> !sourceId.isNullOrBlank()
        is OnlinePlaylistsIntent.SelectPlaylist -> playlistId != null
        OnlinePlaylistsIntent.ClearMessage,
        OnlinePlaylistsIntent.ClearPlaylistImportReport -> false
        else -> true
    }
}
