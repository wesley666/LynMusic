package top.iwesley.lyn.music.feature.tags

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.AudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.MusicTagSaveResult
import top.iwesley.lyn.music.data.repository.MusicTagsRepository
import top.iwesley.lyn.music.domain.serializeLyricsDocument

data class MusicTagsRowMetadata(
    val tagLabel: String? = null,
    val albumArtist: String? = null,
)

sealed interface MusicTagsPendingTrackAction {
    val trackId: String

    data class SelectOnly(override val trackId: String) : MusicTagsPendingTrackAction
    data class SelectAndPlay(override val trackId: String) : MusicTagsPendingTrackAction
}

data class MusicTagsDraft(
    val title: String = "",
    val artistName: String = "",
    val albumTitle: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val genre: String = "",
    val comment: String = "",
    val albumArtist: String = "",
    val composer: String = "",
    val discNumber: String = "",
    val embeddedLyrics: String = "",
    val isCompilation: Boolean = false,
    val artworkLocator: String? = null,
    val pendingArtworkBytes: ByteArray? = null,
    val clearArtwork: Boolean = false,
)

data class MusicTagsLyricsSearchState(
    val isVisible: Boolean = false,
    val title: String = "",
    val artistName: String = "",
    val albumTitle: String = "",
    val isLoading: Boolean = false,
    val hasResult: Boolean = false,
    val directResults: List<LyricsSearchCandidate> = emptyList(),
    val workflowResults: List<WorkflowSongCandidate> = emptyList(),
    val error: String? = null,
)

data class MusicTagsState(
    val tracks: List<Track> = emptyList(),
    val rowMetadata: Map<String, MusicTagsRowMetadata> = emptyMap(),
    val rowMetadataLoadingIds: Set<String> = emptySet(),
    val selectedTrackId: String? = null,
    val selectedSnapshot: AudioTagSnapshot? = null,
    val draft: MusicTagsDraft = MusicTagsDraft(),
    val isDirty: Boolean = false,
    val canEditSelected: Boolean = false,
    val canWriteSelected: Boolean = false,
    val isLoadingSelected: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val onlineLyricsSearch: MusicTagsLyricsSearchState = MusicTagsLyricsSearchState(),
    val pendingTrackAction: MusicTagsPendingTrackAction? = null,
    val showDiscardChangesDialog: Boolean = false,
    val message: String? = null,
) {
    val selectedTrack: Track?
        get() = tracks.firstOrNull { it.id == selectedTrackId }
}

sealed interface MusicTagsIntent {
    data class SelectTrack(val trackId: String) : MusicTagsIntent
    data class ActivateTrack(val trackId: String) : MusicTagsIntent
    data class EnsureRowMetadata(val trackId: String) : MusicTagsIntent
    data object ConfirmDiscardSelection : MusicTagsIntent
    data object DismissDiscardSelection : MusicTagsIntent
    data class TitleChanged(val value: String) : MusicTagsIntent
    data class ArtistChanged(val value: String) : MusicTagsIntent
    data class AlbumChanged(val value: String) : MusicTagsIntent
    data class YearChanged(val value: String) : MusicTagsIntent
    data class TrackNumberChanged(val value: String) : MusicTagsIntent
    data class GenreChanged(val value: String) : MusicTagsIntent
    data class CommentChanged(val value: String) : MusicTagsIntent
    data class AlbumArtistChanged(val value: String) : MusicTagsIntent
    data class ComposerChanged(val value: String) : MusicTagsIntent
    data class DiscNumberChanged(val value: String) : MusicTagsIntent
    data class EmbeddedLyricsChanged(val value: String) : MusicTagsIntent
    data class CompilationChanged(val value: Boolean) : MusicTagsIntent
    data object OpenOnlineLyricsSearch : MusicTagsIntent
    data object DismissOnlineLyricsSearch : MusicTagsIntent
    data class OnlineLyricsTitleChanged(val value: String) : MusicTagsIntent
    data class OnlineLyricsArtistChanged(val value: String) : MusicTagsIntent
    data class OnlineLyricsAlbumChanged(val value: String) : MusicTagsIntent
    data object SearchOnlineLyrics : MusicTagsIntent
    data class ApplyOnlineLyricsCandidate(val candidate: LyricsSearchCandidate) : MusicTagsIntent
    data class ApplyOnlineWorkflowSongCandidate(val candidate: WorkflowSongCandidate) : MusicTagsIntent
    data object PickArtwork : MusicTagsIntent
    data object ClearArtwork : MusicTagsIntent
    data object ResetDraft : MusicTagsIntent
    data object RefreshSelected : MusicTagsIntent
    data object Save : MusicTagsIntent
    data object ClearMessage : MusicTagsIntent
}

sealed interface MusicTagsEffect {
    data class PlayTracks(val tracks: List<Track>, val startIndex: Int) : MusicTagsEffect
}

class MusicTagsStore(
    private val repository: MusicTagsRepository,
    private val lyricsRepository: LyricsRepository,
    private val editorPlatformService: AudioTagEditorPlatformService,
    private val storeScope: CoroutineScope,
) : BaseStore<MusicTagsState, MusicTagsIntent, MusicTagsEffect>(
    initialState = MusicTagsState(),
    scope = storeScope,
) {
    private var selectedLoadVersion = 0L
    private var onlineLyricsSearchVersion = 0L

    init {
        storeScope.launch {
            repository.localTracks.collect { tracks ->
                val previousSelectedId = state.value.selectedTrackId
                val nextSelectedId = previousSelectedId?.takeIf { currentId ->
                    tracks.any { it.id == currentId }
                } ?: tracks.firstOrNull()?.id
                updateState { current ->
                    current.copy(
                        tracks = tracks,
                        selectedTrackId = nextSelectedId,
                    )
                }
                if (nextSelectedId != null && nextSelectedId != previousSelectedId) {
                    loadSelectedTrack(nextSelectedId)
                }
                if (nextSelectedId == null) {
                    updateState {
                        it.copy(
                            selectedSnapshot = null,
                            draft = MusicTagsDraft(),
                            isDirty = false,
                            canEditSelected = false,
                            canWriteSelected = false,
                            isLoadingSelected = false,
                            onlineLyricsSearch = MusicTagsLyricsSearchState(),
                        )
                    }
                }
            }
        }
    }

    override suspend fun handleIntent(intent: MusicTagsIntent) {
        when (intent) {
            MusicTagsIntent.ClearMessage -> updateState { it.copy(message = null) }
            MusicTagsIntent.ConfirmDiscardSelection -> {
                val pendingAction = state.value.pendingTrackAction
                if (pendingAction != null) {
                    discardAndHandlePendingAction(pendingAction)
                } else {
                    updateState {
                        it.copy(
                            pendingTrackAction = null,
                            showDiscardChangesDialog = false,
                        )
                    }
                }
            }

            MusicTagsIntent.DismissDiscardSelection -> updateState {
                it.copy(
                    pendingTrackAction = null,
                    showDiscardChangesDialog = false,
                )
            }

            MusicTagsIntent.ResetDraft -> resetDraft()
            MusicTagsIntent.RefreshSelected -> refreshSelectedTrack()
            MusicTagsIntent.Save -> saveSelectedTrack()
            MusicTagsIntent.OpenOnlineLyricsSearch -> openOnlineLyricsSearch()
            MusicTagsIntent.DismissOnlineLyricsSearch -> dismissOnlineLyricsSearch()
            MusicTagsIntent.SearchOnlineLyrics -> searchOnlineLyrics()
            MusicTagsIntent.PickArtwork -> pickArtwork()
            MusicTagsIntent.ClearArtwork -> updateDraft {
                copy(
                    pendingArtworkBytes = null,
                    clearArtwork = artworkLocator != null || pendingArtworkBytes != null,
                )
            }

            is MusicTagsIntent.SelectTrack -> selectTrack(intent.trackId)
            is MusicTagsIntent.ActivateTrack -> activateTrack(intent.trackId)
            is MusicTagsIntent.EnsureRowMetadata -> ensureRowMetadata(intent.trackId)
            is MusicTagsIntent.TitleChanged -> updateDraft { copy(title = intent.value) }
            is MusicTagsIntent.ArtistChanged -> updateDraft { copy(artistName = intent.value) }
            is MusicTagsIntent.AlbumChanged -> updateDraft { copy(albumTitle = intent.value) }
            is MusicTagsIntent.YearChanged -> updateDraft { copy(year = intent.value) }
            is MusicTagsIntent.TrackNumberChanged -> updateDraft { copy(trackNumber = intent.value) }
            is MusicTagsIntent.GenreChanged -> updateDraft { copy(genre = intent.value) }
            is MusicTagsIntent.CommentChanged -> updateDraft { copy(comment = intent.value) }
            is MusicTagsIntent.AlbumArtistChanged -> updateDraft { copy(albumArtist = intent.value) }
            is MusicTagsIntent.ComposerChanged -> updateDraft { copy(composer = intent.value) }
            is MusicTagsIntent.DiscNumberChanged -> updateDraft { copy(discNumber = intent.value) }
            is MusicTagsIntent.EmbeddedLyricsChanged -> updateDraft { copy(embeddedLyrics = intent.value) }
            is MusicTagsIntent.CompilationChanged -> updateDraft { copy(isCompilation = intent.value) }
            is MusicTagsIntent.OnlineLyricsTitleChanged -> updateOnlineLyricsSearch { copy(title = intent.value) }
            is MusicTagsIntent.OnlineLyricsArtistChanged -> updateOnlineLyricsSearch { copy(artistName = intent.value) }
            is MusicTagsIntent.OnlineLyricsAlbumChanged -> updateOnlineLyricsSearch { copy(albumTitle = intent.value) }
            is MusicTagsIntent.ApplyOnlineLyricsCandidate -> applyOnlineLyricsCandidate(intent.candidate)
            is MusicTagsIntent.ApplyOnlineWorkflowSongCandidate -> applyOnlineWorkflowSongCandidate(intent.candidate)
        }
    }

    private suspend fun selectTrack(trackId: String) {
        if (trackId == state.value.selectedTrackId) return
        if (state.value.isDirty) {
            updateState {
                it.copy(
                    pendingTrackAction = MusicTagsPendingTrackAction.SelectOnly(trackId),
                    showDiscardChangesDialog = true,
                )
            }
            return
        }
        updateState {
            it.copy(
                selectedTrackId = trackId,
                pendingTrackAction = null,
                showDiscardChangesDialog = false,
            )
        }
        loadSelectedTrack(trackId)
    }

    private suspend fun activateTrack(trackId: String) {
        val current = state.value
        if (trackId == current.selectedTrackId) {
            emitPlayTracksFor(trackId)
            return
        }
        if (current.isDirty) {
            updateState {
                it.copy(
                    pendingTrackAction = MusicTagsPendingTrackAction.SelectAndPlay(trackId),
                    showDiscardChangesDialog = true,
                )
            }
            return
        }
        updateState {
            it.copy(
                selectedTrackId = trackId,
                pendingTrackAction = null,
                showDiscardChangesDialog = false,
            )
        }
        emitPlayTracksFor(trackId)
        loadSelectedTrack(trackId)
    }

    private suspend fun discardAndHandlePendingAction(action: MusicTagsPendingTrackAction) {
        if (action.trackId == state.value.selectedTrackId) {
            updateState {
                it.copy(
                    pendingTrackAction = null,
                    showDiscardChangesDialog = false,
                    isDirty = false,
                )
            }
            if (action is MusicTagsPendingTrackAction.SelectAndPlay) {
                emitPlayTracksFor(action.trackId)
            }
            return
        }
        updateState {
            it.copy(
                selectedTrackId = action.trackId,
                pendingTrackAction = null,
                showDiscardChangesDialog = false,
                isDirty = false,
            )
        }
        if (action is MusicTagsPendingTrackAction.SelectAndPlay) {
            emitPlayTracksFor(action.trackId)
        }
        loadSelectedTrack(action.trackId)
    }

    private suspend fun emitPlayTracksFor(trackId: String) {
        val tracks = state.value.tracks
        val startIndex = tracks.indexOfFirst { it.id == trackId }
        if (startIndex < 0) return
        emitEffect(MusicTagsEffect.PlayTracks(tracks = tracks, startIndex = startIndex))
    }

    private suspend fun loadSelectedTrack(trackId: String) {
        val track = state.value.tracks.firstOrNull { it.id == trackId } ?: return
        val loadVersion = ++selectedLoadVersion
        updateState {
            it.copy(
                selectedTrackId = trackId,
                selectedSnapshot = null,
                draft = MusicTagsDraft(),
                isDirty = false,
                canEditSelected = false,
                canWriteSelected = false,
                isLoadingSelected = true,
                isRefreshing = false,
                onlineLyricsSearch = MusicTagsLyricsSearchState(),
                pendingTrackAction = null,
                message = null,
            )
        }
        invalidateOnlineLyricsSearch()
        val canEdit = repository.canEdit(track)
        val canWrite = repository.canWrite(track)
        val result = if (canEdit) repository.readTags(track) else Result.failure(IllegalStateException("当前歌曲不支持标签读取。"))
        if (loadVersion != selectedLoadVersion || state.value.selectedTrackId != trackId) return
        result
            .onSuccess { snapshot ->
                updateState { current ->
                    current.copy(
                        selectedSnapshot = snapshot,
                        draft = snapshot.toDraft(),
                        isDirty = false,
                        canEditSelected = canEdit,
                        canWriteSelected = canWrite,
                        isLoadingSelected = false,
                        isRefreshing = false,
                        rowMetadata = current.rowMetadata + (trackId to snapshot.toRowMetadata()),
                        rowMetadataLoadingIds = current.rowMetadataLoadingIds - trackId,
                    )
                }
            }
            .onFailure { throwable ->
                updateState {
                    it.copy(
                        selectedSnapshot = null,
                        draft = MusicTagsDraft(),
                        isDirty = false,
                        canEditSelected = canEdit,
                        canWriteSelected = canWrite,
                        isLoadingSelected = false,
                        isRefreshing = false,
                        rowMetadataLoadingIds = it.rowMetadataLoadingIds - trackId,
                        message = throwable.message ?: "读取音频标签失败。",
                    )
                }
            }
    }

    private suspend fun ensureRowMetadata(trackId: String) {
        val current = state.value
        if (trackId in current.rowMetadata || trackId in current.rowMetadataLoadingIds) return
        val track = current.tracks.firstOrNull { it.id == trackId } ?: return
        updateState { it.copy(rowMetadataLoadingIds = it.rowMetadataLoadingIds + trackId) }
        repository.readTags(track)
            .onSuccess { snapshot ->
                updateState {
                    it.copy(
                        rowMetadata = it.rowMetadata + (trackId to snapshot.toRowMetadata()),
                        rowMetadataLoadingIds = it.rowMetadataLoadingIds - trackId,
                    )
                }
            }
            .onFailure {
                updateState {
                    it.copy(
                        rowMetadataLoadingIds = it.rowMetadataLoadingIds - trackId,
                    )
                }
            }
    }

    private suspend fun pickArtwork() {
        if (!state.value.canWriteSelected) {
            updateState { it.copy(message = "当前平台暂不支持本地标签写回。") }
            return
        }
        editorPlatformService.pickArtworkBytes()
            .onSuccess { bytes ->
                if (bytes != null) {
                    updateDraft {
                        copy(
                            pendingArtworkBytes = bytes,
                            clearArtwork = false,
                        )
                    }
                }
            }
            .onFailure { throwable ->
                updateState { it.copy(message = throwable.message ?: "选择封面失败。") }
            }
    }

    private suspend fun openOnlineLyricsSearch() {
        val current = state.value
        if (!current.canWriteSelected || current.selectedTrack == null) return
        invalidateOnlineLyricsSearch()
        updateState {
            it.copy(
                onlineLyricsSearch = MusicTagsLyricsSearchState(
                    isVisible = true,
                    title = current.draft.title,
                    artistName = current.draft.artistName,
                    albumTitle = current.draft.albumTitle,
                ),
                message = null,
            )
        }
    }

    private suspend fun dismissOnlineLyricsSearch() {
        invalidateOnlineLyricsSearch()
        updateState { it.copy(onlineLyricsSearch = MusicTagsLyricsSearchState()) }
    }

    private suspend fun searchOnlineLyrics() {
        val current = state.value
        val selectedTrack = current.selectedTrack ?: return
        val searchState = current.onlineLyricsSearch
        val title = searchState.title.trim()
        if (title.isBlank()) {
            updateState {
                it.copy(
                    onlineLyricsSearch = searchState.copy(
                        isLoading = false,
                        hasResult = false,
                        directResults = emptyList(),
                        workflowResults = emptyList(),
                        error = "标题不能为空",
                    ),
                )
            }
            return
        }
        val searchTrack = selectedTrack.copy(
            title = title,
            artistName = searchState.artistName.trim().ifBlank { null },
            albumTitle = searchState.albumTitle.trim().ifBlank { null },
        )
        val requestVersion = ++onlineLyricsSearchVersion
        updateState {
            it.copy(
                onlineLyricsSearch = searchState.copy(
                    isLoading = true,
                    hasResult = false,
                    directResults = emptyList(),
                    workflowResults = emptyList(),
                    error = null,
                ),
                message = null,
            )
        }
        val directResult = runCatching {
            lyricsRepository.searchLyricsCandidates(
                track = searchTrack,
                includeTrackProvidedCandidate = false,
            )
        }
        val workflowResult = runCatching { lyricsRepository.searchWorkflowSongCandidates(searchTrack) }
        if (requestVersion != onlineLyricsSearchVersion || state.value.selectedTrackId != selectedTrack.id) return
        updateState { latest ->
            latest.copy(
                onlineLyricsSearch = latest.onlineLyricsSearch.copy(
                    isLoading = false,
                    hasResult = true,
                    directResults = directResult.getOrDefault(emptyList()),
                    workflowResults = workflowResult.getOrDefault(emptyList()),
                    error = directResult.exceptionOrNull()?.message ?: workflowResult.exceptionOrNull()?.message,
                ),
            )
        }
    }

    private suspend fun applyOnlineLyricsCandidate(candidate: LyricsSearchCandidate) {
        applySearchImport(
            documentText = serializeLyricsDocument(candidate.document),
            title = candidate.title,
            artistName = candidate.artistName,
            albumTitle = candidate.albumTitle,
            artworkLocator = candidate.artworkLocator,
        )
    }

    private suspend fun applyOnlineWorkflowSongCandidate(candidate: WorkflowSongCandidate) {
        val searchTrack = state.value.selectedTrack?.let(::buildOnlineSearchTrack) ?: return
        val resolved = runCatching {
            lyricsRepository.resolveWorkflowSongCandidate(searchTrack, candidate)
        }.getOrElse { throwable ->
            updateState { it.copy(message = throwable.message ?: "在线歌词获取失败。") }
            return
        }
        applySearchImport(
            documentText = serializeLyricsDocument(resolved.document),
            title = candidate.title,
            artistName = candidate.artists.joinToString(" / ").ifBlank { null },
            albumTitle = candidate.album,
            artworkLocator = resolved.artworkLocator,
        )
    }

    private suspend fun applySearchImport(
        documentText: String,
        title: String?,
        artistName: String?,
        albumTitle: String?,
        artworkLocator: String?,
    ) {
        val artworkImport = loadArtworkForDraft(artworkLocator)
        updateState { current ->
            val nextDraft = current.draft.copy(
                title = title?.trim()?.takeIf { it.isNotBlank() } ?: current.draft.title,
                artistName = artistName?.trim()?.takeIf { it.isNotBlank() } ?: current.draft.artistName,
                albumTitle = albumTitle?.trim()?.takeIf { it.isNotBlank() } ?: current.draft.albumTitle,
                embeddedLyrics = documentText,
                pendingArtworkBytes = artworkImport.bytes ?: current.draft.pendingArtworkBytes,
                clearArtwork = if (artworkImport.bytes != null) false else current.draft.clearArtwork,
            )
            current.copy(
                draft = nextDraft,
                isDirty = current.selectedSnapshot?.let { nextDraft.isDirtyComparedTo(it) } ?: false,
                onlineLyricsSearch = MusicTagsLyricsSearchState(),
                message = artworkImport.message ?: "已写入编辑器，点击保存可写回文件。",
            )
        }
    }

    private fun buildOnlineSearchTrack(selectedTrack: Track): Track {
        val searchState = state.value.onlineLyricsSearch
        return selectedTrack.copy(
            title = searchState.title.trim().ifBlank { selectedTrack.title },
            artistName = searchState.artistName.trim().ifBlank { selectedTrack.artistName },
            albumTitle = searchState.albumTitle.trim().ifBlank { selectedTrack.albumTitle },
        )
    }

    private suspend fun loadArtworkForDraft(locator: String?): ImportedArtworkResult {
        val normalizedLocator = normalizeArtworkLocator(locator)?.trim().orEmpty()
        if (normalizedLocator.isBlank()) return ImportedArtworkResult()
        return editorPlatformService.loadArtworkBytes(normalizedLocator).fold(
            onSuccess = { bytes ->
                bytes?.takeIf { it.isNotEmpty() }?.let { ImportedArtworkResult(bytes = it) } ?: ImportedArtworkResult()
            },
            onFailure = { throwable ->
                ImportedArtworkResult(message = "已写入编辑器，封面导入失败：${throwable.message ?: "读取失败。"}")
            },
        )
    }

    private suspend fun resetDraft() {
        val snapshot = state.value.selectedSnapshot ?: return
        updateState {
            it.copy(
                draft = snapshot.toDraft(),
                isDirty = false,
                message = null,
            )
        }
    }

    private suspend fun refreshSelectedTrack() {
        val current = state.value
        val track = current.selectedTrack ?: return
        if (current.isDirty) {
            updateState { it.copy(message = "请先保存或重置当前修改后再刷新。") }
            return
        }
        updateState { it.copy(isRefreshing = true, message = null) }
        repository.refreshTags(track)
            .onSuccess { result ->
                applyRefreshResult(track.id, result)
            }
            .onFailure { throwable ->
                updateState {
                    it.copy(
                        isRefreshing = false,
                        message = throwable.message ?: "刷新标签失败。",
                    )
                }
            }
    }

    private suspend fun saveSelectedTrack() {
        val current = state.value
        val track = current.selectedTrack ?: return
        if (!current.canWriteSelected) {
            updateState { it.copy(message = "当前平台暂不支持本地标签写回。") }
            return
        }
        updateState { it.copy(isSaving = true, message = null) }
        repository.saveTags(track, current.draft.toPatch())
            .onSuccess { result ->
                applySaveResult(track.id, result)
            }
            .onFailure { throwable ->
                updateState {
                    it.copy(
                        isSaving = false,
                        message = throwable.message ?: "标签保存失败。",
                    )
                }
            }
    }

    private fun applySaveResult(trackId: String, result: MusicTagSaveResult) {
        updateState { current ->
            val updatedTracks = current.tracks.map { existing ->
                if (existing.id == result.track.id) result.track else existing
            }
            current.copy(
                tracks = updatedTracks,
                selectedTrackId = trackId,
                selectedSnapshot = result.snapshot,
                draft = result.snapshot.toDraft(),
                isDirty = false,
                isSaving = false,
                isRefreshing = false,
                canEditSelected = true,
                canWriteSelected = current.canWriteSelected,
                rowMetadata = current.rowMetadata + (trackId to result.snapshot.toRowMetadata()),
                message = "标签已保存。",
            )
        }
    }

    private fun applyRefreshResult(trackId: String, result: MusicTagSaveResult) {
        updateState { current ->
            val updatedTracks = current.tracks.map { existing ->
                if (existing.id == result.track.id) result.track else existing
            }
            current.copy(
                tracks = updatedTracks,
                selectedTrackId = trackId,
                selectedSnapshot = result.snapshot,
                draft = result.snapshot.toDraft(),
                isDirty = false,
                isRefreshing = false,
                canEditSelected = true,
                canWriteSelected = current.canWriteSelected,
                rowMetadata = current.rowMetadata + (trackId to result.snapshot.toRowMetadata()),
                message = "标签已刷新。",
            )
        }
    }

    private fun updateDraft(transform: MusicTagsDraft.() -> MusicTagsDraft) {
        updateState { current ->
            val nextDraft = current.draft.transform()
            current.copy(
                draft = nextDraft,
                isDirty = current.selectedSnapshot?.let { nextDraft.isDirtyComparedTo(it) } ?: false,
            )
        }
    }

    private fun updateOnlineLyricsSearch(
        transform: MusicTagsLyricsSearchState.() -> MusicTagsLyricsSearchState,
    ) {
        updateState { current ->
            current.copy(
                onlineLyricsSearch = current.onlineLyricsSearch
                    .transform()
                    .copy(
                        hasResult = false,
                        directResults = emptyList(),
                        workflowResults = emptyList(),
                        error = null,
                    ),
            )
        }
    }

    private fun invalidateOnlineLyricsSearch() {
        onlineLyricsSearchVersion += 1L
    }
}

private data class ImportedArtworkResult(
    val bytes: ByteArray? = null,
    val message: String? = null,
)

private fun AudioTagSnapshot.toDraft(): MusicTagsDraft {
    return MusicTagsDraft(
        title = title,
        artistName = artistName.orEmpty(),
        albumTitle = albumTitle.orEmpty(),
        year = year?.toString().orEmpty(),
        trackNumber = trackNumber?.toString().orEmpty(),
        genre = genre.orEmpty(),
        comment = comment.orEmpty(),
        albumArtist = albumArtist.orEmpty(),
        composer = composer.orEmpty(),
        discNumber = discNumber?.toString().orEmpty(),
        embeddedLyrics = embeddedLyrics.orEmpty(),
        isCompilation = isCompilation,
        artworkLocator = artworkLocator,
        pendingArtworkBytes = null,
        clearArtwork = false,
    )
}

private fun AudioTagSnapshot.toRowMetadata(): MusicTagsRowMetadata {
    return MusicTagsRowMetadata(
        tagLabel = tagLabel,
        albumArtist = albumArtist,
    )
}

private fun MusicTagsDraft.toPatch(): AudioTagPatch {
    return AudioTagPatch(
        title = title.trim().ifBlank { null },
        artistName = artistName.trim().ifBlank { null },
        albumTitle = albumTitle.trim().ifBlank { null },
        albumArtist = albumArtist.trim().ifBlank { null },
        year = year.trim().toIntOrNull(),
        genre = genre.trim().ifBlank { null },
        comment = comment.trim().ifBlank { null },
        composer = composer.trim().ifBlank { null },
        embeddedLyrics = embeddedLyrics.trimEnd().ifBlank { null },
        isCompilation = isCompilation,
        trackNumber = trackNumber.trim().toIntOrNull(),
        discNumber = discNumber.trim().toIntOrNull(),
        artworkBytes = pendingArtworkBytes,
        clearArtwork = clearArtwork,
    )
}

private fun MusicTagsDraft.isDirtyComparedTo(snapshot: AudioTagSnapshot): Boolean {
    if (pendingArtworkBytes != null) return true
    if (clearArtwork && snapshot.artworkLocator != null) return true
    if (!clearArtwork && artworkLocator != snapshot.artworkLocator) return true
    return title != snapshot.title ||
        artistName != snapshot.artistName.orEmpty() ||
        albumTitle != snapshot.albumTitle.orEmpty() ||
        year != snapshot.year?.toString().orEmpty() ||
        trackNumber != snapshot.trackNumber?.toString().orEmpty() ||
        genre != snapshot.genre.orEmpty() ||
        comment != snapshot.comment.orEmpty() ||
        albumArtist != snapshot.albumArtist.orEmpty() ||
        composer != snapshot.composer.orEmpty() ||
        discNumber != snapshot.discNumber?.toString().orEmpty() ||
        embeddedLyrics != snapshot.embeddedLyrics.orEmpty() ||
        isCompilation != snapshot.isCompilation
}
