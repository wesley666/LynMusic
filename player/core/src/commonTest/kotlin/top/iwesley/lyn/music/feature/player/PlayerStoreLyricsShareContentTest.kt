package top.iwesley.lyn.music.feature.player

import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

class PlayerStoreLyricsShareContentTest {
    @Test fun sameTrackTitleChangeRebuildsPreview() = changedSnapshot { it.copy(metadataTitle = "Updated title") }
    @Test fun sameTrackArtistChangeRebuildsPreview() = changedSnapshot { it.copy(metadataArtistName = "Updated artist") }
    @Test fun sameTrackArtworkChangeRebuildsPreview() = changedSnapshot { it.copy(metadataArtworkLocator = "/new-cover.jpg") }
    @Test fun sameTrackArtworkCacheKeyChangeRebuildsPreview() = changedSnapshot {
        it.copy(queue = it.queue.map { track -> track.copy(albumId = "changed-album") })
    }

    private fun changedSnapshot(change: (PlaybackSnapshot) -> PlaybackSnapshot) = withLyricsShareLifecycle {
        open()
        val before = store.state.value
        // Cache validity is derived from current content even before the asynchronous observer runs.
        assertFalse(before.copy(snapshot = change(before.snapshot)).hasFreshSharePreview)
        platform.renderAutomatically = false
        platform.ignoreRenderCancellation = true
        dispatch(PlayerIntent.BuildLyricsSharePreview)
        drain()
        val old = platform.renders.last()
        playback.snapshot.value = change(playback.snapshot.value)
        drain()
        val latest = platform.renders.last()
        assertNotEquals(old.input, latest.input)
        assertEquals(before.lyricsShareSession, store.state.value.lyricsShareSession)
        assertEquals(before.selectedLyricsLineIndices, store.state.value.selectedLyricsLineIndices)
        assertFalse(store.state.value.hasFreshSharePreview)
        old.succeed(old.input.lifecycleImage())
        drain()
        assertTrue(store.state.value.isShareRendering)
        assertFalse(store.state.value.hasFreshSharePreview)
        latest.succeed(latest.input.lifecycleImage())
        drain()
        assertTrue(store.state.value.hasFreshSharePreview)
        assertContentEquals(latest.input.lifecycleImage(), store.state.value.sharePreviewBytes)
        assertFalse(store.state.value.isShareRendering)
    }

    @Test fun changedLyricsAtTheSameIndicesReplacePendingPreview() = withLyricsShareLifecycle {
        open()
        val session = store.state.value.lyricsShareSession
        platform.renderAutomatically = false
        platform.ignoreRenderCancellation = true
        dispatch(PlayerIntent.BuildLyricsSharePreview)
        drain()
        val old = platform.renders.last()
        lyrics.replace(original.id, LyricsDocument(listOf(LyricsLine(0, "Corrected first"),
            LyricsLine(1000, "Corrected second")), sourceId = "source", rawPayload = "corrected"))
        drain()
        val latest = platform.renders.last()
        assertEquals(listOf("Corrected first"), latest.input.lyricsLines)
        assertEquals(session, store.state.value.lyricsShareSession)
        old.fail()
        drain()
        assertTrue(store.state.value.isShareRendering)
        assertNull(store.state.value.sharePreviewError)
        latest.succeed(latest.input.lifecycleImage())
        drain()
        assertTrue(store.state.value.hasFreshSharePreview)
        assertContentEquals(latest.input.lifecycleImage(), store.state.value.sharePreviewBytes)
    }

    @Test fun playbackPositionAndUnselectedLyricsDoNotRebuildPreview() = withLyricsShareLifecycle {
        open()
        val before = store.state.value
        val renders = platform.renders.size
        playback.snapshot.value = playback.snapshot.value.copy(positionMs = 1500)
        drain()
        assertEquals(renders, platform.renders.size)
        assertEquals(before.selectedLyricsLineIndices, store.state.value.selectedLyricsLineIndices)
        val changed = assertNotNull(before.lyrics).copy(lines = before.lyrics.lines.mapIndexed { i, line ->
            if (i == 1) line.copy(text = "Unselected correction") else line
        })
        assertTrue(before.copy(lyrics = changed).hasFreshSharePreview)
        assertTrue(store.state.value.hasFreshSharePreview)
    }

    @Test fun imageCopyAfterArtworkChangeDoesNotReuseOldPreview() = withLyricsShareLifecycle {
        open()
        val old = assertNotNull(store.state.value.sharePreviewBytes).copyOf()
        platform.renderAutomatically = false
        playback.snapshot.value = playback.snapshot.value.copy(metadataArtworkLocator = "/new-cover.jpg")
        drain()
        val live = platform.renders.last()
        dispatch(PlayerIntent.CopyLyricsShareImage)
        drain()
        val committed = platform.renders.last()
        assertNotSame(live, committed)
        assertEquals("/new-cover.jpg", committed.input.artworkLocator)
        closeShare()
        assertTrue(live.cancelled)
        assertFalse(committed.cancelled)
        committed.succeed(committed.input.lifecycleImage())
        drain()
        platform.copies.single().succeed(Unit)
        drain()
        assertEquals(listOf("image:" + committed.input.lifecycleImage().decodeToString()), platform.copied)
        assertNotEquals("image:" + old.decodeToString(), platform.copied.single())
    }
}
