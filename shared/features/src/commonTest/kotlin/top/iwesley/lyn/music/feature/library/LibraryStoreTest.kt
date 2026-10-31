package top.iwesley.lyn.music.feature.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeLibraryProbe
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.LibraryRepository
import top.iwesley.lyn.music.data.repository.TrackPlaybackStat
import top.iwesley.lyn.music.data.repository.TrackPlaybackStatsRepository
import top.iwesley.lyn.music.feature.TestOfflineDownloadRepository
import top.iwesley.lyn.music.feature.testOfflineDownload

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryStoreTest {

    @Test
    fun `automatic fallback cannot overwrite a source selection already being saved`() = runTest {
        val first = sampleTracks().first().copy(sourceId = "dav-1")
        val second = first.copy(id = "second-track", sourceId = "dav-2")
        val tracks = listOf(first, second)
        val sources = FakeImportSourceRepository(listOf(
            sampleSource("dav-1", ImportSourceType.WEBDAV, "A"),
            sampleSource("dav-2", ImportSourceType.WEBDAV, "B"),
        ))
        val preferences = FakeLibrarySourceFilterPreferencesStore(librarySourceFilter = LibrarySourceFilter.Source("dav-1"))
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val store = LibraryStore(FakeLibraryRepository(tracks), sources, preferences, scope)
            advanceUntilIdle()
            preferences.beforeSourceFilterWrite = {
                writeStarted.complete(Unit)
                releaseWrite.await()
            }
            store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.Source("dav-2")))
            runCurrent()
            assertEquals(true, writeStarted.isCompleted)
            preferences.beforeSourceFilterWrite = null
            sources.updateSources(listOf(sampleSource("dav-2", ImportSourceType.WEBDAV, "B")))
            runCurrent()
            assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)

            releaseWrite.complete(Unit)
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.Source("dav-2"), preferences.librarySourceFilter.value)
            assertEquals(LibrarySourceFilter.Source("dav-2"), store.state.value.selectedSourceFilter)
            assertEquals(listOf(second), store.state.value.filteredTracks)
        } finally { releaseWrite.complete(Unit); scope.cancel() }
    }

    @Test
    fun `local group normalizes saved selections and follows folder additions removals and disabling`() = runTest {
        val tracks = sampleTracks()
        val sources = FakeImportSourceRepository(sampleSources())
        val repository = FakeLibraryRepository(tracks)
        val preferences = FakeLibrarySourceFilterPreferencesStore(librarySourceFilter = LibrarySourceFilter.Source("local-1"))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val store = LibraryStore(repository, sources, preferences, scope)
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.LOCAL_FOLDER, store.state.value.selectedSourceFilter)
            assertEquals(LibrarySourceFilter.LOCAL_FOLDER, preferences.librarySourceFilter.value)
            assertEquals(setOf("track-local-1", "track-local-2"), store.state.value.filteredTracks.map { it.id }.toSet())
            assertEquals(2, store.state.value.filteredAlbums.single().trackCount)

            val added = tracks.first().copy(id = "track-local-3", sourceId = "local-3", title = "New Local")
            val remaining = sampleSources().filter { it.source.id != "local-1" }
            val addedSource = sampleSource("local-3", ImportSourceType.LOCAL_FOLDER, "Another folder")
            sources.updateSources(remaining + addedSource)
            repository.updateTracks(listOf(tracks[1], added, tracks.last()))
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.LOCAL_FOLDER, store.state.value.selectedSourceFilter)
            assertEquals(setOf("track-local-2", "track-local-3"), store.state.value.filteredTracks.map { it.id }.toSet())

            sources.updateSources(listOf(addedSource, sampleSources().last()))
            repository.updateTracks(listOf(added, tracks.last()))
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.LOCAL_FOLDER, store.state.value.selectedSourceFilter)
            assertEquals(listOf(added), store.state.value.filteredTracks)

            sources.updateSources(listOf(addedSource.copy(source = addedSource.source.copy(enabled = false)), sampleSources().last()))
            repository.updateTracks(listOf(tracks.last()))
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)
            assertEquals(LibrarySourceFilter.ALL, preferences.librarySourceFilter.value)
        } finally { scope.cancel() }
    }

    @Test
    fun `fallback write failures keep state ready and allow later updates and persistence recovery`() = runTest {
        for (missingOnStartup in listOf(true, false)) {
            val tracks = sampleTracks().take(1).map { it.copy(sourceId = "dav-1") }
            val repository = FakeLibraryRepository(tracks)
            val selectedSource = sampleSource("dav-1", ImportSourceType.WEBDAV, "Remote")
            val sources = FakeImportSourceRepository(if (missingOnStartup) emptyList() else listOf(selectedSource))
            val releaseWrite = CompletableDeferred<Unit>()
            val preferences = FakeLibrarySourceFilterPreferencesStore(
                librarySourceFilter = LibrarySourceFilter.Source("dav-1"),
            ).apply {
                sourceFilterWriteFailure = IllegalStateException("Settings are not writable")
                beforeSourceFilterWrite = { releaseWrite.await() }
            }
            val failures = mutableListOf<Throwable>()
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob() +
                CoroutineExceptionHandler { _, error -> failures += error })
            try {
                val store = LibraryStore(repository, sources, preferences, scope)
                advanceUntilIdle()
                if (!missingOnStartup) {
                    sources.updateSources(emptyList())
                    advanceUntilIdle()
                }
                // The preference write is still suspended; the fallback must already be visible.
                assertEquals(false, store.state.value.isLoadingContent)
                assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)
                releaseWrite.complete(Unit)
                advanceUntilIdle()
                assertEquals(emptyList<Throwable>(), failures)

                val updated = tracks.first().copy(id = "other-track", sourceId = "other", title = "Updated")
                sources.updateSources(listOf(sampleSource("other", ImportSourceType.WEBDAV, "Other")))
                repository.updateTracks(listOf(updated))
                advanceUntilIdle()
                assertEquals(listOf(updated), store.state.value.filteredTracks)
                assertEquals(emptyList<Throwable>(), failures)

                preferences.sourceFilterWriteFailure = null
                val recovered = updated.copy(title = "Recovered")
                repository.updateTracks(listOf(recovered))
                advanceUntilIdle()
                assertEquals(listOf(recovered), store.state.value.filteredTracks)
                assertEquals(LibrarySourceFilter.ALL, preferences.librarySourceFilter.value)
            } finally { scope.cancel() }
        }
    }

    @Test
    fun `fallback write cancellation still stops its collection`() = runTest {
        val tracks = sampleTracks().take(1).map { it.copy(sourceId = "dav-1") }
        val repository = FakeLibraryRepository(tracks)
        val sources = FakeImportSourceRepository()
        val preferences = FakeLibrarySourceFilterPreferencesStore(
            librarySourceFilter = LibrarySourceFilter.Source("dav-1"),
        ).apply { sourceFilterWriteFailure = CancellationException("Store cancelled") }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val store = LibraryStore(repository, sources, preferences, scope)
            advanceUntilIdle()
            val beforeUpdate = store.state.value.tracks
            preferences.sourceFilterWriteFailure = null
            repository.updateTracks(tracks.map { it.copy(title = "After cancellation") })
            store.ensureStarted()
            advanceUntilIdle()
            assertEquals(beforeUpdate, store.state.value.tracks)
            assertEquals(LibrarySourceFilter.Source("dav-1"), preferences.librarySourceFilter.value)
        } finally { scope.cancel() }
    }

    @Test
    fun `same protocol sources remain separate and renaming preserves selection`() = runTest {
        val first = sampleSource("dav-1", ImportSourceType.WEBDAV, "NAS")
        val second = sampleSource("dav-2", ImportSourceType.WEBDAV, "NAS")
        val firstTrack = sampleTracks().last()
        val secondTrack = firstTrack.copy(id = "track-webdav-2", sourceId = "dav-2", albumTitle = "Other Album", artistName = "Other Artist")
        val sources = FakeImportSourceRepository(listOf(first, second))
        val preferences = FakeLibrarySourceFilterPreferencesStore(librarySourceFilter = LibrarySourceFilter.Source("dav-2"))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(FakeLibraryRepository(listOf(firstTrack, secondTrack)), sources, preferences, scope)

        advanceUntilIdle()
        assertEquals(listOf(secondTrack.id), store.state.value.filteredTracks.map { it.id })
        assertEquals(listOf("Other Album"), store.state.value.filteredAlbums.map { it.title })
        assertEquals(listOf("Other Artist"), store.state.value.filteredArtists.map { it.name })
        assertEquals(1, store.state.value.visibleAlbumCount)
        assertEquals(1, store.state.value.visibleArtistCount)
        sources.updateSources(listOf(first, second.copy(source = second.source.copy(label = "公司"))))
        advanceUntilIdle()
        assertEquals(LibrarySourceFilter.Source("dav-2"), store.state.value.selectedSourceFilter)
        assertEquals(LibrarySourceFilter.Source("dav-2"), preferences.librarySourceFilter.value)
        assertEquals(listOf(secondTrack.id), store.state.value.filteredTracks.map { it.id })
        assertEquals("公司", store.state.value.sourceLabelsById["dav-2"])
        scope.cancel()
    }

    @Test
    fun `removed disabled and online sources reset persisted selection even if same protocol remains`() = runTest {
        val selected = sampleSource("dav-1", ImportSourceType.WEBDAV, "家里")
        val other = sampleSource("dav-2", ImportSourceType.WEBDAV, "公司")
        val changes = listOf(
            listOf(other),
            listOf(selected.copy(source = selected.source.copy(enabled = false)), other),
            listOf(selected.copy(source = selected.source.copy(indexMode = ImportSourceIndexMode.ONLINE)), other),
        )
        changes.forEach { changedSources ->
            val sources = FakeImportSourceRepository(listOf(selected, other))
            val preferences = FakeLibrarySourceFilterPreferencesStore(librarySourceFilter = LibrarySourceFilter.Source("dav-1"))
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val store = LibraryStore(FakeLibraryRepository(sampleTracks()), sources, preferences, scope)
            advanceUntilIdle()
            sources.updateSources(changedSources)
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)
            assertEquals(LibrarySourceFilter.ALL, preferences.librarySourceFilter.value)
            sources.updateSources(listOf(selected, other))
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)
            scope.cancel()
        }
    }

    @Test
    fun `saved source selection is not cleared before first source emission`() = runTest {
        val sourceFlow = MutableSharedFlow<List<SourceWithStatus>>(replay = 1)
        val sources = object : ImportSourceRepository by FakeImportSourceRepository() {
            override fun observeSources(): Flow<List<SourceWithStatus>> = sourceFlow
        }
        val selected = LibrarySourceFilter.Source("dav-1")
        val preferences = FakeLibrarySourceFilterPreferencesStore(librarySourceFilter = selected)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(FakeLibraryRepository(sampleTracks()), sources, preferences, scope)

        advanceUntilIdle()
        assertEquals(true, store.state.value.isLoadingContent)
        assertEquals(selected, preferences.librarySourceFilter.value)
        sourceFlow.emit(sampleSources())
        advanceUntilIdle()
        assertEquals(selected, store.state.value.selectedSourceFilter)
        assertEquals(selected, preferences.librarySourceFilter.value)
        scope.cancel()
    }

    @Test
    fun `default state exposes all filter and leaves tracks unfiltered`() = runTest {
        val tracks = sampleTracks()
        val libraryRepository = FakeLibraryRepository(tracks)
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()

        val state = store.state.value
        assertEquals(listOf(LibrarySourceFilter.ALL, LibrarySourceFilter.DOWNLOADED), state.availableSourceFilters)
        assertEquals(LibrarySourceFilter.ALL, state.selectedSourceFilter)
        assertEquals(listOf("track-local-2", "track-webdav-1", "track-local-1"), state.filteredTracks.map { it.id })
        assertEquals(listOf("Album One", "Album Two"), state.filteredAlbums.map { it.title })
        assertEquals(listOf(2, 1), state.filteredAlbums.map { it.trackCount })
        assertEquals(listOf("Artist A", "Artist B"), state.filteredArtists.map { it.name })
        assertEquals(listOf(2, 1), state.filteredArtists.map { it.trackCount })
        assertEquals(2, state.visibleAlbumCount)
        assertEquals(2, state.visibleArtistCount)
        scope.cancel()
    }

    @Test
    fun `available filters reflect connected sources`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(
            listOf(
                sampleSource("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录"),
                sampleSource("dav-1", ImportSourceType.WEBDAV, "WebDAV 曲库"),
            ),
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()

        assertEquals(
            listOf(
                LibrarySourceFilter.ALL,
                LibrarySourceFilter.LOCAL_FOLDER,
                LibrarySourceFilter.Source("dav-1"),
                LibrarySourceFilter.DOWNLOADED,
            ),
            store.state.value.availableSourceFilters,
        )
        assertEquals(
            mapOf(
                "local-1" to "下载目录",
                "dav-1" to "WebDAV 曲库",
            ),
            store.state.value.sourceLabelsById,
        )
        scope.cancel()
    }

    @Test
    fun `local folder filter combines tracks from every local source`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.LOCAL_FOLDER))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, state.selectedSourceFilter)
        assertEquals(listOf("track-local-2", "track-local-1"), state.filteredTracks.map { it.id })
        assertEquals(listOf("Album One"), state.filteredAlbums.map { it.title })
        assertEquals(listOf("Artist A"), state.filteredArtists.map { it.name })
        scope.cancel()
    }

    @Test
    fun `downloaded source filter keeps local and completed offline tracks`() = runTest {
        val tracks = sampleTracks() + listOf(
            sampleTracks().last().copy(id = "track-webdav-pending", title = "Pending Cloud"),
            sampleTracks().last().copy(id = "track-webdav-failed", title = "Failed Cloud"),
            sampleTracks().last().copy(id = "track-webdav-missing-local", title = "Missing Local Cloud"),
        )
        val libraryRepository = FakeLibraryRepository(tracks)
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val offlineDownloadRepository = TestOfflineDownloadRepository(
            mapOf(
                "track-webdav-1" to testOfflineDownload("track-webdav-1", "dav-1"),
                "track-webdav-pending" to testOfflineDownload(
                    trackId = "track-webdav-pending",
                    sourceId = "dav-1",
                    status = OfflineDownloadStatus.Pending,
                ),
                "track-webdav-failed" to testOfflineDownload(
                    trackId = "track-webdav-failed",
                    sourceId = "dav-1",
                    status = OfflineDownloadStatus.Failed,
                ),
                "track-webdav-missing-local" to testOfflineDownload(
                    trackId = "track-webdav-missing-local",
                    sourceId = "dav-1",
                    localMediaLocator = null,
                ),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(
            repository = libraryRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
            offlineDownloadRepository = offlineDownloadRepository,
        )

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.DOWNLOADED))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(LibrarySourceFilter.DOWNLOADED, state.selectedSourceFilter)
        assertEquals(listOf("track-local-2", "track-webdav-1", "track-local-1"), state.filteredTracks.map { it.id })
        assertEquals(listOf("Album One", "Album Two"), state.filteredAlbums.map { it.title })
        assertEquals(listOf("Artist A", "Artist B"), state.filteredArtists.map { it.name })
        scope.cancel()
    }

    @Test
    fun `search query intersects with selected source filter`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.Source("dav-1")))
        store.dispatch(LibraryIntent.SearchChanged("cloud"))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(listOf("track-webdav-1"), state.filteredTracks.map { it.id })
        assertEquals(listOf("Album Two"), state.filteredAlbums.map { it.title })
        assertEquals(listOf("Artist B"), state.filteredArtists.map { it.name })
        assertEquals(1, state.visibleAlbumCount)
        assertEquals(1, state.visibleArtistCount)
        scope.cancel()
    }

    @Test
    fun `search query matches relative path folders`() = runTest {
        val pathOnlyTrack = Track(
            id = "track-path-only",
            sourceId = "local-1",
            title = "Hidden Song",
            durationMs = 120_000L,
            mediaLocator = "file:///music/hidden-song.mp3",
            relativePath = "Mixtapes/Special Folder/hidden-song.mp3",
        )
        val libraryRepository = FakeLibraryRepository(sampleTracks() + pathOnlyTrack)
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SearchChanged("special folder"))
        advanceUntilIdle()

        assertEquals(listOf("track-path-only"), store.state.value.filteredTracks.map { it.id })
        scope.cancel()
    }

    @Test
    fun `visible counts follow current filtered tracks`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.LOCAL_FOLDER))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(2, state.filteredTracks.size)
        assertEquals(1, state.visibleAlbumCount)
        assertEquals(1, state.visibleArtistCount)
        scope.cancel()
    }

    @Test
    fun `selected filter falls back to all when selected source disappears`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.LOCAL_FOLDER))
        advanceUntilIdle()

        importSourceRepository.updateSources(
            listOf(sampleSource("dav-1", ImportSourceType.WEBDAV, "WebDAV 曲库")),
        )
        libraryRepository.updateTracks(
            listOf(
                Track(
                    id = "track-webdav-1",
                    sourceId = "dav-1",
                    title = "Cloud Song",
                    artistName = "Artist B",
                    albumTitle = "Album Two",
                    durationMs = 185_000L,
                    mediaLocator = "https://dav.example.com/music/cloud-song.mp3",
                    relativePath = "Artist B/Cloud Song.mp3",
                ),
            ),
        )
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(LibrarySourceFilter.ALL, state.selectedSourceFilter)
        assertEquals(
            listOf(LibrarySourceFilter.ALL, LibrarySourceFilter.Source("dav-1"), LibrarySourceFilter.DOWNLOADED),
            state.availableSourceFilters,
        )
        assertEquals(listOf("track-webdav-1"), state.filteredTracks.map { it.id })
        scope.cancel()
    }

    @Test
    fun `selected source filter loads from persisted preferences and updates them on change`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(
            librarySourceFilter = LibrarySourceFilter.Source("dav-1"),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        assertEquals(LibrarySourceFilter.Source("dav-1"), store.state.value.selectedSourceFilter)
        assertEquals(listOf("track-webdav-1"), store.state.value.filteredTracks.map { it.id })

        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.LOCAL_FOLDER))
        advanceUntilIdle()

        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, preferencesStore.librarySourceFilter.value)
        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, store.state.value.selectedSourceFilter)
        scope.cancel()
    }

    @Test
    fun `track sort mode loads from preferences and updates filtered tracks`() = runTest {
        val libraryRepository = FakeLibraryRepository(
            sampleTracks().mapIndexed { index, track -> track.copy(addedAt = (index + 1) * 100L) },
        )
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(
            libraryTrackSortMode = TrackSortMode.PLAY_COUNT,
        )
        val trackStatsRepository = FakeTrackPlaybackStatsRepository(
            mapOf(
                "track-local-1" to TrackPlaybackStat("track-local-1", playCount = 2, lastPlayedAt = 20L),
                "track-local-2" to TrackPlaybackStat("track-local-2", playCount = 7, lastPlayedAt = 70L),
                "track-webdav-1" to TrackPlaybackStat("track-webdav-1", playCount = 4, lastPlayedAt = 40L),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(
            repository = libraryRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
            trackPlaybackStatsRepository = trackStatsRepository,
        )

        advanceUntilIdle()
        assertEquals(TrackSortMode.PLAY_COUNT, store.state.value.selectedTrackSortMode)
        assertEquals(listOf("track-local-2", "track-webdav-1", "track-local-1"), store.state.value.filteredTracks.map { it.id })

        store.dispatch(LibraryIntent.TrackSortChanged(TrackSortMode.ADDED_AT))
        advanceUntilIdle()

        assertEquals(TrackSortMode.ADDED_AT, preferencesStore.libraryTrackSortMode.value)
        assertEquals(listOf("track-webdav-1", "track-local-2", "track-local-1"), store.state.value.filteredTracks.map { it.id })
        scope.cancel()
    }
}

private class FakeLibraryRepository(
    tracks: List<Track>,
) : LibraryRepository {
    private val mutableTracks = MutableStateFlow(tracks)
    private val mutableArtists = MutableStateFlow(artistsFrom(tracks))
    private val mutableAlbums = MutableStateFlow(albumsFrom(tracks))

    override val tracks: Flow<List<Track>> = mutableTracks.asStateFlow()
    override val artists: Flow<List<Artist>> = mutableArtists.asStateFlow()
    override val albums: Flow<List<Album>> = mutableAlbums.asStateFlow()

    fun updateTracks(tracks: List<Track>) {
        mutableTracks.value = tracks
        mutableArtists.value = artistsFrom(tracks)
        mutableAlbums.value = albumsFrom(tracks)
    }

    override suspend fun getTracksByIds(trackIds: List<String>): List<Track> {
        val byId = mutableTracks.value.associateBy { it.id }
        return trackIds.mapNotNull { byId[it] }
    }
}

private class FakeImportSourceRepository(
    sources: List<SourceWithStatus> = emptyList(),
) : ImportSourceRepository {
    private val mutableSources = MutableStateFlow(sources)

    fun updateSources(sources: List<SourceWithStatus>) {
        mutableSources.value = sources
    }

    override fun observeSources(): Flow<List<SourceWithStatus>> = mutableSources.asStateFlow()

    override suspend fun importLocalFolder(): Result<ImportScanSummary?> = Result.success(testScanSummary())

    override suspend fun testSambaSource(draft: SambaSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun testUpdatedSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addSambaSource(draft: SambaSourceDraft): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun testWebDavSource(draft: WebDavSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun testUpdatedWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun testNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun probeNavidromeSource(draft: NavidromeSourceDraft): Result<NavidromeLibraryProbe> {
        return Result.success(NavidromeLibraryProbe(totalTrackCount = null))
    }

    override suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun addNavidromeSourceOnline(
        draft: NavidromeSourceDraft,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun probeExistingNavidromeSource(sourceId: String): Result<NavidromeLibraryProbe> {
        return Result.success(NavidromeLibraryProbe(totalTrackCount = null))
    }

    override suspend fun switchNavidromeSourceToOnline(
        sourceId: String,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun rescanSource(sourceId: String): Result<ImportScanSummary?> = Result.success(testScanSummary(sourceId))

    override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun deleteSource(sourceId: String): Result<Unit> = Result.success(Unit)
}

private fun testScanSummary(sourceId: String = "source-1"): ImportScanSummary {
    return ImportScanSummary(
        sourceId = sourceId,
        discoveredAudioFileCount = 1,
        importedTrackCount = 1,
    )
}

private class FakeLibrarySourceFilterPreferencesStore(
    librarySourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    favoritesSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    libraryTrackSortMode: TrackSortMode = TrackSortMode.TITLE,
    favoritesTrackSortMode: TrackSortMode = TrackSortMode.ADDED_AT,
) : LibrarySourceFilterPreferencesStore {
    private val sourceFilterMutex = Mutex()
    var sourceFilterWriteFailure: Exception? = null
    var beforeSourceFilterWrite: (suspend () -> Unit)? = null

    override val librarySourceFilter = MutableStateFlow(librarySourceFilter)
    override val favoritesSourceFilter = MutableStateFlow(favoritesSourceFilter)
    override val libraryTrackSortMode = MutableStateFlow(libraryTrackSortMode)
    override val favoritesTrackSortMode = MutableStateFlow(favoritesTrackSortMode)
    override val onlineLibrarySourceId = MutableStateFlow<String?>(null)
    override val onlineFavoritesSourceId = MutableStateFlow<String?>(null)
    override val onlinePlaylistsSourceId = MutableStateFlow<String?>(null)

    override suspend fun setLibrarySourceFilter(filter: LibrarySourceFilter) {
        updateSourceFilter(librarySourceFilter, filter)
    }

    override suspend fun setFavoritesSourceFilter(filter: LibrarySourceFilter) {
        updateSourceFilter(favoritesSourceFilter, filter)
    }

    override suspend fun compareAndSetLibrarySourceFilter(expected: LibrarySourceFilter, filter: LibrarySourceFilter): Boolean =
        updateSourceFilter(librarySourceFilter, filter, expected)

    override suspend fun compareAndSetFavoritesSourceFilter(expected: LibrarySourceFilter, filter: LibrarySourceFilter): Boolean =
        updateSourceFilter(favoritesSourceFilter, filter, expected)

    private suspend fun updateSourceFilter(
        current: MutableStateFlow<LibrarySourceFilter>,
        filter: LibrarySourceFilter,
        expected: LibrarySourceFilter? = null,
    ): Boolean = sourceFilterMutex.withLock {
        if (expected != null && current.value != expected) return@withLock false
        beforeSourceFilterWrite?.invoke()
        sourceFilterWriteFailure?.let { throw it }
        current.value = filter
        true
    }

    override suspend fun setLibraryTrackSortMode(mode: TrackSortMode) {
        libraryTrackSortMode.value = mode
    }

    override suspend fun setFavoritesTrackSortMode(mode: TrackSortMode) {
        favoritesTrackSortMode.value = mode
    }

    override suspend fun setOnlineLibrarySourceId(sourceId: String?) {
        onlineLibrarySourceId.value = sourceId
    }

    override suspend fun setOnlineFavoritesSourceId(sourceId: String?) {
        onlineFavoritesSourceId.value = sourceId
    }

    override suspend fun setOnlinePlaylistsSourceId(sourceId: String?) {
        onlinePlaylistsSourceId.value = sourceId
    }
}

private class FakeTrackPlaybackStatsRepository(
    initialStats: Map<String, TrackPlaybackStat> = emptyMap(),
) : TrackPlaybackStatsRepository {
    override val trackStats = MutableStateFlow(initialStats)
}

private fun sampleTracks(): List<Track> {
    return listOf(
        Track(
            id = "track-local-1",
            sourceId = "local-1",
            title = "Local Song",
            artistName = "Artist A",
            albumTitle = "Album One",
            durationMs = 210_000L,
            mediaLocator = "file:///music/local-song.mp3",
            relativePath = "Artist A/Local Song.mp3",
        ),
        Track(
            id = "track-local-2",
            sourceId = "local-2",
            title = "Another Local",
            artistName = "Artist A",
            albumTitle = "Album One",
            durationMs = 192_000L,
            mediaLocator = "file:///music/another-local.mp3",
            relativePath = "Artist A/Another Local.mp3",
        ),
        Track(
            id = "track-webdav-1",
            sourceId = "dav-1",
            title = "Cloud Song",
            artistName = "Artist B",
            albumTitle = "Album Two",
            durationMs = 185_000L,
            mediaLocator = "https://dav.example.com/music/cloud-song.mp3",
            relativePath = "Artist B/Cloud Song.mp3",
        ),
    )
}

private fun sampleSources(): List<SourceWithStatus> {
    return listOf(
        sampleSource("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录"),
        sampleSource("local-2", ImportSourceType.LOCAL_FOLDER, "NAS 缓存"),
        sampleSource("dav-1", ImportSourceType.WEBDAV, "WebDAV 曲库"),
    )
}

private fun sampleSource(id: String, type: ImportSourceType, label: String): SourceWithStatus {
    return SourceWithStatus(
        source = ImportSource(
            id = id,
            type = type,
            label = label,
            rootReference = label,
            createdAt = 0L,
        ),
    )
}

private fun artistsFrom(tracks: List<Track>): List<Artist> {
    return deriveVisibleArtists(tracks)
}

private fun albumsFrom(tracks: List<Track>): List<Album> {
    return deriveVisibleAlbums(tracks)
}
