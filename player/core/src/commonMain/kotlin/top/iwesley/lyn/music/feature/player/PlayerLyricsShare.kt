package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.resources.*

/** Owns share events and their updates to the Store's single PlayerState. */
internal class PlayerLyricsShare(
    private val storeScope: CoroutineScope,
    private val lyricsSharePlatformService: LyricsSharePlatformService,
    private val lyricsShareFontLibraryPlatformService: LyricsShareFontLibraryPlatformService,
    private val lyricsShareFontPreferencesStore: LyricsShareFontPreferencesStore,
    private val states: StateFlow<PlayerState>,
    private val updateState: ((PlayerState) -> PlayerState) -> Unit,
    private val newSession: () -> PlayerTaskSession,
    private val closeSearch: () -> Unit,
    private val refreshLyricsFonts: () -> Unit,
    private val nextOperationId: () -> Long,
) {
    private val sessionTasks = MutableStateFlow<LyricsShareTasks?>(null)
    private fun state(): PlayerState = states.value
    private val latestImageSave = MutableStateFlow<SubmittedImageSave?>(null)
    private val fontPreferences = Channel<FontPreferenceWrite>(Channel.UNLIMITED)
    private val exports = LyricsShareExportExecutor(
        storeScope, lyricsSharePlatformService,
        onCopyCompleted = ::completeLyricsCopy,
        onSaveCompleted = ::completeImageSave,
        onSaveFinished = { latestImageSave.compareAndSet(it, null) },
    )

    init {
        storeScope.launch {
            // Metadata, lyrics and resolved fonts can change without a UI edit. Accepted input
            // changes are scheduled here, independently of the cancellable task that produced them.
            states.collect {
                val current = state()
                val session = current.lyricsShareSession
                if (session != null && !current.isLyricsShareInitializing &&
                    current.shareCardModel != current.lyricsShareModel()
                ) requestLyricsSharePreview(session, initial = current, requirePendingChange = true)
            }
        }
        storeScope.launch {
            for (request in fontPreferences) {
                when (request) {
                    is SubmittedFontChoice -> persistFontChoice(request)
                    is ResolvedFontPreference -> {
                        val result = runCatching {
                            // A queued automatic correction retains the caller's cancellable session.
                            withContext(request.owner) {
                                val current = state()
                                if (current.lyricsShareSession != request.session ||
                                    current.lyricsShareFontChoiceRevision != request.revision ||
                                    !request.ownsRequest(current)
                                ) false else {
                                    writeFontPreference(request.fontKey)
                                    true
                                }
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        request.result.complete(result)
                    }
                }
            }
        }
    }

    /** UI edits and export capture finish before dispatch returns; platform work remains async. */
    fun dispatch(intent: PlayerIntent): Boolean {
        when (intent) {
            PlayerIntent.BeginLyricsShare -> beginLyricsShare()
            PlayerIntent.EndLyricsShare -> close()
            is PlayerIntent.LyricsShareTemplateChanged, is PlayerIntent.ToggleLyricsLineSelection,
            PlayerIntent.ClearLyricsSelection, is PlayerIntent.LyricsShareFontChanged -> editLyricsShareContent(intent)
            PlayerIntent.SaveLyricsShareImage -> submitImageSave()
            PlayerIntent.CopyLyricsShareImage -> submitLyricsCopy(copyImage = true)
            PlayerIntent.CopyLyricsShareText -> submitLyricsCopy(copyImage = false)
            PlayerIntent.RequestLyricsShareFonts -> requestLyricsShareFonts()
            PlayerIntent.BuildLyricsSharePreview -> state().lyricsShareSession?.let {
                requestLyricsSharePreview(it, force = true)
            }
            PlayerIntent.ClearLyricsShareMessage -> {
                val session = state().lyricsShareSession ?: return true
                if (!state().isLyricsShareInitializing) updateState {
                    if (it.lyricsShareSession == session) it.copy(shareMessage = null, sharePreviewError = null) else it
                }
            }
            PlayerIntent.InvalidateLyricsShareFontCache -> {
                invalidateLyricsShareFontCache()
                refreshLyricsFonts()
            }
            else -> return false
        }
        return true
    }

    fun close() {
        sessionTasks.getAndUpdate { null }?.cancel()
        updateState { it.clearLyricsShareState() }
    }

    private fun beginLyricsShare() {
        close()
        if (state().effectiveSnapshot.currentTrack == null || state().lyrics == null) return
        closeSearch()
        val session = newSession()
        val tasks = LyricsShareTasks(session, storeScope)
        sessionTasks.value = tasks
        updateState { it.copy(lyricsShareSession = session, isLyricsShareInitializing = true) }
        startInitialization(tasks)
    }

    private fun startInitialization(tasks: LyricsShareTasks) {
        val requestId = nextOperationId()
        updateState {
            if (it.lyricsShareSession == tasks.session && it.isLyricsShareInitializing)
                it.copy(lyricsShareInitializationRequestId = requestId) else it
        }
        if (state().lyricsShareInitializationRequestId != requestId) return
        tasks.initialization.launch(requestId) {
            try {
                openLyricsShare(tasks.session, requestId)
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                updateState {
                    if (it.ownsInitialization(tasks.session, requestId))
                        it.clearLyricsShareState().copy(message = error.uiErrorText()) else it
                }
            } finally {
                updateState {
                    if (it.ownsInitialization(tasks.session, requestId)) it.clearLyricsShareState() else it
                }
                if (state().lyricsShareSession != tasks.session && sessionTasks.compareAndSet(tasks, null)) tasks.cancel()
            }
        }
    }

    private suspend fun openLyricsShare(session: PlayerTaskSession, requestId: Long) {
        val currentState = state()
        if (!currentState.ownsInitialization(session, requestId) || currentState.lyrics == null) return
        val supportsFontSelection = supportsLyricsShareFontSelection()
        val savedFontKey = if (supportsFontSelection) {
            lyricsShareFontPreferencesStore.selectedLyricsShareFontKey.value
        } else {
            null
        }
        var selectedFontKey = currentState.selectedLyricsShareFontKey
            ?: savedFontKey
            ?: DEFAULT_LYRICS_SHARE_FONT_KEY.takeIf { supportsFontSelection }
        var selectedFontDisplayName = currentState.selectedLyricsShareFontDisplayName
            .takeIf { currentState.selectedLyricsShareFontKey == selectedFontKey }
        var clearedMissingImportedFont = false
        var resolvedImportedFontOnOpen = false
        val importedFont = resolveOpeningImportedLyricsShareFont(selectedFontKey)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (!state().ownsInitialization(session, requestId)) return
        when (importedFont) {
            ImportedLyricsShareFontOpenResult.Missing -> {
                if (!persistResolvedFont(null, session, currentState.lyricsShareFontChoiceRevision, initializationId = requestId)) return
                selectedFontKey = DEFAULT_LYRICS_SHARE_FONT_KEY.takeIf { supportsFontSelection }
                selectedFontDisplayName = null
                clearedMissingImportedFont = true
            }

            is ImportedLyricsShareFontOpenResult.Resolved -> {
                selectedFontDisplayName = importedFont.displayName
                resolvedImportedFontOnOpen = true
            }

            null -> Unit
        }
        if (supportsFontSelection && currentState.availableLyricsShareFonts.isNotEmpty()) {
            val availableSelectedFont = currentState.availableLyricsShareFonts.firstOrNull { option ->
                selectedFontKey?.let { option.fontKey.equals(it, ignoreCase = true) } == true
            }
            if (availableSelectedFont != null) {
                selectedFontDisplayName = availableSelectedFont.displayName
            } else if (!resolvedImportedFontOnOpen) {
                val resolvedFontKey = resolveLyricsShareFontSelection(
                    availableFonts = currentState.availableLyricsShareFonts,
                    preferredFontKey = selectedFontKey,
                )
                if (resolvedFontKey != null && resolvedFontKey != savedFontKey && !clearedMissingImportedFont) {
                    if (!persistResolvedFont(resolvedFontKey, session, currentState.lyricsShareFontChoiceRevision, initializationId = requestId)) return
                }
                selectedFontKey = resolvedFontKey
                selectedFontDisplayName = currentState.availableLyricsShareFonts
                    .firstOrNull { option ->
                        resolvedFontKey?.let { option.fontKey.equals(it, ignoreCase = true) } == true
                    }
                    ?.displayName
            }
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (!state().ownsInitialization(session, requestId)) return
        var initializedState: PlayerState? = null
        updateState {
            // A failed CAS can retry after this initialization lost ownership. Discard the
            // earlier candidate so only the ready state actually published can trigger a preview.
            initializedState = null
            if (!it.ownsInitialization(session, requestId)) return@updateState it
            val latestLyrics = it.lyrics ?: return@updateState it
            val defaultSelection = resolveDefaultLyricsShareSelection(latestLyrics, it.highlightedLineIndex)
                ?.let(::setOf).orEmpty()
            it.copy(
                lyricsShareSession = session,
                isLyricsShareInitializing = false,
                lyricsShareInitializationRequestId = null,
                selectedLyricsShareFontKey = selectedFontKey,
                selectedLyricsShareFontDisplayName = selectedFontDisplayName,
                selectedLyricsLineIndices = defaultSelection,
                shareCardModel = deriveLyricsShareCardModel(
                    snapshot = it.effectiveSnapshot,
                    lyrics = latestLyrics,
                    selectedLineIndices = defaultSelection,
                    template = it.selectedLyricsShareTemplate,
                    fontKey = selectedFontKey,
                ),
                sharePreviewBytes = null,
                sharePreviewModel = null,
                sharePreviewSelection = emptySet(),
                sharePreviewError = null,
                isShareRendering = false,
                lyricsSharePreviewRequestId = null,
                isShareSaving = false,
                lyricsShareSaveRequestId = null,
                isShareCopying = false,
                lyricsShareCopyRequestId = null,
                shareMessage = null,
            ).also { ready -> initializedState = ready }
        }
        // Closing/reopening can race with the final state update. Its continuation still belongs
        // to this initialization's session, even when the update was rejected during a CAS retry.
        currentCoroutineContext().ensureActive()
        initializedState?.let { requestLyricsSharePreview(session, initial = it) }
    }

    private fun editLyricsShareContent(intent: PlayerIntent) {
        val current = state()
        val session = current.lyricsShareSession ?: return
        if (sessionTasks.value?.session != session) return
        if (current.isLyricsShareInitializing || current.effectiveSnapshot.currentTrack?.id != session.trackId) return
        val fontChange = intent as? PlayerIntent.LyricsShareFontChanged
        if (fontChange != null && (!supportsLyricsShareFontSelection() ||
                current.availableLyricsShareFonts.none { it.fontKey == fontChange.fontKey })) return
        val fontRevision = fontChange?.let { nextOperationId() }
        if (intent is PlayerIntent.LyricsShareTemplateChanged && intent.template == current.selectedLyricsShareTemplate) return
        if (intent is PlayerIntent.ToggleLyricsLineSelection &&
            !isSelectableLyricsShareLine(current.lyrics?.lines?.getOrNull(intent.index)?.text?.trim().orEmpty())
        ) return
        // Keep edits and the following copy/save capture in UI dispatch order. Rendering stays async.
        updateState { latest ->
            if (latest.lyricsShareSession != session || latest.isLyricsShareInitializing ||
                latest.effectiveSnapshot.currentTrack?.id != session.trackId
            ) return@updateState latest
            val font = fontChange?.let { change ->
                latest.availableLyricsShareFonts.firstOrNull { it.fontKey == change.fontKey }
                    ?: return@updateState latest
            }
            // Reselecting the current font can retry a failed preference write without rebuilding.
            if (font != null && font.fontKey == latest.selectedLyricsShareFontKey) {
                return@updateState latest.copy(lyricsShareFontChoiceRevision = requireNotNull(fontRevision), shareMessage = null)
            }
            val selection = when (intent) {
                is PlayerIntent.ToggleLyricsLineSelection -> {
                    if (!isSelectableLyricsShareLine(latest.lyrics?.lines?.getOrNull(intent.index)?.text?.trim().orEmpty())) return@updateState latest
                    latest.selectedLyricsLineIndices.toMutableSet().also { if (!it.add(intent.index)) it.remove(intent.index) }.toSet()
                }
                PlayerIntent.ClearLyricsSelection -> emptySet()
                else -> latest.selectedLyricsLineIndices
            }
            val template = (intent as? PlayerIntent.LyricsShareTemplateChanged)?.template ?: latest.selectedLyricsShareTemplate
            val clear = intent == PlayerIntent.ClearLyricsSelection
            latest.copy(
                selectedLyricsLineIndices = selection,
                selectedLyricsShareTemplate = template,
                selectedLyricsShareFontKey = font?.fontKey ?: latest.selectedLyricsShareFontKey,
                selectedLyricsShareFontDisplayName = font?.displayName ?: latest.selectedLyricsShareFontDisplayName,
                lyricsShareFontChoiceRevision = fontRevision ?: latest.lyricsShareFontChoiceRevision,
                shareCardModel = deriveLyricsShareCardModel(latest.effectiveSnapshot, latest.lyrics,
                    selection, template, font?.fontKey ?: latest.selectedLyricsShareFontKey),
                sharePreviewBytes = if (clear) null else latest.sharePreviewBytes,
                sharePreviewModel = if (clear) null else latest.sharePreviewModel,
                sharePreviewSelection = if (clear) emptySet() else latest.sharePreviewSelection,
                lyricsSharePreviewRequestId = null,
                sharePreviewError = null,
                isShareRendering = false,
                shareMessage = null,
            )
        }
        if (fontChange != null) {
            if (state().lyricsShareFontChoiceRevision != fontRevision) return
            fontPreferences.trySend(SubmittedFontChoice(requireNotNull(fontRevision), session, fontChange.fontKey))
            if (fontChange.fontKey == current.selectedLyricsShareFontKey) return
        }
        requestLyricsSharePreview(session, force = true)
    }

    private suspend fun persistFontChoice(choice: SubmittedFontChoice) {
        try {
            writeFontPreference(choice.fontKey)
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()
            updateState { current ->
                if (current.lyricsShareSession == choice.session &&
                    current.lyricsShareFontChoiceRevision == choice.revision &&
                    current.effectiveSnapshot.currentTrack?.id == choice.session.trackId
                ) current.copy(shareMessage = error.uiErrorText()) else current
            }
        }
        // Success never writes back to the draft: the edit was already accepted synchronously.
    }

    /** Automatic normalization remains cancellable; it shares the writer with committed choices. */
    private suspend fun persistResolvedFont(
        fontKey: String?, session: PlayerTaskSession, revision: Long,
        initializationId: Long? = null, fontsId: Long? = null,
    ): Boolean {
        val result = CompletableDeferred<Result<Boolean>>()
        fontPreferences.trySend(ResolvedFontPreference(
            fontKey, session, revision, initializationId, fontsId, currentCoroutineContext().job, result,
        ))
        return result.await().getOrThrow()
    }

    private suspend fun writeFontPreference(fontKey: String?) {
        currentCoroutineContext().ensureActive()
        if (lyricsShareFontPreferencesStore.selectedLyricsShareFontKey.value != fontKey) {
            lyricsShareFontPreferencesStore.setSelectedLyricsShareFontKey(fontKey)
        }
        currentCoroutineContext().ensureActive()
    }

    private fun requestLyricsSharePreview(
        session: PlayerTaskSession, force: Boolean = false,
        initial: PlayerState = state(), requirePendingChange: Boolean = false,
    ) {
        // Automatic callers carry the state that triggered their work. Atomic checks below keep
        // initialization and observations from treating a newer failed preview as a new request.
        val tasks = sessionTasks.value ?: return
        if (tasks.session != session || !initial.isReadyShare(session)) return
        val model = initial.lyricsShareModel()
        val selection = initial.selectedLyricsLineIndices.toSet()
        if (!force && model != null && initial.shareCardModel == model &&
            (initial.lyricsSharePreviewRequestId != null || initial.hasFreshSharePreview)) return
        val requestId = nextOperationId()
        fun matches(current: PlayerState) = current.isReadyShare(session) &&
            current.selectedLyricsLineIndices == selection && current.lyricsShareModel() == model
        updateState { current ->
            // Content and the nullable active ID can both return to their original values after
            // newer previews finish. The retained revision must still match the captured state.
            if (!matches(current) || current.lyricsSharePreviewRevision != initial.lyricsSharePreviewRevision ||
                current.lyricsSharePreviewRequestId != initial.lyricsSharePreviewRequestId
            ) return@updateState current
            // An edit can accept this model before submitting its own preview. Recheck the
            // observer's pending-input condition in the same atomic update as the request version.
            if (requirePendingChange && current.shareCardModel == model) return@updateState current
            if (model == null) current.copy(
                shareCardModel = null, sharePreviewBytes = null, sharePreviewModel = null,
                sharePreviewSelection = emptySet(),
                sharePreviewError = null, isShareRendering = false, lyricsSharePreviewRequestId = null,
                lyricsSharePreviewRevision = requestId,
            ) else current.copy(
                shareCardModel = model, sharePreviewError = null,
                isShareRendering = true, lyricsSharePreviewRequestId = requestId,
                lyricsSharePreviewRevision = requestId,
            )
        }
        val accepted = state()
        if (!accepted.isReadyShare(session) || accepted.lyricsSharePreviewRevision != requestId) return
        if (model == null) {
            tasks.preview.cancel(requestId)
        } else if (accepted.lyricsSharePreviewRequestId == requestId) {
            tasks.preview.launch(requestId) { rebuildLyricsSharePreview(session, requestId, selection, model) }
        }
    }

    private suspend fun rebuildLyricsSharePreview(
        session: PlayerTaskSession, requestId: Long, selection: Set<Int>, model: LyricsShareCardModel,
    ) {
        fun ownsRequest(current: PlayerState) = current.isReadyShare(session) &&
            current.lyricsSharePreviewRequestId == requestId
        fun matchesContent(current: PlayerState) = ownsRequest(current) &&
            current.selectedLyricsLineIndices == selection && current.lyricsShareModel() == model
        try {
            if (!ownsRequest(state())) return
            val bytes = lyricsSharePlatformService.buildPreview(model).getOrThrow()
            currentCoroutineContext().ensureActive()
            updateState { current ->
                if (!matchesContent(current)) return@updateState current
                current.copy(
                    sharePreviewBytes = bytes, sharePreviewModel = model, sharePreviewSelection = selection,
                    sharePreviewError = null, isShareRendering = false, lyricsSharePreviewRequestId = null,
                )
            }
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()
            updateState { current ->
                if (!matchesContent(current)) return@updateState current
                current.copy(
                    sharePreviewError = uiText(Res.string.lyrics_share_image_generation_failed, error.uiErrorDetail()),
                    isShareRendering = false, lyricsSharePreviewRequestId = null,
                )
            }
        } finally {
            // A discarded result must end its own busy state, without clearing a replacement request.
            updateState { current ->
                if (ownsRequest(current)) current.copy(isShareRendering = false, lyricsSharePreviewRequestId = null) else current
            }
        }
    }

    private fun submitImageSave() {
        val current = state()
        val session = current.lyricsShareSession ?: return
        val track = current.effectiveSnapshot.currentTrack ?: return
        if (current.isLyricsShareInitializing || session.trackId != track.id) return
        if (current.selectedLyricsLineIndices.isEmpty()) {
            updateState { if (it.lyricsShareSession == session) it.copy(shareMessage = uiText(Res.string.lyrics_share_line_selection_required)) else it }
            return
        }
        val model = deriveLyricsShareCardModel(current.effectiveSnapshot, current.lyrics,
            current.selectedLyricsLineIndices.toSet(), current.selectedLyricsShareTemplate, current.selectedLyricsShareFontKey) ?: return
        val previous = latestImageSave.value
        if (previous != null && previous.id == current.lyricsShareSaveRequestId &&
            previous.session == session && previous.model == model
        ) return
        val bytes = current.sharePreviewBytes?.takeIf { current.hasFreshSharePreview && current.shareCardModel == model }?.copyOf()
        val request = SubmittedImageSave(nextOperationId(), session, track, model, bytes, buildLyricsShareSuggestedName(track.title))
        latestImageSave.value = request
        updateState {
            if (it.lyricsShareSession == session && it.effectiveSnapshot.currentTrack?.id == track.id) it.copy(
                isShareSaving = true, lyricsShareSaveRequestId = request.id, shareMessage = null,
            ) else it
        }
        exports.submitSave(request)
    }

    private fun completeImageSave(request: SubmittedImageSave, message: UiText) {
        updateState { current ->
            if (current.lyricsShareSession == request.session && current.lyricsShareSaveRequestId == request.id &&
                current.effectiveSnapshot.currentTrack?.id == request.track.id
            ) {
                current.copy(isShareSaving = false, lyricsShareSaveRequestId = null, shareMessage = message)
            } else current.withOperationNotice(request.id, request.track, message)
        }
    }

    private fun submitLyricsCopy(copyImage: Boolean) {
        val current = state()
        val session = current.lyricsShareSession ?: return
        val track = current.effectiveSnapshot.currentTrack ?: return
        if (current.isLyricsShareInitializing || session.trackId != track.id) return
        val text = buildSelectedLyricsShareText(current.lyrics, current.selectedLyricsLineIndices) ?: run {
            updateState {
                if (it.lyricsShareSession == session) it.copy(shareMessage = uiText(Res.string.lyrics_share_line_selection_required)) else it
            }
            return
        }
        val content = if (copyImage) {
            val model = deriveLyricsShareCardModel(current.effectiveSnapshot, current.lyrics,
                current.selectedLyricsLineIndices.toSet(), current.selectedLyricsShareTemplate, current.selectedLyricsShareFontKey) ?: return
            val bytes = current.sharePreviewBytes?.takeIf { current.hasFreshSharePreview && current.shareCardModel == model }?.copyOf()
            SubmittedLyricsCopyContent.Image(model, bytes)
        } else SubmittedLyricsCopyContent.Text(text)
        val request = SubmittedLyricsCopy(nextOperationId(), session, track, content)
        // Publish ownership before enqueueing so an older completion must retry against this ID.
        updateState { latest ->
            if (latest.lyricsShareSession == session && latest.effectiveSnapshot.currentTrack?.id == track.id) {
                latest.copy(isShareCopying = true, lyricsShareCopyRequestId = request.id, shareMessage = null)
            } else latest
        }
        exports.submitCopy(request)
    }

    private fun completeLyricsCopy(request: SubmittedLyricsCopy, message: UiText) {
        updateState { current ->
            if (current.lyricsShareSession == request.session && current.lyricsShareCopyRequestId == request.id &&
                current.effectiveSnapshot.currentTrack?.id == request.track.id
            ) {
                current.copy(isShareCopying = false, lyricsShareCopyRequestId = null, shareMessage = message)
            } else current.withOperationNotice(request.id, request.track, message)
        }
    }

    private suspend fun resolveOpeningImportedLyricsShareFont(
        fontKey: String?,
    ): ImportedLyricsShareFontOpenResult? {
        val normalizedFontKey = fontKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        parseLyricsShareImportedFontHash(normalizedFontKey) ?: return null
        val fontPath = lyricsShareFontLibraryPlatformService.resolveImportedFontPath(normalizedFontKey)
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return ImportedLyricsShareFontOpenResult.Missing
        val displayName = lyricsShareFontLibraryPlatformService.listImportedFonts()
            .getOrNull()
            ?.firstOrNull { option -> option.fontKey.equals(normalizedFontKey, ignoreCase = true) }
            ?.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: parseImportedLyricsShareFontDisplayNameFromPath(fontPath)
        return ImportedLyricsShareFontOpenResult.Resolved(displayName)
    }

    private fun requestLyricsShareFonts() {
        if (!supportsLyricsShareFontSelection()) return
        val current = state()
        val tasks = sessionTasks.value ?: return
        if (!current.isReadyShare(tasks.session) || current.availableLyricsShareFonts.isNotEmpty() ||
            current.isLyricsShareFontsLoading) return
        val requestId = nextOperationId()
        updateState {
            if (it.isReadyShare(tasks.session) && !it.isLyricsShareFontsLoading && it.availableLyricsShareFonts.isEmpty())
                it.copy(isLyricsShareFontsLoading = true, lyricsShareFontsRequestId = requestId, lyricsShareFontsError = null) else it
        }
        if (state().lyricsShareFontsRequestId == requestId) {
            tasks.fonts.launch(requestId) { loadLyricsShareFonts(tasks.session, requestId) }
        }
    }

    private suspend fun loadLyricsShareFonts(session: PlayerTaskSession, requestId: Long) {
        fun ownsLoad(current: PlayerState) = current.isReadyShare(session) && current.lyricsShareFontsRequestId == requestId
        try {
            if (!ownsLoad(state())) return
            val fonts = lyricsSharePlatformService.listAvailableFontFamilies().getOrThrow()
            currentCoroutineContext().ensureActive()
            val latest = state()
            if (!ownsLoad(latest)) return
            val normalizedFonts = fonts.mapNotNull { option ->
                option.fontKey.trim().takeIf { it.isNotEmpty() }?.let { key ->
                    option.copy(
                        fontKey = key, displayName = option.displayName.trim().ifBlank { key },
                        previewText = option.previewText.ifBlank { option.displayName.ifBlank { key } },
                        fontFilePath = option.fontFilePath?.trim()?.takeIf { it.isNotEmpty() },
                    )
                }
            }.distinctBy { it.fontKey.lowercase() }
            val revision = latest.lyricsShareFontChoiceRevision
            val resolvedKey = resolveLyricsShareFontSelection(normalizedFonts, latest.selectedLyricsShareFontKey
                ?: lyricsShareFontPreferencesStore.selectedLyricsShareFontKey.value ?: DEFAULT_LYRICS_SHARE_FONT_KEY)
                ?: error("No available lyrics share fonts")
            persistResolvedFont(resolvedKey, session, revision, fontsId = requestId)
            currentCoroutineContext().ensureActive()
            updateState { current ->
                if (!ownsLoad(current)) return@updateState current
                val loaded = current.copy(availableLyricsShareFonts = normalizedFonts,
                    isLyricsShareFontsLoading = false, lyricsShareFontsRequestId = null, lyricsShareFontsError = null)
                // An explicit edit during persistence keeps its newer choice.
                if (current.lyricsShareFontChoiceRevision != revision) return@updateState loaded
                val resolved = loaded.copy(
                    selectedLyricsShareFontKey = resolvedKey,
                    selectedLyricsShareFontDisplayName = normalizedFonts.first { it.fontKey == resolvedKey }.displayName,
                )
                val model = resolved.lyricsShareModel()
                // Metadata/lyrics may have changed before the state observer can schedule a preview.
                val changed = current.shareCardModel != model
                resolved.copy(
                    // Keep the previous card model until the observer accepts a preview request.
                    // Publishing the new model here would hide pending work if this font task is cancelled.
                    lyricsSharePreviewRequestId = if (changed) null else current.lyricsSharePreviewRequestId,
                    isShareRendering = if (changed) false else current.isShareRendering,
                )
            }
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()
            updateState {
                if (ownsLoad(it)) it.copy(lyricsShareFontsError = uiText(Res.string.font_system_list_read_failed)) else it
            }
        } finally {
            updateState {
                if (ownsLoad(it)) it.copy(isLyricsShareFontsLoading = false, lyricsShareFontsRequestId = null) else it
            }
        }
    }

    private fun invalidateLyricsShareFontCache() {
        val requestId = nextOperationId()
        val tasks = sessionTasks.value
        updateState {
            it.copy(availableLyricsShareFonts = emptyList(), isLyricsShareFontsLoading = false,
                lyricsShareFontsRequestId = null, lyricsShareFontsError = null)
        }
        tasks?.fonts?.cancel(requestId)
        if (tasks != null && state().isLyricsShareInitializing) startInitialization(tasks)
    }

    private fun supportsLyricsShareFontSelection(): Boolean {
        return lyricsShareFontPreferencesStore !== UnsupportedLyricsShareFontPreferencesStore
    }

    private fun resolveDefaultLyricsShareSelection(
        lyrics: LyricsDocument,
        highlightedLineIndex: Int,
    ): Int? {
        if (highlightedLineIndex !in lyrics.lines.indices) return null
        if (isSelectableLyricsShareLine(lyrics.lines[highlightedLineIndex].text)) {
            return highlightedLineIndex
        }
        for (index in highlightedLineIndex + 1 until lyrics.lines.size) {
            if (isSelectableLyricsShareLine(lyrics.lines[index].text)) {
                return index
            }
        }
        for (index in highlightedLineIndex - 1 downTo 0) {
            if (isSelectableLyricsShareLine(lyrics.lines[index].text)) {
                return index
            }
        }
        return null
    }
}

internal fun buildSelectedLyricsShareText(
    lyrics: LyricsDocument?,
    selectedLineIndices: Set<Int>,
): String? {
    val selectedLines = lyrics?.lines.orEmpty()
        .mapIndexedNotNull { index, line ->
            if (index !in selectedLineIndices) return@mapIndexedNotNull null
            line.text.trim().takeIf { text ->
                text.isNotEmpty() && !isPlayerLyricsStructureTagLine(text)
            }
        }
    return selectedLines.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n")
}

internal fun PlayerState.clearLyricsShareState(): PlayerState {
    return copy(
        lyricsShareSession = null,
        isLyricsShareInitializing = false,
        lyricsShareInitializationRequestId = null,
        isLyricsShareFontsLoading = false,
        lyricsShareFontsRequestId = null,
        lyricsShareFontsError = null,
        selectedLyricsShareFontDisplayName = null,
        selectedLyricsLineIndices = emptySet(),
        shareCardModel = null,
        sharePreviewBytes = null,
        sharePreviewModel = null,
        sharePreviewSelection = emptySet(),
        sharePreviewError = null,
        isShareRendering = false,
        lyricsSharePreviewRequestId = null,
        isShareSaving = false,
        lyricsShareSaveRequestId = null,
        isShareCopying = false,
        lyricsShareCopyRequestId = null,
        shareMessage = null,
    )
}

private sealed interface ImportedLyricsShareFontOpenResult {
    data object Missing : ImportedLyricsShareFontOpenResult
    data class Resolved(val displayName: String?) : ImportedLyricsShareFontOpenResult
}

private fun parseImportedLyricsShareFontDisplayNameFromPath(path: String): String? {
    val fileName = path
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
    val separatorIndex = fileName.indexOf("__")
    if (separatorIndex <= 0) return null
    val extensionStart = fileName.lastIndexOf('.').takeIf { it > separatorIndex + 2 } ?: fileName.length
    return fileName
        .substring(separatorIndex + 2, extensionStart)
        .trim()
        .takeIf { it.isNotEmpty() }
}

private fun resolveLyricsShareFontSelection(
    availableFonts: List<LyricsShareFontOption>,
    preferredFontKey: String?,
): String? {
    if (availableFonts.isEmpty()) return null
    fun resolveCandidate(value: String?): String? {
        val normalizedValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return availableFonts.firstOrNull { it.fontKey.equals(normalizedValue, ignoreCase = true) }?.fontKey
    }
    return resolveCandidate(preferredFontKey)
        ?: resolveCandidate(DEFAULT_LYRICS_SHARE_FONT_KEY)
        ?: availableFonts.first().fontKey
}

private sealed interface FontPreferenceWrite
private data class SubmittedFontChoice(val revision: Long, val session: PlayerTaskSession, val fontKey: String) : FontPreferenceWrite
private data class ResolvedFontPreference(
    val fontKey: String?,
    val session: PlayerTaskSession,
    val revision: Long,
    val initializationId: Long?,
    val fontsId: Long?,
    val owner: Job,
    val result: CompletableDeferred<Result<Boolean>>,
) : FontPreferenceWrite {
    fun ownsRequest(state: PlayerState): Boolean =
        (initializationId == null || state.lyricsShareInitializationRequestId == initializationId) &&
            (fontsId == null || state.lyricsShareFontsRequestId == fontsId)
}

private fun PlayerState.isReadyShare(session: PlayerTaskSession): Boolean =
    lyricsShareSession == session && !isLyricsShareInitializing && effectiveSnapshot.currentTrack?.id == session.trackId

private fun PlayerState.ownsInitialization(session: PlayerTaskSession, requestId: Long): Boolean =
    lyricsShareSession == session && isLyricsShareInitializing && lyricsShareInitializationRequestId == requestId &&
        effectiveSnapshot.currentTrack?.id == session.trackId
