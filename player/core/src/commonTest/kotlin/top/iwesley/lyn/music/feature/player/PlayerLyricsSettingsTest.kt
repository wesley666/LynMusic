package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import top.iwesley.lyn.music.cast.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerLyricsSettingsTest {
    @Test fun `offset is added once and uses the same direction as highlighting`() = runTest {
        val harness = Harness(backgroundScope)
        harness.show(harness.a)
        assertEquals(200L, harness.state.lyrics?.offsetMs)
        assertEquals(-1, harness.state.highlightedLineIndex)
        harness.settings.changeOffset("a", "v1", 100)
        assertEquals(300L, harness.state.lyrics?.offsetMs)
        assertEquals(0, harness.state.highlightedLineIndex)
        harness.settings.changeOffset("a", "v1", 200)
        assertEquals(400L, harness.state.lyrics?.offsetMs)
        harness.settings.changeOffset("a", "v1", 0)
        assertEquals(200L, harness.state.lyrics?.offsetMs)
        assertEquals(200L, harness.state.originalLyrics?.offsetMs)
        runCurrent()
        assertEquals(0L, harness.repository.offsets["a"])
    }

    @Test fun `reset wins over a running offset write and coalesces pending adjustments`() = runTest {
        val harness = Harness(backgroundScope)
        harness.show(harness.a)
        val gate = CompletableDeferred<Unit>()
        harness.repository.writeGate = gate
        harness.settings.changeOffset("a", "v1", 500)
        runCurrent()
        harness.settings.changeOffset("a", "v1", 800)
        harness.settings.changeOffset("a", "v1", 0)
        assertEquals(0L, harness.state.lyricsUserOffsetMs)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(500L, 0L), harness.repository.writes.map { it.second })
        assertEquals(0L, harness.repository.offsets["a"])
        assertEquals(0L, harness.state.lyricsUserOffsetMs)
    }

    @Test fun `style reset persists after an older slow write without closing settings`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val preferences = SlowPreferences(gate)
        val harness = Harness(backgroundScope, preferences)
        harness.settings.changeStyle(PlayerLyricsStyle(highlightArgb = 0xffff0000.toInt(), fontScale = 1.75f, showTranslation = false))
        assertFalse(harness.state.lyricsStyle.showTranslation)
        runCurrent()
        harness.settings.changeStyle(PlayerLyricsStyle(highlightArgb = 0xff0000ff.toInt()))
        harness.settings.changeStyle(PlayerLyricsStyle())
        assertEquals(PlayerLyricsStyle(), harness.state.lyricsStyle)
        gate.complete(Unit)
        runCurrent()
        assertEquals(PlayerLyricsStyle(), preferences.playerLyricsStyle.value)
        assertEquals(2, preferences.writes.size)
    }

    @Test fun `calibration follows the effective cast song rather than paused local song`() = runTest {
        val harness = Harness(backgroundScope)
        harness.state = harness.state.copy(castQueueIndex = 1, castState = CastSessionState(status = CastSessionStatus.Casting, playback = CastPlaybackState(positionMs = 750)))
        harness.show(harness.b)
        harness.settings.changeOffset("a", "v1", 900)
        assertEquals(0L, harness.state.lyricsUserOffsetMs)
        harness.settings.changeOffset("b", "v1", 400)
        runCurrent()
        assertEquals(mapOf("b" to 400L), harness.repository.offsets)
        harness.state = harness.state.copy(castQueueIndex = null, castState = CastSessionState())
        harness.show(harness.a)
        assertEquals(0L, harness.state.lyricsUserOffsetMs)
        assertEquals("a", harness.state.lyricsTrackId)
        assertEquals(400L, harness.repository.offsets["b"])
    }

    @Test fun `switching songs preserves pending old-song write without changing new-song display`() = runTest {
        val harness = Harness(backgroundScope)
        harness.show(harness.a)
        val gate = CompletableDeferred<Unit>()
        harness.repository.writeGate = gate
        harness.settings.changeOffset("a", "v1", 800)
        runCurrent()
        harness.state = harness.state.copy(snapshot = harness.state.snapshot.copy(currentIndex = 1))
        harness.show(harness.b)
        harness.settings.changeOffset("b", "v1", -100)
        gate.complete(Unit)
        runCurrent()
        assertEquals(mapOf("a" to 800L, "b" to -100L), harness.repository.offsets)
        assertEquals(-100L, harness.state.lyricsUserOffsetMs)
        assertEquals("b", harness.state.lyricsTrackId)
    }

    @Test fun `reloading the same version does not undo an unsaved visible adjustment`() = runTest {
        val harness = Harness(backgroundScope)
        harness.show(harness.a)
        harness.settings.changeOffset("a", "v1", 800)
        harness.show(harness.a)
        assertEquals(800L, harness.state.lyricsUserOffsetMs)
        runCurrent()
        assertEquals(800L, harness.repository.offsets["a"])
    }

    @Test fun `reload cannot restore an old read after a pending write finishes`() = runTest {
        val harness = Harness(backgroundScope)
        harness.show(harness.a)
        harness.settings.changeOffset("a", "v1", 800)
        val gate = CompletableDeferred<Unit>()
        harness.repository.readGate = gate
        val reload = async(start = CoroutineStart.UNDISPATCHED) { harness.show(harness.a) }
        runCurrent()
        assertEquals(800L, harness.repository.offsets["a"])
        gate.complete(Unit)
        reload.await()
        assertEquals(800L, harness.state.lyricsUserOffsetMs)
        assertEquals(1_000L, harness.state.lyrics?.offsetMs)
    }

    @Test fun `reset completed during reload wins over the old persisted adjustment`() = runTest {
        val harness = Harness(backgroundScope)
        harness.show(harness.a)
        harness.settings.changeOffset("a", "v1", 800)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        harness.repository.readGate = gate
        val reload = async(start = CoroutineStart.UNDISPATCHED) { harness.show(harness.a) }
        harness.settings.changeOffset("a", "v1", 0)
        runCurrent()
        assertEquals(0L, harness.repository.offsets["a"])
        gate.complete(Unit)
        reload.await()
        assertEquals(0L, harness.state.lyricsUserOffsetMs)
        assertEquals(200L, harness.state.lyrics?.offsetMs)
    }

    @Test fun `a later authoritative read can invalidate a previously saved adjustment`() = runTest {
        val harness = Harness(backgroundScope)
        harness.show(harness.a)
        harness.settings.changeOffset("a", "v1", 800)
        runCurrent()
        harness.repository.offsets.remove("a")
        harness.show(harness.a)
        assertEquals(0L, harness.state.lyricsUserOffsetMs)
    }

    @Test fun `startup restores imported font and deletion falls back without offering system fonts`() = runTest {
        val preferences = InMemoryPlayerLyricsStylePreferencesStore()
        preferences.setPlayerLyricsStyle(PlayerLyricsStyle(fontKey = "imported:abc"))
        val library = FontLibrary()
        val harness = Harness(backgroundScope, preferences, library)
        runCurrent()
        assertEquals("/font.ttf", harness.state.lyricsFontPath)
        assertEquals(listOf("imported:abc"), harness.state.availableLyricsFonts.map { it.fontKey })
        library.exists = false
        harness.settings.refreshFonts()
        runCurrent()
        assertNull(harness.state.lyricsStyle.fontKey)
        assertNull(harness.state.lyricsFontPath)
        assertNull(preferences.playerLyricsStyle.value.fontKey)
    }

    private class Harness(
        scope: CoroutineScope,
        preferences: PlayerLyricsStylePreferencesStore = InMemoryPlayerLyricsStylePreferencesStore(),
        fonts: LyricsShareFontLibraryPlatformService = UnsupportedLyricsShareFontLibraryPlatformService,
    ) {
        val a = track("a")
        val b = track("b")
        var state = PlayerState(snapshot = PlaybackSnapshot(queue = listOf(a, b), currentIndex = 0, positionMs = 750), lyricsStyle = preferences.playerLyricsStyle.value)
        val repository = Repository()
        val settings = PlayerLyricsSettings(scope, repository, preferences, fonts, { state }, { state = it(state) })
        suspend fun show(track: Track) {
            settings.accept(track, ResolvedLyricsResult(LyricsDocument(listOf(LyricsLine(1_000, "First"), LyricsLine(2_000, "Second")), offsetMs = 200, sourceId = "provider", rawPayload = "raw"), version = "v1")) {
                state.effectiveSnapshot.currentTrack?.id == track.id
            }
        }
        private fun track(id: String) = Track(id, "local", id, mediaLocator = "file:///$id.mp3", relativePath = "$id.mp3")
    }

    private class Repository : LyricsRepository {
        val offsets = mutableMapOf<String, Long>()
        val writes = mutableListOf<Pair<String, Long>>()
        var writeGate: CompletableDeferred<Unit>? = null
        var readGate: CompletableDeferred<Unit>? = null
        override suspend fun readOffset(track: Track, version: String): Long {
            val offset = offsets[track.id] ?: 0L
            readGate?.let { gate -> readGate = null; gate.await() }
            return offset
        }
        override suspend fun saveOffset(track: Track, version: String, offsetMs: Long): Boolean {
            writeGate?.let { gate -> writeGate = null; gate.await() }
            writes += track.id to offsetMs
            offsets[track.id] = offsetMs
            return true
        }
        override suspend fun getLyrics(track: Track): ResolvedLyricsResult? = null
        override suspend fun searchLyricsCandidates(track: Track, includeTrackProvidedCandidate: Boolean): List<LyricsSearchCandidate> = emptyList()
        override suspend fun applyLyricsCandidate(trackId: String, candidate: LyricsSearchCandidate, mode: LyricsSearchApplyMode): AppliedLyricsResult = error("unused")
        override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()
        override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult = error("unused")
        override suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate, mode: LyricsSearchApplyMode): AppliedLyricsResult = error("unused")
    }

    private class SlowPreferences(private val gate: CompletableDeferred<Unit>) : PlayerLyricsStylePreferencesStore {
        override val playerLyricsStyle = MutableStateFlow(PlayerLyricsStyle())
        val writes = mutableListOf<PlayerLyricsStyle>()
        override suspend fun setPlayerLyricsStyle(value: PlayerLyricsStyle) {
            if (writes.isEmpty()) gate.await()
            writes += value
            playerLyricsStyle.value = value
        }
    }

    private class FontLibrary : LyricsShareFontLibraryPlatformService {
        var exists = true
        override suspend fun listImportedFonts() = Result.success(if (exists) listOf(
            LyricsShareFontOption("Serif", "System"),
            LyricsShareFontOption("imported:abc", "Imported", kind = LyricsShareFontKind.IMPORTED, fontFilePath = "/font.ttf"),
        ) else emptyList())
        override suspend fun resolveImportedFontPath(fontKey: String) = Result.success("/font.ttf".takeIf { exists })
        override suspend fun importFont(): Result<LyricsShareFontOption?> = Result.success(null)
        override suspend fun deleteImportedFont(fontKey: String): Result<Unit> = Result.success(Unit)
    }
}
