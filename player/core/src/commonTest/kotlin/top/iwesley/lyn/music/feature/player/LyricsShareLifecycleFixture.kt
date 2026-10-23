package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.*

/** Controllable platform boundaries; tests observe published state and actual export payloads. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LyricsShareLifecycleFixture(
    private val test: TestScope,
    fontLibrary: LyricsShareFontLibraryPlatformService = UnsupportedLyricsShareFontLibraryPlatformService,
) {
    private val scope = CoroutineScope(StandardTestDispatcher(test.testScheduler) + SupervisorJob())
    val original = Track("original", "source", "Original", mediaLocator = "", relativePath = "")
    val playback = CommitPlaybackRepository(PlaybackSnapshot(queue = listOf(original), currentIndex = 0))
    val lyrics = LifecycleLyricsRepository()
    val platform = LifecycleSharePlatform()
    val preferences = LifecycleSharePreferences()
    val store = PlayerStore(playback, lyrics, scope, lyricsSharePlatformService = platform,
        lyricsShareFontPreferencesStore = preferences, lyricsShareFontLibraryPlatformService = fontLibrary)

    init { drain() }
    fun dispatch(intent: PlayerIntent) = store.dispatch(intent)
    fun drain() = test.testScheduler.advanceUntilIdle()
    fun open() {
        dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        assertNotNull(store.state.value.lyricsShareSession)
        assertFalse(store.state.value.isLyricsShareInitializing)
    }
    fun closeShare() { dispatch(PlayerIntent.EndLyricsShare); drain() }
    fun switchTrack(track: Track) {
        playback.snapshot.value = PlaybackSnapshot(queue = listOf(track), currentIndex = 0)
        drain()
    }
    fun loadFonts() { dispatch(PlayerIntent.RequestLyricsShareFonts); drain() }
    fun shutdown() { scope.cancel(); drain() }
    fun dispose() {
        scope.cancel()
        // Non-cooperative fake calls are always released, including when an assertion fails.
        platform.releasePending()
        preferences.calls.forEach { it.fail(CancellationException("Fixture disposed")) }
        drain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun withLyricsShareLifecycle(test: LyricsShareLifecycleFixture.() -> Unit) = runTest {
    val fixture = LyricsShareLifecycleFixture(this)
    try { fixture.test() } finally { fixture.dispose() }
}

internal class SharePlatformCall<I, O>(val input: I) {
    private val result = CompletableDeferred<Result<O>>()
    var cancelled = false
        private set
    fun succeed(value: O) { result.complete(Result.success(value)) }
    fun fail(error: Throwable = IllegalStateException("Controlled failure")) { result.complete(Result.failure(error)) }
    suspend fun await(ignoreCancellation: Boolean = false): Result<O> = try {
        if (ignoreCancellation) withContext(NonCancellable) { result.await() } else result.await()
    } catch (error: CancellationException) {
        cancelled = true
        throw error
    }
}

internal data class ShareImageSaveInput(val bytes: ByteArray, val suggestedName: String)

internal class LifecycleSharePlatform : LyricsSharePlatformService {
    var renderAutomatically = true
    var fontsAutomatically = true
    var renderException: Throwable? = null
    var fontsException: Throwable? = null
    var ignoreRenderCancellation = false
    var ignoreFontCancellation = false
    val renders = mutableListOf<SharePlatformCall<LyricsShareCardModel, ByteArray>>()
    val fonts = mutableListOf<SharePlatformCall<Unit, List<LyricsShareFontOption>>>()
    val copies = mutableListOf<SharePlatformCall<String, Unit>>()
    val saves = mutableListOf<SharePlatformCall<ShareImageSaveInput, LyricsShareSaveResult>>()
    val copied = mutableListOf<String>()
    val saved = mutableListOf<ShareImageSaveInput>()

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> {
        renderException?.let { throw it }
        val call = SharePlatformCall<LyricsShareCardModel, ByteArray>(model).also(renders::add)
        if (renderAutomatically) call.succeed(model.lifecycleImage())
        return call.await(ignoreRenderCancellation)
    }
    override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> {
        fontsException?.let { throw it }
        val call = SharePlatformCall<Unit, List<LyricsShareFontOption>>(Unit).also(fonts::add)
        if (fontsAutomatically) call.succeed(listOf(
            LyricsShareFontOption("Serif", "Serif"), LyricsShareFontOption("Another", "Another"),
        ))
        return call.await(ignoreFontCancellation)
    }
    override suspend fun copyImage(pngBytes: ByteArray) = copy("image:" + pngBytes.decodeToString())
    override suspend fun copyText(text: String) = copy("text:" + text)
    private suspend fun copy(value: String): Result<Unit> {
        val call = SharePlatformCall<String, Unit>(value).also(copies::add)
        return call.await().onSuccess { copied += value }
    }
    override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> {
        val input = ShareImageSaveInput(pngBytes.copyOf(), suggestedName)
        val call = SharePlatformCall<ShareImageSaveInput, LyricsShareSaveResult>(input).also(saves::add)
        return call.await().onSuccess { saved += input }
    }
    fun releasePending() {
        renders.forEach { it.fail(CancellationException("Fixture disposed")) }
        fonts.forEach { it.fail(CancellationException("Fixture disposed")) }
        copies.forEach { it.fail(CancellationException("Fixture disposed")) }
        saves.forEach { it.fail(CancellationException("Fixture disposed")) }
    }
}

internal class LifecycleSharePreferences : LyricsShareFontPreferencesStore {
    override val selectedLyricsShareFontKey = MutableStateFlow<String?>(null)
    var writeAutomatically = true
    val calls = mutableListOf<SharePlatformCall<String?, Unit>>()
    override suspend fun setSelectedLyricsShareFontKey(value: String?) {
        val call = SharePlatformCall<String?, Unit>(value).also(calls::add)
        if (writeAutomatically) call.succeed(Unit)
        call.await().getOrThrow()
        selectedLyricsShareFontKey.value = value
    }
}

/** Include the entire render input so stale artwork, metadata or lyrics cannot pass as fresh bytes. */
internal fun LyricsShareCardModel.lifecycleImage(): ByteArray = toString().encodeToByteArray()
internal fun savedShareImage() = LyricsShareSaveResult(UiText.Raw("Saved"))

internal class LifecycleLyricsRepository : LyricsRepository by CopyTestLyricsRepository() {
    override val changes = MutableSharedFlow<LyricsChange>(extraBufferCapacity = 1)
    var replacement: LyricsDocument? = null
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult = replacement?.let(::ResolvedLyricsResult)
        ?: CopyTestLyricsRepository().getLyrics(track)
    override suspend fun loadPlaybackLyrics(track: Track) = LyricsLoadResult.Found(getLyrics(track))
    fun replace(trackId: String, document: LyricsDocument) {
        replacement = document
        check(changes.tryEmit(LyricsChange(trackId, contentChanged = true)))
    }
}
