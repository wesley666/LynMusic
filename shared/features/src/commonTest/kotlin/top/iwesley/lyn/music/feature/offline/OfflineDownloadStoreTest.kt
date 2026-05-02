package top.iwesley.lyn.music.feature.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.feature.TestOfflineDownloadRepository

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDownloadStoreTest {

    @Test
    fun `refresh available space writes bytes into state`() = runTest {
        val repository = TestOfflineDownloadRepository(nextAvailableSpaceBytes = 5_368_709_120L)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.RefreshAvailableSpace)
        advanceUntilIdle()

        assertEquals(5_368_709_120L, store.state.value.availableSpaceBytes)
        assertFalse(store.state.value.availableSpaceLoading)
        assertEquals(1, repository.availableSpaceCalls)
        scope.cancel()
    }

    @Test
    fun `refresh available space can return unknown`() = runTest {
        val repository = TestOfflineDownloadRepository(nextAvailableSpaceBytes = null)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.RefreshAvailableSpace)
        advanceUntilIdle()

        assertNull(store.state.value.availableSpaceBytes)
        assertFalse(store.state.value.availableSpaceLoading)
        assertEquals(1, repository.availableSpaceCalls)
        scope.cancel()
    }
}
