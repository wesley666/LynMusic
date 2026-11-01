@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package top.iwesley.lyn.music.desktop.ui

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlin.test.*
import top.iwesley.lyn.music.feature.details.*
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter

class MusicDetailNavigationTest {
    @Test fun stackDeduplicatesOnlyTheSameTopAndSource()=runTest {
        val owner=owner()
        owner.open(LibraryNavigationTarget.OnlineAlbum("one","album"))
        val first=owner.top
        owner.open(LibraryNavigationTarget.OnlineAlbum("one","album",albumTitle="New seed"))
        assertEquals(2,owner.backStack.size)
        owner.open(LibraryNavigationTarget.OnlineAlbum("two","album"))
        assertEquals(3,owner.backStack.size)
        owner.open(LibraryNavigationTarget.OnlineAlbum("one","album"))
        assertNotEquals(first.entryId,owner.top.entryId)
        owner.pop(); assertEquals("two",owner.top.sourceId)
        owner.pop();assertEquals(first,owner.top)
        owner.pop();assertFalse(owner.hasDetails)
    }
    @Test fun localFilterIsPartOfDestinationIdentity()=runTest {
        val owner=owner()
        owner.open(LibraryNavigationTarget.Artist("artist",LibrarySourceFilter.Source("one")))
        owner.open(LibraryNavigationTarget.Artist("artist",LibrarySourceFilter.Source("two")))
        assertEquals(3,owner.backStack.size)
        assertEquals(LibrarySourceFilter.Source("two"),owner.top.request().sourceFilter)
    }
    @Test fun routesSerializeOnlyIdentifiersContextAndSeeds() {
        val route=LibraryNavigationTarget.OnlineAlbum("source","album","Title","Various artists","cover").detailRoute().copy(entryId="entry")
        assertEquals(route,Json.decodeFromString<MusicDetailRoute>(Json.encodeToString(route)))
        val local=LibraryNavigationTarget.Album("album",LibrarySourceFilter.DOWNLOADED).detailRoute()
        assertEquals(LibrarySourceFilter.DOWNLOADED,Json.decodeFromString<MusicDetailRoute>(Json.encodeToString(local)).request().sourceFilter)
    }
    @Test fun clearInvalidatesOldCallbacksAndDoesNotRestorePlayer()=runTest {
        var restores=0
        val owner=owner(restore={restores++})
        owner.open(LibraryNavigationTarget.Album("a"),fromPlayer=true)
        val old=owner.top;val generation=owner.generation
        owner.clear();assertTrue(owner.immediate)
        owner.open(LibraryNavigationTarget.Album("b"))
        owner.pop(old.entryId,generation)
        assertEquals("b",owner.top.entityId);assertFalse(owner.immediate)
        owner.pop();assertEquals(0,restores)
    }
    @Test fun finalNormalPopRestoresPlayerOnce()=runTest {
        var restores=0;val owner=owner(restore={restores++})
        owner.open(LibraryNavigationTarget.Album("a"),true)
        owner.open(LibraryNavigationTarget.Artist("b"))
        owner.pop();assertEquals(0,restores)
        owner.pop();owner.pop();assertEquals(1,restores)
    }
    @Test fun coveredEntryRetainsLoadedDataUntilReleased()=runTest {
        val owner=owner()
        owner.open(LibraryNavigationTarget.Album("a"));val a=owner.top
        val state=owner.entry(a);runCurrent();assertEquals("a",state.data?.title)
        owner.open(LibraryNavigationTarget.Artist("b"));owner.pop()
        assertSame(state,owner.entry(a))
        owner.pop();owner.release(a.entryId)
    }
    @Test fun poppedEntryIgnoresNonCancellableLateResponse()=runTest {
        val gate=CompletableDeferred<Unit>()
        val queries=MusicDetailQueries { flow { withContext(NonCancellable) { gate.await() }; emit(MusicDetailData("late")) } }
        val owner=owner(queries)
        owner.open(LibraryNavigationTarget.Album("a"));val state=owner.entry(owner.top);runCurrent()
        owner.pop();gate.complete(Unit);runCurrent()
        assertNull(state.data)
    }
    @Test fun retryOnlyUpdatesItsOwnEntryAndAcceptsSuccessfulEmptyData()=runTest {
        var calls=0
        val owner=owner(MusicDetailQueries { request -> flow {
            if(request.entityId=="a" && ++calls==1) error("failure")
            emit(MusicDetailData(request.entityId))
        } })
        owner.open(LibraryNavigationTarget.Album("a"));val a=owner.entry(owner.top);runCurrent()
        assertNotNull(a.error)
        owner.open(LibraryNavigationTarget.Album("b"));val b=owner.entry(owner.top);runCurrent()
        a.retry();runCurrent()
        assertEquals("a",a.data?.title);assertNull(a.error);assertEquals("b",b.data?.title)
        assertTrue(a.data!!.tracks.isEmpty())
        owner.clear()
    }
    @Test fun anOutgoingSceneCannotStartANewQueryAfterItsRouteWasRemoved() = runTest {
        var requests = 0
        val owner = owner(MusicDetailQueries { flow { requests++; emit(MusicDetailData("late")) } })
        owner.open(LibraryNavigationTarget.Album("a"))
        val route = owner.top
        owner.pop()
        val state = owner.entry(route)
        runCurrent()
        assertEquals(0, requests)
        assertNull(state.data)
        owner.release(route.entryId)
    }
    @Test fun unavailableSourceIsExposedWithoutChangingTarget()=runTest {
        val owner=owner(MusicDetailQueries { flow { throw MusicDetailUnavailableException() } })
        owner.open(LibraryNavigationTarget.OnlineArtist("missing","artist"))
        val state=owner.entry(owner.top);runCurrent()
        assertIs<MusicDetailUnavailableException>(state.error)
        assertEquals("missing",owner.top.sourceId)
        owner.clear()
    }
    private fun CoroutineScope.owner(
        queries:MusicDetailQueries=MusicDetailQueries { request -> flowOf(MusicDetailData(request.entityId)) },
        restore:()->Unit={},
    )=MusicDetailNavigation(NavBackStack<NavKey>(MusicDetailRoute()),queries,this,mutableStateOf(false),restore)
}
