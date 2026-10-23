@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package top.iwesley.lyn.music.automotive.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.ANDROID_AUTOMOTIVE_PLATFORM_NAME
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerTaskSession
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class AutomotivePlayerCompositionTest {
    @Test
    fun `collapse button cancels pending share in every supported layout`() = onUiThread {
        for ((width, height) in listOf(480 to 900, 1280 to 720)) {
            Fixture(width, height).use { ui ->
                ui.startPendingShare()
                assertFalse(ui.presentation.isLyricsShareVisible)
                ui.click("Collapse player")
                assertFalse(ui.presentation.isExpanded)
                assertTrue(PlayerIntent.EndLyricsShare in ui.intents)
                assertFalse(ui.presentation.isLyricsShareVisible)
            }
        }
    }


    @Test
    fun `portrait and landscape controls dispatch the same shared playback actions`() = onUiThread {
        for ((width, height) in listOf(480 to 900, 1280 to 720)) {
            Fixture(width, height).use { ui ->
                ui.intents.clear()
                ui.click("Next song")
                ui.click("Previous song")
                ui.click("Play queue")
                assertEquals(listOf(PlayerIntent.SkipNext, PlayerIntent.SkipPrevious), ui.intents)
                assertEquals(width > height, ui.hasLabel("Clean mode"))
                ui.capture(if (width > height) "landscape" else "portrait")
                ui.click("Collapse player")
                assertEquals(false, ui.presentation.isExpanded)
            }
        }
    }

    @Test
    fun `landscape clean mode hides playback controls and keeps the lyrics surface`() = onUiThread {
        Fixture(1280, 720).use { ui ->
            ui.click("Clean mode")
            assertFalse(ui.hasLabel("Collapse player"))
            assertFalse(ui.hasLabel("Next song"))
            assertTrue(ui.hasLabel("Test lyric"))
            ui.capture("landscape-clean")
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(width: Int, height: Int) : AutoCloseable {
        val intents = mutableListOf<PlayerIntent>()
        private val platform = PlatformDescriptor(
            ANDROID_AUTOMOTIVE_PLATFORM_NAME,
            PlatformCapabilities(false, false, false, false, false),
        )
        private var state by mutableStateOf(PlayerState(
            snapshot = PlaybackSnapshot(
                queue = listOf(Track("song", "local", "Song", mediaLocator = "", relativePath = "")),
                currentIndex = 0,
                durationMs = 180_000L,
            ),
            lyrics = LyricsDocument(listOf(LyricsLine(0L, "Test lyric")), sourceId = "local", rawPayload = "Test lyric"),
            highlightedLineIndex = 0,
            lyricsTrackId = "song",
            lyricsVersion = "v1",
        ))
        private fun dispatch(intent: PlayerIntent) {
            intents += intent
            presentation.onPlaybackIntent(intent)
            when (intent) {
                PlayerIntent.BeginLyricsShare -> state = state.copy(
                    lyricsShareSession = PlayerTaskSession(1, "song"), isLyricsShareInitializing = true,
                )
                PlayerIntent.EndLyricsShare -> state = state.copy(
                    lyricsShareSession = null, isLyricsShareInitializing = false,
                )
                else -> Unit
            }
        }
        val presentation = PlayerPresentation({ state }, ::dispatch).apply { expand() }
        private var frame = 0L
        private val scene = ImageComposeScene(width, height) {
            ProvideUiResourceLanguage("en") {
                CompositionLocalProvider(LocalPlayerPresentation provides presentation, LocalPlatformDescriptor provides platform) {
                    LynMusicTheme {
                        PlayerDrawerHost(
                            visible = true,
                            platform = platform,
                            logger = NoopDiagnosticLogger,
                            state = state,
                            appDisplayScalePreset = AppDisplayScalePreset.Default,
                            showCompactPlayerLyrics = false,
                            playerArtworkStyle = PlayerArtworkStyle.VINYL,
                            showEqualizerEntry = false,
                            onOpenEqualizer = {},
                            lyricsShareThemeTokens = CLASSIC_APP_THEME_TOKENS,
                            lyricsShareTextPalette = AppThemeTextPalette.White,
                            onPlayerIntent = ::dispatch,
                            isFavorite = false,
                            onToggleFavorite = {},
                            onOpenAddToPlaylist = {},
                            onOpenQueue = { presentation.showQueue(true) },
                            onOpenLibraryNavigationTarget = {},
                        )
                    }
                }
            }
        }

        init { render() }

        fun startPendingShare() {
            presentation.openShare()
            render()
        }

        private fun render() {
            repeat(4) {
                Snapshot.sendApplyNotifications()
                scene.render(++frame * 16_666_667L).close()
            }
        }

        private fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }

        private fun SemanticsNode.matches(label: String): Boolean =
            config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true ||
                config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true

        fun hasLabel(label: String): Boolean = nodes().any { it.matches(label) }

        fun click(label: String) {
            val node = nodes().lastOrNull { it.matches(label) } ?: error("Missing automotive control: $label")
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center)
            render()
        }

        fun capture(name: String) {
            val file = File("build/reports/automotive-ui/$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image ->
                image.encodeToData()!!.use { file.writeBytes(it.bytes) }
            }
        }

        override fun close() = scene.close()
    }
}
