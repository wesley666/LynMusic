package top.iwesley.lyn.music.feature.tags

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.AudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.MusicTagSaveResult
import top.iwesley.lyn.music.data.repository.MusicTagsRepository

data class MusicTagsRowMetadata(
    val tagLabel: String? = null,
    val albumArtist: String? = null,
)

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
    val pendingSelectionTrackId: String? = null,
    val showDiscardChangesDialog: Boolean = false,
    val message: String? = null,
) {
    val selectedTrack: Track?
        get() = tracks.firstOrNull { it.id == selectedTrackId }
}

sealed interface MusicTagsIntent {
    data class SelectTrack(val trackId: String) : MusicTagsIntent
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
    data object PickArtwork : MusicTagsIntent
    data object ClearArtwork : MusicTagsIntent
    data object ResetDraft : MusicTagsIntent
    data object RefreshSelected : MusicTagsIntent
    data object Save : MusicTagsIntent
    data object ClearMessage : MusicTagsIntent
}

sealed interface MusicTagsEffect

class MusicTagsStore(
    private val repository: MusicTagsRepository,
    private val editorPlatformService: AudioTagEditorPlatformService,
    private val storeScope: CoroutineScope,
) : BaseStore<MusicTagsState, MusicTagsIntent, MusicTagsEffect>(
    initialState = MusicTagsState(),
    scope = storeScope,
) {
    private var selectedLoadVersion = 0L

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
                val pendingTrackId = state.value.pendingSelectionTrackId
                if (pendingTrackId != null) {
                    discardAndSelectTrack(pendingTrackId)
                } else {
                    updateState {
                        it.copy(
                            pendingSelectionTrackId = null,
                            showDiscardChangesDialog = false,
                        )
                    }
                }
            }

            MusicTagsIntent.DismissDiscardSelection -> updateState {
                it.copy(
                    pendingSelectionTrackId = null,
                    showDiscardChangesDialog = false,
                )
            }

            MusicTagsIntent.ResetDraft -> resetDraft()
            MusicTagsIntent.RefreshSelected -> refreshSelectedTrack()
            MusicTagsIntent.Save -> saveSelectedTrack()
            MusicTagsIntent.PickArtwork -> pickArtwork()
            MusicTagsIntent.ClearArtwork -> updateDraft {
                copy(
                    pendingArtworkBytes = null,
                    clearArtwork = artworkLocator != null || pendingArtworkBytes != null,
                )
            }

            is MusicTagsIntent.SelectTrack -> selectTrack(intent.trackId)
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
        }
    }

    private suspend fun selectTrack(trackId: String) {
        if (trackId == state.value.selectedTrackId) return
        if (state.value.isDirty) {
            updateState {
                it.copy(
                    pendingSelectionTrackId = trackId,
                    showDiscardChangesDialog = true,
                )
            }
            return
        }
        updateState {
            it.copy(
                selectedTrackId = trackId,
                pendingSelectionTrackId = null,
                showDiscardChangesDialog = false,
            )
        }
        loadSelectedTrack(trackId)
    }

    private suspend fun discardAndSelectTrack(trackId: String) {
        if (trackId == state.value.selectedTrackId) {
            updateState {
                it.copy(
                    pendingSelectionTrackId = null,
                    showDiscardChangesDialog = false,
                    isDirty = false,
                )
            }
            return
        }
        updateState {
            it.copy(
                selectedTrackId = trackId,
                pendingSelectionTrackId = null,
                showDiscardChangesDialog = false,
                isDirty = false,
            )
        }
        loadSelectedTrack(trackId)
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
                message = null,
            )
        }
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
}

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
