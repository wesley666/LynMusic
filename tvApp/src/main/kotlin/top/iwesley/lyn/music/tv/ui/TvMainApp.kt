package top.iwesley.lyn.music.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import top.iwesley.lyn.music.LynMusicAppComponent
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.tv.ConfigureTvImageLoader
import top.iwesley.lyn.music.tv.TvAppComponentHolder
import top.iwesley.lyn.music.tv.TvPlayerActivity

@Composable
internal fun TvMainApp(
    component: LynMusicAppComponent,
) {
    ConfigureTvImageLoader()

    DisposableEffect(component) {
        TvAppComponentHolder.attach(component)
        onDispose {
            TvAppComponentHolder.detach(component)
            component.dispose()
        }
    }

    val scope = rememberCoroutineScope()
    val tvStore = remember(component) { TvMainStore(scope) }
    val tvState by tvStore.state.collectAsState()
    val myState by component.myStore.state.collectAsState()
    val libraryState by component.libraryStore.state.collectAsState()
    val favoritesState by component.favoritesStore.state.collectAsState()
    val playerState by component.playerStore.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(component, tvStore) {
        component.playerStore.startHydration()
        tvStore.dispatch(TvMainIntent.ActivateDestination(TvMainDestination.Library))
    }

    LaunchedEffect(component, tvStore) {
        tvStore.effects.collect { effect ->
            when (effect) {
                TvMainEffect.StartMy -> component.myStore.ensureStarted()
                TvMainEffect.StartLibrary -> component.libraryStore.ensureStarted()
                TvMainEffect.StartFavorites -> component.favoritesStore.ensureContentStarted()
                is TvMainEffect.SearchLibrary ->
                    component.libraryStore.dispatch(LibraryIntent.SearchChanged(effect.query))
                is TvMainEffect.SearchFavorites ->
                    component.favoritesStore.dispatch(FavoritesIntent.SearchChanged(effect.query))
                is TvMainEffect.PlayTracks -> {
                    component.playerStore.dispatch(PlayerIntent.PlayTracks(effect.tracks, effect.startIndex))
                    context.startActivity(TvPlayerActivity.createIntent(context))
                }
                is TvMainEffect.ToggleFavorite ->
                    component.favoritesStore.dispatch(FavoritesIntent.ToggleFavorite(effect.track))
            }
        }
    }

    TvMainTheme {
        TvMainScreen(
            state = tvState,
            myState = myState,
            libraryState = libraryState,
            favoritesState = favoritesState,
            playerState = playerState,
            artworkCacheStore = component.artworkCacheStore,
            onIntent = tvStore::dispatch,
            onPlayerIntent = component.playerStore::dispatch,
        )
    }
}
