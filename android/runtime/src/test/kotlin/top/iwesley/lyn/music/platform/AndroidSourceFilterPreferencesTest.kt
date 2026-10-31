package top.iwesley.lyn.music.platform

import android.app.Application
import android.content.Context
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidSourceFilterPreferencesTest {
    @Test
    fun `conditional source updates reject stale values and preserve selection after reload`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("lynmusic.settings", Context.MODE_PRIVATE).edit().clear().commit()
        val store = AndroidAppPreferencesStore(context)
        val first = LibrarySourceFilter.Source("dav-a")
        val second = LibrarySourceFilter.Source("dav-b")
        for (favorites in listOf(false, true)) {
            val setFilter: suspend (LibrarySourceFilter) -> Unit =
                if (favorites) store::setFavoritesSourceFilter else store::setLibrarySourceFilter
            val compareAndSet: suspend (LibrarySourceFilter, LibrarySourceFilter) -> Boolean =
                if (favorites) store::compareAndSetFavoritesSourceFilter else store::compareAndSetLibrarySourceFilter
            val current = if (favorites) store.favoritesSourceFilter else store.librarySourceFilter
            setFilter(first)
            assertTrue(compareAndSet(first, LibrarySourceFilter.ALL))
            assertEquals(LibrarySourceFilter.ALL, current.value)
            setFilter(second)
            assertFalse(compareAndSet(first, LibrarySourceFilter.ALL))
            assertEquals(second, current.value)
            val reloaded = AndroidAppPreferencesStore(context)
            assertEquals(second, if (favorites) reloaded.favoritesSourceFilter.value else reloaded.librarySourceFilter.value)
        }
    }

    @Test
    fun `old protocol selections reset and source ids survive store reload`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("lynmusic.settings", Context.MODE_PRIVATE).edit()
            .clear()
            .putString("library_source_filter", "WEBDAV")
            .putString("favorites_source_filter", "NAVIDROME")
            .commit()
        val store = AndroidAppPreferencesStore(context)
        assertEquals(LibrarySourceFilter.ALL, store.librarySourceFilter.value)
        assertEquals(LibrarySourceFilter.ALL, store.favoritesSourceFilter.value)

        store.setLibrarySourceFilter(LibrarySourceFilter.Source("dav-1"))
        store.setFavoritesSourceFilter(LibrarySourceFilter.Source("nav-2"))
        val reloaded = AndroidAppPreferencesStore(context)
        assertEquals(LibrarySourceFilter.Source("dav-1"), reloaded.librarySourceFilter.value)
        assertEquals(LibrarySourceFilter.Source("nav-2"), reloaded.favoritesSourceFilter.value)
        reloaded.setLibrarySourceFilter(LibrarySourceFilter.LOCAL_FOLDER)
        reloaded.setFavoritesSourceFilter(LibrarySourceFilter.LOCAL_FOLDER)
        val localSelection = AndroidAppPreferencesStore(context)
        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, localSelection.librarySourceFilter.value)
        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, localSelection.favoritesSourceFilter.value)
    }

    @Test
    fun `all and downloaded preferences remain readable`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("lynmusic.settings", Context.MODE_PRIVATE).edit()
            .clear()
            .putString("library_source_filter", "ALL")
            .putString("favorites_source_filter", "DOWNLOADED")
            .commit()

        val store = AndroidAppPreferencesStore(context)
        assertEquals(LibrarySourceFilter.ALL, store.librarySourceFilter.value)
        assertEquals(LibrarySourceFilter.DOWNLOADED, store.favoritesSourceFilter.value)
    }
}
