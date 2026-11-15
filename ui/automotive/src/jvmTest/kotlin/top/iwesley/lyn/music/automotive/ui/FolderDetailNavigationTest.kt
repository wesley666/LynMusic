package top.iwesley.lyn.music.automotive.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter

class FolderDetailNavigationTest {
    private val tree = deriveLibraryFolderTree(listOf(
        Track("one", "one", "Song", mediaLocator = "", relativePath = "Parent/Child/song.mp3"),
        Track("two", "two", "Song", mediaLocator = "", relativePath = "Parent/Child/song.mp3")),
        mapOf("one" to "One", "two" to "Two"))

    @Test fun rootAndNestedFoldersSerializeAndPopInOrderWithoutStartingDetailQueries() = runTest {
        val queries = TestMusicDetailQueries(album = { error("Folder navigation must not query albums") },
            artistTracks = { error("Folder navigation must not query artist tracks") })
        val owner = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), queries, this, mutableStateOf(false), {})
        val routes = listOf("", "Parent", "Parent/Child").map { path ->
            owner.openFolder(tree.nodesByKey.getValue(LibraryFolderKey("one", path)), LibrarySourceFilter.ALL)
            owner.top.also { assertEquals(it, Json.decodeFromString<MusicDetailRoute>(Json.encodeToString(it))) }
        }
        assertEquals("", routes.first().folderPath)
        assertEquals(4, owner.backStack.size)
        routes.asReversed().forEach { route -> assertEquals(route, owner.top); owner.requestBack() }
        assertFalse(owner.hasDetails)
    }

    @Test fun identicalPathsInDifferentSourcesRemainDistinctAndDoubleClicksDoNotDuplicateTheTop() = runTest {
        val owner = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), TestMusicDetailQueries(), this, mutableStateOf(false), {})
        val first = tree.nodesByKey.getValue(LibraryFolderKey("one", "Parent/Child"))
        val second = tree.nodesByKey.getValue(LibraryFolderKey("two", "Parent/Child"))
        repeat(3) { owner.openFolder(first, LibrarySourceFilter.ALL) }
        assertEquals(2, owner.backStack.size)
        owner.openFolder(second, LibrarySourceFilter.ALL)
        assertEquals(3, owner.backStack.size)
        assertNotEquals(owner.backStack[1], owner.top)
        owner.pop()
        assertEquals("one", owner.top.sourceId)
        owner.clear()
    }

    @Test fun eachFolderRetainsItsListUnderChildrenAndStaleBackCallbacksCannotPopANewerPage() = runTest {
        val owner = MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()), TestMusicDetailQueries(), this, mutableStateOf(false), {})
        owner.openFolder(tree.nodesByKey.getValue(LibraryFolderKey("one", "Parent")), LibrarySourceFilter.ALL)
        val parent = owner.top
        val generation = owner.generation
        val parentState = owner.folderEntry(parent).also { it.list = LazyListState(14, 7) }
        owner.openFolder(tree.nodesByKey.getValue(LibraryFolderKey("one", "Parent/Child")), LibrarySourceFilter.ALL)
        val child = owner.top
        val childState = owner.folderEntry(child).also { it.list = LazyListState(23, 11) }
        owner.requestBack(parent.entryId, generation)
        assertEquals(child, owner.top)
        for (open in listOf<() -> Unit>(
            { owner.open(LibraryNavigationTarget.Album("album")) },
            { owner.open(LibraryNavigationTarget.Artist("artist")) })) {
            open()
            owner.pop()
            assertSame(childState, owner.folderEntry(child))
            assertEquals(23, owner.folderEntry(child).list.firstVisibleItemIndex)
            assertEquals(11, owner.folderEntry(child).list.firstVisibleItemScrollOffset)
        }
        owner.pop()
        assertSame(parentState, owner.folderEntry(parent))
        assertEquals(14, parentState.list.firstVisibleItemIndex)
        assertEquals(7, parentState.list.firstVisibleItemScrollOffset)
        owner.clear()
        assertNotSame(parentState, owner.folderEntry(parent))
    }
}
