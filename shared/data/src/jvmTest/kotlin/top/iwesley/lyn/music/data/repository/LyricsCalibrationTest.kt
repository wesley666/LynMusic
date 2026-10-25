package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.db.*
import top.iwesley.lyn.music.domain.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LyricsCalibrationTest {
    private val track = Track("track:local:song", "local", "Song", mediaLocator = "file:///song.mp3", relativePath = "song.mp3")
    private val payload = "[offset:200]\n[00:01.00]First\n[00:03.00]Second"
    private val source = "provider"

    @Test fun `language-only sidecar updates reload playback without resetting calibration or repeating notifications`() = runTest {
        withDatabase { database ->
            var livePayload = """<tt xmlns:m="urn:metadata" xml:lang="en"><body><p begin="1s">Original<span m:role="x-translation" xml:lang="zh-Hans">Translation</span></p></body></tt>"""
            val sidecar = object : SameNameLyricsFileGateway {
                override suspend fun readSameNameLyrics(track: Track) = Result.success(livePayload)
            }
            val repository = repository(database, sidecar)
            val original = assertNotNull(repository.getLyrics(track))
            assertTrue(repository.saveOffset(track, original.version, 350))
            val savedCalibration = database.lyricsOffsetDao().get(track.id)
            val changes = mutableListOf<LyricsChange>()
            val observer = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                repository.changes.collect { if (it.trackId == track.id) changes += it }
            }

            for ((oldLanguage, newLanguage) in listOf("en" to "zh-Hant", "zh-Hant" to "en")) {
                changes.clear()
                livePayload = livePayload.replace("xml:lang=\"$oldLanguage\"", "xml:lang=\"$newLanguage\"")
                repository.searchLyricsCandidates(track)
                runCurrent()
                assertEquals(listOf(LyricsChange(track.id, contentChanged = true)), changes)

                // The consumer reloads the updated document, but keeps using the saved timing version.
                val reloaded = assertNotNull(repository.loadPlaybackLyrics(track) as? LyricsLoadResult.Found).lyrics
                val presentation = assertNotNull(parseEnhancedLyricsPresentation(reloaded.document.rawPayload, reloaded.document))
                assertEquals(newLanguage, presentation.originalLanguage)
                assertEquals(if (newLanguage == "en") "Translation" else null,
                    presentation.translationTextFor(presentation.lines.single(), true))
                assertEquals(original.version, reloaded.version)
                assertEquals(350L, repository.readOffset(track, reloaded.version))
                assertEquals(savedCalibration, database.lyricsOffsetDao().get(track.id))
                runCurrent()
                assertEquals(1, changes.size, "Reloading the same payload must not trigger a reload loop")
            }

            changes.clear()
            livePayload = livePayload.replace("xml:lang=\"zh-Hans\"", "xml:lang=\"zh-Hant\"")
            repository.searchLyricsCandidates(track)
            livePayload = livePayload.replace("<body>", "\n<body>")
            repository.searchLyricsCandidates(track)
            runCurrent()
            assertTrue(changes.isEmpty(), "Translation language and XML formatting do not change original-language visibility")
            assertEquals(savedCalibration, database.lyricsOffsetDao().get(track.id))
            observer.cancel()
        }
    }

    @Test fun `LRC and Navidrome language metadata reloads preserve timing versions`() = runTest {
        val payloads = listOf(
            "[lang:en]\n[00:01.00]Hello [00:02.00]world[00:03.00]\n[00:01.00]你[00:01.00]好[00:01.00]" to "[lang:en]",
            "[language:en]\n[00:01.00]Hello [00:02.00]world[00:03.00]\n[00:01.00]你[00:01.00]好[00:01.00]" to "[language:en]",
            """{"lyricsList":{"structuredLyrics":[{"kind":"main","lang":"en","synced":true,"line":[{"start":1000,"value":"[00:01.00]Hello [00:02.00]world[00:03.00]"},{"start":1000,"value":"[00:01.00]你[00:01.00]好[00:01.00]"}]}]}}""" to "\"lang\":\"en\"",
        )
        for ((raw, languageMarker) in payloads) {
            withDatabase { database ->
                val row = LyricsCacheEntity(track.id, source, raw, 1)
                database.writeLyricsCache(row)
                val version = requireNotNull(row.versionOrNull())
                assertTrue(database.saveLyricsOffset(track, version, -450))
                val changes = mutableListOf<LyricsChange>()
                val observer = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    LyricsChanges.changes.collect { if (it.trackId == track.id) changes += it }
                }
                val replacement = row.copy(rawPayload = raw.replace(languageMarker, languageMarker.replace("en", "zh")), updatedAt = 2)
                database.writeLyricsCache(replacement)
                runCurrent()
                assertEquals(listOf(LyricsChange(track.id, contentChanged = true)), changes)
                assertEquals(version, replacement.versionOrNull())
                assertEquals(-450L, database.readLyricsOffset(track, version))
                val reloaded = assertNotNull(repository(database).getLyrics(track))
                val presentation = assertNotNull(parseEnhancedLyricsPresentation(reloaded.document.rawPayload, reloaded.document))
                assertEquals("zh", presentation.originalLanguage)
                assertNull(presentation.translationTextFor(presentation.lines.single(), true))
                database.writeLyricsCache(replacement.copy(updatedAt = 3))
                runCurrent()
                assertEquals(1, changes.size)
                observer.cancel()
            }
        }
    }

    @Test fun `manually reapplying the same automatic lyrics preserves calibration`() = runTest {
        for (mode in listOf(LyricsSearchApplyMode.FULL, LyricsSearchApplyMode.LYRICS_ONLY)) {
            withDatabase { database ->
                val repository = repository(database)
                val document = requireNotNull(parseCachedLyrics(source, payload))
                database.writeLyricsCache(LyricsCacheEntity(track.id, source, payload, 1))
                val version = lyricsVersion(document)
                assertTrue(repository.saveOffset(track, version, 600))
                val applied = repository.applyLyricsCandidate(track.id, LyricsSearchCandidate(
                    sourceId = source, sourceName = "Provider", document = document,
                ), mode)
                assertEquals(version, applied.version)
                assertEquals(600L, repository.readOffset(track, version), mode.name)
                assertEquals(version, repository(database).getLyrics(track)?.version)
            }
        }
    }

    @Test fun `selected embedded lyrics win over live sidecar and follow subsequent tag updates`() = runTest {
        for (mode in listOf(LyricsSearchApplyMode.FULL, LyricsSearchApplyMode.LYRICS_ONLY)) {
            withDatabase { database ->
                val sidecar = object : SameNameLyricsFileGateway {
                    override suspend fun readSameNameLyrics(track: Track) = Result.success(payload)
                }
                val repository = repository(database, sidecar)
                val automatic = assertNotNull(repository.getLyrics(track))
                assertTrue(repository.saveOffset(track, automatic.version, 600))
                val embedded = requireNotNull(parseCachedLyrics(EMBEDDED_LYRICS_SOURCE_ID, payload.replace("First", "Embedded")))
                val candidate = LyricsSearchCandidate(sourceId = EMBEDDED_LYRICS_SOURCE_ID, sourceName = "Tags", document = embedded, isTrackProvided = true)
                val applied = repository.applyLyricsCandidate(track.id, candidate, mode)
                val version = assertNotNull(applied.version)
                assertEquals(0L, repository.readOffset(track, version), mode.name)
                assertTrue(repository.saveOffset(track, version, 350))
                assertFalse(repository.saveOffset(track, automatic.version, 900))
                repository.applyLyricsCandidate(track.id, candidate, mode)
                assertEquals(350L, repository.readOffset(track, version))
                val reloaded = assertNotNull(repository(database, sidecar).getLyrics(track))
                assertEquals(embedded, reloaded.document)
                assertEquals(version, reloaded.version)
                database.writeLyricsCache(LyricsCacheEntity(track.id, EMBEDDED_LYRICS_SOURCE_ID, payload.replace("First", "Updated tag"), 99))
                val updated = assertNotNull(repository.getLyrics(track))
                assertEquals("Updated tag", updated.document.lines.first().text)
                assertNotEquals(version, updated.version)
                assertEquals(0L, repository.readOffset(track, updated.version))
                assertFalse(repository.saveOffset(track, version, 900))
            }
        }
    }

    @Test fun `selecting a sidecar still reads live updates and falls back after confirmed removal`() = runTest {
        withDatabase { database ->
            var livePayload: String? = payload
            val sidecar = object : SameNameLyricsFileGateway {
                override suspend fun readSameNameLyrics(track: Track) = Result.success(livePayload)
            }
            val repository = repository(database, sidecar)
            val embedded = requireNotNull(parseCachedLyrics(EMBEDDED_LYRICS_SOURCE_ID, payload.replace("First", "Embedded")))
            repository.applyLyricsCandidate(track.id, LyricsSearchCandidate(
                sourceId = EMBEDDED_LYRICS_SOURCE_ID, sourceName = "Tags", document = embedded, isTrackProvided = true,
            ), LyricsSearchApplyMode.LYRICS_ONLY)
            val selected = repository.applyLyricsCandidate(track.id, LyricsSearchCandidate(
                sourceId = SAME_NAME_LRC_SOURCE_ID, sourceName = "LRC", isTrackProvided = true,
                document = requireNotNull(parseCachedLyrics(SAME_NAME_LRC_SOURCE_ID, payload)),
            ), LyricsSearchApplyMode.LYRICS_ONLY)
            val firstVersion = assertNotNull(selected.version)
            assertTrue(repository.saveOffset(track, firstVersion, 400))
            livePayload = payload.replace("First", "Updated file")
            val updated = assertNotNull(repository.getLyrics(track))
            assertEquals("Updated file", updated.document.lines.first().text)
            assertEquals(0L, repository.readOffset(track, updated.version))
            assertFalse(repository.saveOffset(track, firstVersion, 900))
            assertTrue(repository.saveOffset(track, updated.version, 300))
            livePayload = null
            val fallback = assertNotNull(repository.getLyrics(track))
            assertEquals(embedded, fallback.document)
            assertEquals(0L, repository.readOffset(track, fallback.version))
        }
    }

    @Test fun `separate overlay database receives committed offset and content changes`() = runTest {
        val path = Files.createTempFile("lyrics-overlay", ".db").toString()
        val main = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = path))
        val overlay = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = path))
        try {
            val row = LyricsCacheEntity(track.id, source, payload, 1)
            main.writeLyricsCache(row)
            val version = requireNotNull(row.versionOrNull())
            val reader = repository(overlay)
            assertEquals(0L, reader.readOffset(track, version))
            val offsetChange = async(start = CoroutineStart.UNDISPATCHED) { reader.changes.first { it.trackId == track.id } }
            assertTrue(repository(main).saveOffset(track, version, 500))
            assertFalse(offsetChange.await().contentChanged)
            assertEquals(500L, reader.readOffset(track, version))
            val contentChange = async(start = CoroutineStart.UNDISPATCHED) { reader.changes.first { it.trackId == track.id } }
            val replacement = row.copy(rawPayload = payload.replace("First", "Changed"))
            main.writeLyricsCache(replacement)
            assertTrue(contentChange.await().contentChanged)
            assertNull(reader.readOffset(track, version))
            assertEquals(0L, reader.readOffset(track, requireNotNull(replacement.versionOrNull())))
        } finally { main.close(); overlay.close() }
    }

    @Test fun `source cache rebuild keeps same lyrics calibration and removes changed versions`() = runTest {
        withDatabase { database ->
            database.trackDao().upsertAll(listOf(TrackEntity(
                id = track.id, sourceId = track.sourceId, title = track.title,
                artistId = null, artistName = null, albumId = null, albumTitle = null,
                durationMs = 0, trackNumber = null, discNumber = null,
                mediaLocator = track.mediaLocator, relativePath = track.relativePath,
                artworkLocator = null, sizeBytes = 0, modifiedAt = 0,
            )))
            val row = LyricsCacheEntity(track.id, source, payload, 1)
            database.writeLyricsCache(row)
            val version = requireNotNull(row.versionOrNull())
            database.saveLyricsOffset(track, version, 350)
            database.immediateWriteTransaction {
                database.lyricsCacheDao().deleteByTrackIdPrefix("track:local:%")
                database.lyricsCacheDao().upsert(row.copy(updatedAt = 999))
            }
            database.reconcileSourceLyricsOffsets("track:local:%")
            assertEquals(350L, database.readLyricsOffset(track, version))
            database.lyricsCacheDao().upsert(row.copy(rawPayload = payload.replace("First", "Changed")))
            database.reconcileSourceLyricsOffsets("track:local:%")
            assertNull(database.lyricsOffsetDao().get(track.id))
        }
    }

    @Test fun `calibration is isolated per song and survives reopening database`() = runTest {
        val path = Files.createTempFile("lyrics-calibration", ".db").toString()
        var database = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = path))
        val version = lyricsVersion(requireNotNull(parseCachedLyrics(source, payload)))
        try {
            database.writeLyricsCache(LyricsCacheEntity(track.id, source, payload, 1))
            assertTrue(database.saveLyricsOffset(track, version, 900))
            val other = track.copy(id = "other")
            database.writeLyricsCache(LyricsCacheEntity(other.id, source, payload, 1))
            assertEquals(0L, database.readLyricsOffset(other, version))
            database.close()
            database = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = path))
            assertEquals(900L, database.readLyricsOffset(track, version))
            assertTrue(database.saveLyricsOffset(track, version, 90_000))
            assertEquals(30_000L, database.readLyricsOffset(track, version))
            assertTrue(database.saveLyricsOffset(track, version, 0))
            assertEquals(0L, database.readLyricsOffset(track, version))
        } finally { database.close() }
    }

    @Test fun `same content refresh preserves calibration but replacement rejects stale readers and writers`() = runTest {
        withDatabase { database ->
            val initial = LyricsCacheEntity(track.id, source, payload, 1)
            database.writeLyricsCache(initial)
            val first = requireNotNull(initial.versionOrNull())
            database.saveLyricsOffset(track, first, 700)
            database.writeLyricsCache(initial.copy(updatedAt = 999, artworkLocator = "file:///cover.png"))
            assertEquals(700L, database.readLyricsOffset(track, first))
            val replacement = initial.copy(rawPayload = payload.replace("First", "Replaced"))
            database.writeLyricsCache(replacement)
            val second = requireNotNull(replacement.versionOrNull())
            assertNull(database.lyricsOffsetDao().get(track.id))
            assertTrue(database.saveLyricsOffset(track, second, -200))
            assertNull(database.readLyricsOffset(track, first))
            assertFalse(database.saveLyricsOffset(track, first, 900))
            assertEquals(-200L, database.readLyricsOffset(track, second))
            database.writeLyricsCache(initial)
            assertEquals(0L, database.readLyricsOffset(track, first))
        }
    }

    @Test fun `manual apply cache reload and artwork-only update retain original version`() = runTest {
        withDatabase { database ->
            val repository = repository(database)
            val document = requireNotNull(parseCachedLyrics(source, payload))
            val candidate = LyricsSearchCandidate(sourceId = source, sourceName = "Provider", document = document, artworkLocator = "file:///cover.png")
            val applied = repository.applyLyricsCandidate(track.id, candidate, LyricsSearchApplyMode.LYRICS_ONLY)
            val version = requireNotNull(applied.version)
            assertTrue(repository.saveOffset(track, version, 450))
            val cached = assertNotNull(repository.getLyrics(track))
            assertEquals(version, cached.version)
            repository.applyLyricsCandidate(track.id, candidate, LyricsSearchApplyMode.ARTWORK_ONLY)
            assertEquals(version, repository.getLyrics(track)?.version)
            assertEquals(450L, repository.readOffset(track, version))
            // A different provider with identical text is a different lyric version.
            repository.applyLyricsCandidate(track.id, candidate.copy(sourceId = "other", document = document.copy(sourceId = "other")), LyricsSearchApplyMode.LYRICS_ONLY)
            assertNull(database.lyricsOffsetDao().get(track.id))
        }
    }

    @Test fun `temporary read failure and no search match do not delete saved calibration`() = runTest {
        withDatabase { database ->
            val version = lyricsVersion(requireNotNull(parseCachedLyrics(source, payload)))
            database.lyricsOffsetDao().upsert(LyricsOffsetEntity(track.id, version, 600))
            val failed = repository(database).loadPlaybackLyrics(track)
            assertIs<LyricsLoadResult.Unavailable>(failed)
            assertEquals(600L, database.lyricsOffsetDao().get(track.id)?.offsetMs)
            database.writeLyricsCache(LyricsCacheEntity(track.id, source, payload, 3))
            assertEquals(600L, database.readLyricsOffset(track, version))
            database.removeLyricsCache(track.id, source)
            assertNull(database.lyricsOffsetDao().get(track.id))
        }
    }

    @Test fun `confirmed same-name removal differs from a failed file read`() = runTest {
        withDatabase { database ->
            val row = LyricsCacheEntity(track.id, SAME_NAME_LRC_SOURCE_ID, payload, 1)
            database.writeLyricsCache(row)
            val version = requireNotNull(row.versionOrNull())
            database.saveLyricsOffset(track, version, 500)
            val failed = object : SameNameLyricsFileGateway {
                override suspend fun readSameNameLyrics(track: Track): Result<String?> = Result.failure(Exception("offline"))
            }
            assertIs<LyricsLoadResult.Found>(repository(database, failed).loadPlaybackLyrics(track))
            assertEquals(500L, database.readLyricsOffset(track, version))
            val missing = object : SameNameLyricsFileGateway {
                override suspend fun readSameNameLyrics(track: Track): Result<String?> = Result.success(null)
            }
            assertIs<LyricsLoadResult.Removed>(repository(database, missing).loadPlaybackLyrics(track))
            assertNull(database.lyricsOffsetDao().get(track.id))
        }
    }

    @Test fun `word timing translation source and original offset contribute to version`() {
        fun version(raw: String, provider: String = source) = lyricsVersion(requireNotNull(parseCachedLyrics(provider, raw)))
        val raw = "[offset:100]\n[00:01.00]<00:01.00>One <00:02.00>two\n[00:01.00]译文"
        assertNotEquals(version(raw), version(raw.replace("<00:02.00>", "<00:02.50>")))
        assertNotEquals(version(raw), version(raw.replace("译文", "翻译")))
        assertNotEquals(version(raw), version(raw.replace("offset:100", "offset:200")))
        assertNotEquals(version(raw), version(raw, "another-provider"))
        assertEquals(version(raw), version("\n$raw\n"))
    }

    @Test fun `migration keeps old cache and starts without calibrations`() {
        val path = Files.createTempFile("lyrics-migration", ".db").toString()
        BundledSQLiteDriver().open(path).use { connection ->
            fun execute(sql: String) { connection.prepare(sql).use { it.step() } }
            execute("CREATE TABLE lyrics_cache (trackId TEXT NOT NULL, sourceId TEXT NOT NULL, rawPayload TEXT NOT NULL, updatedAt INTEGER NOT NULL, artworkLocator TEXT, PRIMARY KEY(trackId, sourceId))")
            execute("INSERT INTO lyrics_cache VALUES ('song', 'provider', 'lyrics', 4, 'cover')")
            MIGRATION_20_21.migrate(connection)
            connection.prepare("SELECT rawPayload, artworkLocator, originalSourceId, selectedForPlayback FROM lyrics_cache").use {
                assertTrue(it.step()); assertEquals("lyrics", it.getText(0)); assertEquals("cover", it.getText(1)); assertTrue(it.isNull(2))
                assertEquals(0L, it.getLong(3))
            }
            connection.prepare("SELECT COUNT(*) FROM lyrics_offset").use { assertTrue(it.step()); assertEquals(0L, it.getLong(0)) }
        }
    }

    private suspend fun withDatabase(block: suspend (LynMusicDatabase) -> Unit) {
        val path = Files.createTempFile("lyrics-calibration", ".db").toString()
        val database = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = path))
        try { block(database) } finally { database.close() }
    }

    private fun repository(database: LynMusicDatabase, sameName: SameNameLyricsFileGateway = UnsupportedSameNameLyricsFileGateway) = DefaultLyricsRepository(
        database,
        object : LyricsHttpClient { override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> = Result.failure(Exception("offline")) },
        object : SecureCredentialStore {
            override suspend fun get(key: String): String? = null
            override suspend fun put(key: String, value: String) = Unit
            override suspend fun remove(key: String) = Unit
        },
        sameNameLyricsFileGateway = sameName,
    )
}
