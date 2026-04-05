package top.iwesley.lyn.music.feature.tags

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.data.repository.AppliedWorkflowLyricsResult
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.MusicTagSaveResult
import top.iwesley.lyn.music.data.repository.MusicTagsRepository
import top.iwesley.lyn.music.data.repository.ResolvedLyricsResult
import top.iwesley.lyn.music.domain.serializeLyricsDocument

@OptIn(ExperimentalCoroutinesApi::class)
class MusicTagsStoreTest {

    @Test
    fun `loads first local track and initializes draft from snapshot`() = runTest {
        val track = sampleTrack()
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot()),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()

        val state = store.state.value
        assertEquals(track.id, state.selectedTrackId)
        assertEquals("情歌", state.draft.title)
        assertEquals("梁静茹", state.draft.artistName)
        assertEquals("ID3v2.3", state.rowMetadata[track.id]?.tagLabel)
    }

    @Test
    fun `switching tracks with unsaved changes opens discard confirmation`() = runTest {
        val first = sampleTrack(id = "track-1", title = "情歌")
        val second = sampleTrack(id = "track-2", title = "丝路", relativePath = "梁静茹/丝路.mp3")
        val repository = FakeMusicTagsRepository(
            tracks = listOf(first, second),
            snapshots = mapOf(
                first.id to sampleSnapshot(title = "情歌"),
                second.id to sampleSnapshot(title = "丝路"),
            ),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.TitleChanged("情歌 2026"))
        store.dispatch(MusicTagsIntent.SelectTrack(second.id))
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.showDiscardChangesDialog)
        assertEquals(MusicTagsPendingTrackAction.SelectOnly(second.id), state.pendingTrackAction)
        assertEquals(first.id, state.selectedTrackId)
    }

    @Test
    fun `confirm discard selection switches to pending track`() = runTest {
        val first = sampleTrack(id = "track-1", title = "情歌")
        val second = sampleTrack(id = "track-2", title = "丝路", relativePath = "梁静茹/丝路.mp3")
        val repository = FakeMusicTagsRepository(
            tracks = listOf(first, second),
            snapshots = mapOf(
                first.id to sampleSnapshot(title = "情歌"),
                second.id to sampleSnapshot(title = "丝路"),
            ),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.TitleChanged("情歌 2026"))
        store.dispatch(MusicTagsIntent.SelectTrack(second.id))
        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.ConfirmDiscardSelection)
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.showDiscardChangesDialog)
        assertEquals(second.id, state.selectedTrackId)
        assertEquals("丝路", state.draft.title)
        assertFalse(state.isDirty)
    }

    @Test
    fun `activate track with unsaved changes opens play discard confirmation`() = runTest {
        val first = sampleTrack(id = "track-1", title = "情歌")
        val second = sampleTrack(id = "track-2", title = "丝路", relativePath = "梁静茹/丝路.mp3")
        val repository = FakeMusicTagsRepository(
            tracks = listOf(first, second),
            snapshots = mapOf(
                first.id to sampleSnapshot(title = "情歌"),
                second.id to sampleSnapshot(title = "丝路"),
            ),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.TitleChanged("情歌 2026"))
        store.dispatch(MusicTagsIntent.ActivateTrack(second.id))
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.showDiscardChangesDialog)
        assertEquals(MusicTagsPendingTrackAction.SelectAndPlay(second.id), state.pendingTrackAction)
        assertEquals(first.id, state.selectedTrackId)
    }

    @Test
    fun `confirm discard selection with play action switches and emits play effect`() = runTest {
        val first = sampleTrack(id = "track-1", title = "情歌")
        val second = sampleTrack(id = "track-2", title = "丝路", relativePath = "梁静茹/丝路.mp3")
        val repository = FakeMusicTagsRepository(
            tracks = listOf(first, second),
            snapshots = mapOf(
                first.id to sampleSnapshot(title = "情歌"),
                second.id to sampleSnapshot(title = "丝路"),
            ),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()
        val effect = async { store.effects.first() }
        store.dispatch(MusicTagsIntent.TitleChanged("情歌 2026"))
        store.dispatch(MusicTagsIntent.ActivateTrack(second.id))
        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.ConfirmDiscardSelection)
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.showDiscardChangesDialog)
        assertEquals(second.id, state.selectedTrackId)
        assertFalse(state.isDirty)
        assertEquals(
            MusicTagsEffect.PlayTracks(listOf(first, second), 1),
            effect.await(),
        )
    }

    @Test
    fun `activate selected track plays immediately without discard dialog`() = runTest {
        val track = sampleTrack()
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot()),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()
        val effect = async { store.effects.first() }
        store.dispatch(MusicTagsIntent.TitleChanged("情歌 2026"))
        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.ActivateTrack(track.id))
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.showDiscardChangesDialog)
        assertEquals(
            MusicTagsEffect.PlayTracks(listOf(track), 0),
            effect.await(),
        )
        assertTrue(state.isDirty)
    }

    @Test
    fun `save success updates selected track and clears dirty state`() = runTest {
        val track = sampleTrack()
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot()),
            saveResult = Result.success(
                MusicTagSaveResult(
                    track = track.copy(
                        title = "新的情歌",
                        artistName = "新艺人",
                        albumTitle = "新专辑",
                        trackNumber = 7,
                        discNumber = 2,
                        artworkLocator = "/tmp/new-art.png",
                    ),
                    snapshot = sampleSnapshot(
                        title = "新的情歌",
                        artistName = "新艺人",
                        albumTitle = "新专辑",
                        trackNumber = 7,
                        discNumber = 2,
                        artworkLocator = "/tmp/new-art.png",
                    ),
                ),
            ),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.TitleChanged("新的情歌"))
        store.dispatch(MusicTagsIntent.Save)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("标签已保存。", state.message)
        assertFalse(state.isDirty)
        assertEquals("新的情歌", state.selectedTrack?.title)
        assertEquals("新的情歌", state.draft.title)
    }

    @Test
    fun `embedded lyrics field participates in draft save and reset`() = runTest {
        val track = sampleTrack()
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot(embeddedLyrics = "旧歌词")),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()
        assertEquals("旧歌词", store.state.value.draft.embeddedLyrics)

        store.dispatch(MusicTagsIntent.EmbeddedLyricsChanged("新的第一行\n新的第二行"))
        advanceUntilIdle()
        assertTrue(store.state.value.isDirty)

        store.dispatch(MusicTagsIntent.ResetDraft)
        advanceUntilIdle()
        assertEquals("旧歌词", store.state.value.draft.embeddedLyrics)

        store.dispatch(MusicTagsIntent.EmbeddedLyricsChanged("新的第一行\n新的第二行"))
        store.dispatch(MusicTagsIntent.Save)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("新的第一行\n新的第二行", state.draft.embeddedLyrics)
        assertFalse(state.isDirty)
    }

    @Test
    fun `refresh selected reloads track metadata from repository`() = runTest {
        val track = sampleTrack()
        val refreshedSnapshot = sampleSnapshot(
            title = "外部改过的标题",
            artistName = "外部艺人",
            albumTitle = "外部专辑",
            trackNumber = 9,
            discNumber = 3,
            artworkLocator = "/tmp/external-art.png",
        )
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot()),
            refreshResult = Result.success(
                MusicTagSaveResult(
                    track = track.copy(
                        title = refreshedSnapshot.title,
                        artistName = refreshedSnapshot.artistName,
                        albumTitle = refreshedSnapshot.albumTitle,
                        trackNumber = refreshedSnapshot.trackNumber,
                        discNumber = refreshedSnapshot.discNumber,
                        artworkLocator = refreshedSnapshot.artworkLocator,
                    ),
                    snapshot = refreshedSnapshot,
                ),
            ),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.RefreshSelected)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("标签已刷新。", state.message)
        assertEquals("外部改过的标题", state.selectedTrack?.title)
        assertEquals("外部改过的标题", state.draft.title)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `save failure keeps draft and exposes error message`() = runTest {
        val track = sampleTrack()
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot()),
            saveResult = Result.failure(IllegalStateException("写回失败")),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.TitleChanged("失败版本"))
        store.dispatch(MusicTagsIntent.Save)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("写回失败", state.message)
        assertTrue(state.isDirty)
        assertEquals("失败版本", state.draft.title)
    }

    @Test
    fun `cannot save when platform is not writable`() = runTest {
        val track = sampleTrack()
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot()),
            writableTrackIds = emptySet(),
        )
        val store = createStore(repository, testScheduler)

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.TitleChanged("不可写"))
        store.dispatch(MusicTagsIntent.Save)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("当前平台暂不支持本地标签写回。", state.message)
        assertTrue(state.isDirty)
    }

    @Test
    fun `open online lyrics search prefills current draft and search excludes track provided candidate`() = runTest {
        val track = sampleTrack()
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot()),
        )
        val lyricsRepository = FakeMusicTagsLyricsRepository(
            searchResults = listOf(
                LyricsSearchCandidate(
                    sourceId = "direct-1",
                    sourceName = "Direct",
                    document = plainLyricsDocument("direct-1", "第一句"),
                    title = "搜索标题",
                    artistName = "搜索歌手",
                    albumTitle = "搜索专辑",
                ),
            ),
        )
        val store = createStore(repository, testScheduler, lyricsRepository = lyricsRepository)

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.TitleChanged("草稿标题"))
        store.dispatch(MusicTagsIntent.ArtistChanged("草稿歌手"))
        store.dispatch(MusicTagsIntent.AlbumChanged("草稿专辑"))
        advanceUntilIdle()

        store.dispatch(MusicTagsIntent.OpenOnlineLyricsSearch)
        advanceUntilIdle()

        assertEquals("草稿标题", store.state.value.onlineLyricsSearch.title)
        assertEquals("草稿歌手", store.state.value.onlineLyricsSearch.artistName)
        assertEquals("草稿专辑", store.state.value.onlineLyricsSearch.albumTitle)

        store.dispatch(MusicTagsIntent.SearchOnlineLyrics)
        advanceUntilIdle()

        assertEquals(track.id, lyricsRepository.lastSearchTrack?.id)
        assertEquals("草稿标题", lyricsRepository.lastSearchTrack?.title)
        assertEquals("草稿歌手", lyricsRepository.lastSearchTrack?.artistName)
        assertEquals("草稿专辑", lyricsRepository.lastSearchTrack?.albumTitle)
        assertEquals(false, lyricsRepository.lastIncludeTrackProvidedCandidate)
        assertEquals(1, store.state.value.onlineLyricsSearch.directResults.size)
    }

    @Test
    fun `apply direct online lyrics candidate writes draft and does not save immediately`() = runTest {
        val track = sampleTrack()
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot()),
        )
        val lyricsDocument = LyricsDocument(
            lines = listOf(
                LyricsLine(timestampMs = 1_000L, text = "第一句"),
                LyricsLine(timestampMs = 2_000L, text = "第二句"),
            ),
            sourceId = "direct-lrc",
            rawPayload = "ignored",
        )
        val candidate = LyricsSearchCandidate(
            sourceId = "direct-lrc",
            sourceName = "Direct",
            document = lyricsDocument,
            title = "导入标题",
            artistName = "导入歌手",
            albumTitle = "导入专辑",
        )
        val lyricsRepository = FakeMusicTagsLyricsRepository(searchResults = listOf(candidate))
        val store = createStore(repository, testScheduler, lyricsRepository = lyricsRepository)

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.OpenOnlineLyricsSearch)
        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.ApplyOnlineLyricsCandidate(candidate))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("导入标题", state.draft.title)
        assertEquals("导入歌手", state.draft.artistName)
        assertEquals("导入专辑", state.draft.albumTitle)
        assertEquals(serializeLyricsDocument(lyricsDocument), state.draft.embeddedLyrics)
        assertTrue(state.isDirty)
        assertFalse(state.onlineLyricsSearch.isVisible)
        assertEquals("已写入编辑器，点击保存可写回文件。", state.message)
        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun `apply workflow online lyrics candidate imports artwork bytes into draft`() = runTest {
        val track = sampleTrack()
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot()),
        )
        val workflowCandidate = WorkflowSongCandidate(
            sourceId = "workflow-1",
            sourceName = "Workflow",
            id = "song-1",
            title = "候选标题",
            artists = listOf("甲", "乙"),
            album = "候选专辑",
            durationSeconds = 180,
            imageUrl = "https://cover.test/workflow.jpg",
        )
        val resolvedDocument = plainLyricsDocument("workflow-1", "工作流歌词")
        val lyricsRepository = FakeMusicTagsLyricsRepository(
            workflowResults = listOf(workflowCandidate),
            resolvedWorkflowResult = Result.success(
                ResolvedLyricsResult(
                    document = resolvedDocument,
                    artworkLocator = workflowCandidate.imageUrl,
                ),
            ),
        )
        val editorService = FakeAudioTagEditorPlatformService(
            loadArtworkResults = mapOf(workflowCandidate.imageUrl!! to Result.success(byteArrayOf(1, 2, 3))),
        )
        val store = createStore(
            repository,
            testScheduler,
            lyricsRepository = lyricsRepository,
            editorService = editorService,
        )

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.OpenOnlineLyricsSearch)
        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.ApplyOnlineWorkflowSongCandidate(workflowCandidate))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(track.id, lyricsRepository.lastResolvedWorkflowTrack?.id)
        assertEquals(workflowCandidate, lyricsRepository.lastResolvedWorkflowCandidate)
        assertEquals("候选标题", state.draft.title)
        assertEquals("甲 / 乙", state.draft.artistName)
        assertEquals("候选专辑", state.draft.albumTitle)
        assertEquals(serializeLyricsDocument(resolvedDocument), state.draft.embeddedLyrics)
        assertEquals(listOf<Byte>(1, 2, 3), state.draft.pendingArtworkBytes?.toList())
        assertFalse(state.draft.clearArtwork)
        assertTrue(state.isDirty)
        assertEquals("已写入编辑器，点击保存可写回文件。", state.message)
        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun `online lyrics artwork import failure keeps lyrics and shows non blocking message`() = runTest {
        val track = sampleTrack()
        val repository = FakeMusicTagsRepository(
            tracks = listOf(track),
            snapshots = mapOf(track.id to sampleSnapshot()),
        )
        val candidate = LyricsSearchCandidate(
            sourceId = "direct-cover",
            sourceName = "Direct",
            document = plainLyricsDocument("direct-cover", "带封面歌词"),
            artworkLocator = "https://cover.test/fail.jpg",
        )
        val editorService = FakeAudioTagEditorPlatformService(
            loadArtworkResults = mapOf(candidate.artworkLocator!! to Result.failure(IllegalStateException("下载失败"))),
        )
        val store = createStore(
            repository,
            testScheduler,
            lyricsRepository = FakeMusicTagsLyricsRepository(searchResults = listOf(candidate)),
            editorService = editorService,
        )

        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.OpenOnlineLyricsSearch)
        advanceUntilIdle()
        store.dispatch(MusicTagsIntent.ApplyOnlineLyricsCandidate(candidate))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("带封面歌词", state.draft.embeddedLyrics)
        assertNull(state.draft.pendingArtworkBytes)
        assertEquals("已写入编辑器，封面导入失败：下载失败", state.message)
        assertTrue(state.isDirty)
    }

    private fun createStore(
        repository: FakeMusicTagsRepository,
        scheduler: TestCoroutineScheduler,
        lyricsRepository: LyricsRepository = FakeMusicTagsLyricsRepository(),
        editorService: AudioTagEditorPlatformService = FakeAudioTagEditorPlatformService(),
    ): MusicTagsStore {
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        return MusicTagsStore(
            repository = repository,
            lyricsRepository = lyricsRepository,
            editorPlatformService = editorService,
            storeScope = scope,
        )
    }
}

private class FakeMusicTagsRepository(
    tracks: List<Track>,
    private val snapshots: Map<String, AudioTagSnapshot>,
    private val writableTrackIds: Set<String> = snapshots.keys,
    private val refreshResult: Result<MusicTagSaveResult>? = null,
    private val saveResult: Result<MusicTagSaveResult>? = null,
) : MusicTagsRepository {
    private val mutableTracks = MutableStateFlow(tracks)
    var saveCalls: Int = 0
        private set

    override val localTracks: Flow<List<Track>> = mutableTracks.asStateFlow()

    override suspend fun canEdit(track: Track): Boolean = track.id in snapshots

    override suspend fun canWrite(track: Track): Boolean = track.id in writableTrackIds

    override suspend fun readTags(track: Track): Result<AudioTagSnapshot> {
        return snapshots[track.id]?.let(Result.Companion::success)
            ?: Result.failure(IllegalStateException("missing snapshot"))
    }

    override suspend fun refreshTags(track: Track): Result<MusicTagSaveResult> {
        val configured = refreshResult
        if (configured != null) {
            configured.getOrNull()?.let { result ->
                mutableTracks.value = mutableTracks.value.map { existing ->
                    if (existing.id == result.track.id) result.track else existing
                }
            }
            return configured
        }
        val snapshot = snapshots[track.id] ?: return Result.failure(IllegalStateException("missing snapshot"))
        return Result.success(
            MusicTagSaveResult(
                track = track.copy(
                    title = snapshot.title,
                    artistName = snapshot.artistName,
                    albumTitle = snapshot.albumTitle,
                    trackNumber = snapshot.trackNumber,
                    discNumber = snapshot.discNumber,
                    artworkLocator = snapshot.artworkLocator,
                ),
                snapshot = snapshot,
            ),
        )
    }

    override suspend fun saveTags(track: Track, patch: AudioTagPatch): Result<MusicTagSaveResult> {
        saveCalls += 1
        val configured = saveResult
        if (configured != null) {
            configured.getOrNull()?.let { result ->
                mutableTracks.value = mutableTracks.value.map { existing ->
                    if (existing.id == result.track.id) result.track else existing
                }
            }
            return configured
        }
        val snapshot = snapshots[track.id] ?: return Result.failure(IllegalStateException("missing snapshot"))
        val updatedSnapshot = snapshot.copy(
            title = patch.title ?: snapshot.title,
            artistName = patch.artistName ?: snapshot.artistName,
            albumTitle = patch.albumTitle ?: snapshot.albumTitle,
            albumArtist = patch.albumArtist ?: snapshot.albumArtist,
            year = patch.year ?: snapshot.year,
            genre = patch.genre ?: snapshot.genre,
            comment = patch.comment ?: snapshot.comment,
            composer = patch.composer ?: snapshot.composer,
            embeddedLyrics = patch.embeddedLyrics ?: snapshot.embeddedLyrics,
            isCompilation = patch.isCompilation ?: snapshot.isCompilation,
            trackNumber = patch.trackNumber ?: snapshot.trackNumber,
            discNumber = patch.discNumber ?: snapshot.discNumber,
        )
        val updatedTrack = track.copy(
            title = updatedSnapshot.title,
            artistName = updatedSnapshot.artistName,
            albumTitle = updatedSnapshot.albumTitle,
            trackNumber = updatedSnapshot.trackNumber,
            discNumber = updatedSnapshot.discNumber,
            artworkLocator = updatedSnapshot.artworkLocator,
        )
        mutableTracks.value = mutableTracks.value.map { existing ->
            if (existing.id == updatedTrack.id) updatedTrack else existing
        }
        return Result.success(MusicTagSaveResult(updatedTrack, updatedSnapshot))
    }
}

private class FakeMusicTagsLyricsRepository(
    private val searchResults: List<LyricsSearchCandidate> = emptyList(),
    private val workflowResults: List<WorkflowSongCandidate> = emptyList(),
    private val resolvedWorkflowResult: Result<ResolvedLyricsResult> = Result.failure(IllegalStateException("missing resolve result")),
) : LyricsRepository {
    var lastSearchTrack: Track? = null
        private set
    var lastIncludeTrackProvidedCandidate: Boolean? = null
        private set
    var lastWorkflowSearchTrack: Track? = null
        private set
    var lastResolvedWorkflowTrack: Track? = null
        private set
    var lastResolvedWorkflowCandidate: WorkflowSongCandidate? = null
        private set

    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? = null

    override suspend fun searchLyricsCandidates(
        track: Track,
        includeTrackProvidedCandidate: Boolean,
    ): List<LyricsSearchCandidate> {
        lastSearchTrack = track
        lastIncludeTrackProvidedCandidate = includeTrackProvidedCandidate
        return searchResults
    }

    override suspend fun applyLyricsCandidate(trackId: String, candidate: LyricsSearchCandidate): LyricsDocument {
        error("Not used in music tags tests")
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> {
        lastWorkflowSearchTrack = track
        return workflowResults
    }

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        lastResolvedWorkflowTrack = track
        lastResolvedWorkflowCandidate = candidate
        return resolvedWorkflowResult.getOrThrow()
    }

    override suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate): AppliedWorkflowLyricsResult {
        error("Not used in music tags tests")
    }
}

private class FakeAudioTagEditorPlatformService(
    private val pickArtworkResult: Result<ByteArray?> = Result.success(null),
    private val loadArtworkResults: Map<String, Result<ByteArray?>> = emptyMap(),
) : AudioTagEditorPlatformService {
    override suspend fun pickArtworkBytes(): Result<ByteArray?> = pickArtworkResult

    override suspend fun loadArtworkBytes(locator: String): Result<ByteArray?> {
        return loadArtworkResults[locator]
            ?: Result.failure(IllegalStateException("missing artwork for $locator"))
    }
}

private fun sampleTrack(
    id: String = "track-1",
    title: String = "情歌",
    relativePath: String = "梁静茹/情歌.mp3",
): Track {
    return Track(
        id = id,
        sourceId = "local-1",
        title = title,
        artistName = "梁静茹",
        albumTitle = "静茹 & 情歌",
        durationMs = 260_000,
        trackNumber = 1,
        discNumber = 1,
        mediaLocator = "/music/$relativePath",
        relativePath = relativePath,
        artworkLocator = "/tmp/artwork.png",
        modifiedAt = 1_700_000_000_000,
    )
}

private fun sampleSnapshot(
    title: String = "情歌",
    artistName: String = "梁静茹",
    albumTitle: String = "静茹 & 情歌",
    trackNumber: Int = 1,
    discNumber: Int = 1,
    embeddedLyrics: String? = null,
    artworkLocator: String? = "/tmp/artwork.png",
): AudioTagSnapshot {
    return AudioTagSnapshot(
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        albumArtist = "梁静茹",
        year = 2009,
        genre = "Pop",
        comment = "comment",
        composer = "composer",
        isCompilation = false,
        tagLabel = "ID3v2.3",
        trackNumber = trackNumber,
        discNumber = discNumber,
        embeddedLyrics = embeddedLyrics,
        artworkLocator = artworkLocator,
    )
}

private fun plainLyricsDocument(
    sourceId: String,
    text: String,
): LyricsDocument {
    return LyricsDocument(
        lines = listOf(LyricsLine(timestampMs = null, text = text)),
        sourceId = sourceId,
        rawPayload = text,
    )
}
