package top.iwesley.lyn.music.feature.player

import androidx.room.Room
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.data.repository.*

/** Exercise the real cache: artwork-only writes do not publish a lyrics-version change. */
class PlayerStoreCommittedArtworkTest {
    @Test fun closedSearchStillPublishesCommittedArtwork() = verifyArtworkCompletion(AfterSubmission.Close)
    @Test fun newSearchKeepsItsStateWhileCommittedArtworkUpdatesPlayback() = verifyArtworkCompletion(AfterSubmission.NewSearch)
    @Test fun newTrackNeverReceivesTheOriginalTracksArtwork() = verifyArtworkCompletion(AfterSubmission.NextTrack)
    @Test fun unchangedSearchStillPublishesArtworkAndClosesNormally() = verifyArtworkCompletion(AfterSubmission.None)

    private fun verifyArtworkCompletion(afterSubmission: AfterSubmission) = runBlocking {
        val directory = Files.createTempDirectory("lynmusic-committed-artwork")
        val database = buildLynMusicDatabase(
            Room.databaseBuilder<LynMusicDatabase>(name = directory.resolve("lyrics.db").toString()),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val document = LyricsDocument(
                listOf(LyricsLine(0, "Line")), sourceId = "source", rawPayload = "[00:00]Line",
            )
            val track = Track(
                "original", "source", "Song", mediaLocator = "file:///song.mp3",
                relativePath = "song.mp3", artworkLocator = "https://cover/old",
            )
            val repository = DefaultLyricsRepository(database, object : LyricsHttpClient {
                override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> =
                    error("Unexpected network request")
            }, object : SecureCredentialStore {
                override suspend fun put(key: String, value: String) = Unit
                override suspend fun get(key: String): String? = null
                override suspend fun remove(key: String) = Unit
            })
            val candidate = LyricsSearchCandidate("source", "Source", document, artworkLocator = "https://cover/new")
            repository.applyLyricsCandidate(
                track.id, candidate.copy(artworkLocator = "https://cover/old"), LyricsSearchApplyMode.FULL,
            )
            val started = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            val gatedRepository = object : LyricsRepository by repository {
                override suspend fun applyLyricsCandidate(
                    trackId: String, candidate: LyricsSearchCandidate, mode: LyricsSearchApplyMode,
                ): AppliedLyricsResult {
                    started.complete(Unit)
                    gate.await()
                    return repository.applyLyricsCandidate(trackId, candidate, mode)
                }
            }
            val playback = CommitPlaybackRepository(PlaybackSnapshot(queue = listOf(track), currentIndex = 0))
            val store = PlayerStore(playback, gatedRepository, scope)
            withTimeout(5_000) { store.state.first { it.lyrics != null } }
            store.dispatch(PlayerIntent.BeginManualLyricsSearch)
            store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate, LyricsSearchApplyMode.ARTWORK_ONLY))
            withTimeout(5_000) { started.await() }
            when (afterSubmission) {
                AfterSubmission.Close -> store.dispatch(PlayerIntent.EndManualLyricsSearch)
                AfterSubmission.NewSearch -> {
                    store.dispatch(PlayerIntent.EndManualLyricsSearch)
                    store.dispatch(PlayerIntent.BeginManualLyricsSearch)
                    store.dispatch(PlayerIntent.ManualLyricsTitleChanged("New query"))
                }
                AfterSubmission.NextTrack -> {
                    playback.snapshot.value = PlaybackSnapshot(
                        queue = listOf(track.copy(id = "next", artworkLocator = "https://cover/next")), currentIndex = 0,
                    )
                    withTimeout(5_000) { store.state.first { it.snapshot.currentTrack?.id == "next" } }
                }
                AfterSubmission.None -> Unit
            }
            val searchSession = store.state.value.manualLyricsSearchSession
            gate.complete(Unit)
            withTimeout(5_000) {
                store.state.first {
                    if (afterSubmission == AfterSubmission.None) it.manualLyricsSearchSession == null
                    else it.operationNotices.isNotEmpty()
                }
            }
            assertEquals("https://cover/new", database.lyricsCacheDao().getByTrack(track.id).first().artworkLocator)
            assertEquals(
                if (afterSubmission == AfterSubmission.NextTrack) "https://cover/next" else "https://cover/new",
                playback.snapshot.value.currentDisplayArtworkLocator,
            )
            if (afterSubmission == AfterSubmission.NewSearch) {
                assertEquals(searchSession, store.state.value.manualLyricsSearchSession)
                assertEquals("New query", store.state.value.manualLyricsTitle)
                assertNull(store.state.value.message)
            } else {
                assertNull(store.state.value.manualLyricsSearchSession)
            }
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            directory.toFile().deleteRecursively()
        }
    }

    private enum class AfterSubmission { None, Close, NewSearch, NextTrack }
}
