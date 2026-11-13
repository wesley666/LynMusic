package top.iwesley.lyn.music

import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

class TrackListActionsTest {
    @Test fun `tag editing is optional and follows song information`() {
        var edits = 0
        val actions = trackListMenuActions(TrackFavoriteUiState(false), {}, null, {}, {}, onEditMusicTags = { edits++ })
        assertEquals(listOf("favorite", "playlist", "info", "edit_tags", "offline"), actions.map { it.id })
        actions.single { it.id == "edit_tags" }.onClick()
        assertEquals(1, edits)
        assertFalse(TrackListActionBindings().canEditMusicTags(Track("a", "local", "A", mediaLocator = "/a.flac", relativePath = "a.flac")))
        assertNull(TrackListActionBindings().onEditMusicTags)
    }

    @Test fun `only enabled local folder sources expose tag editing regardless of cached locator`() {
        val track = Track("a", "source", "A", mediaLocator = "/downloads/a.flac", relativePath = "a.flac")
        for (type in ImportSourceType.entries) for (enabled in listOf(false, true)) {
            val source = ImportSource("source", type, "Source", "/Music", enabled = enabled)
            assertEquals(type == ImportSourceType.LOCAL_FOLDER && enabled,
                canEditTrackMusicTags(track, listOf(SourceWithStatus(source))))
        }
        assertFalse(canEditTrackMusicTags(track, emptyList()))
        assertFalse(canEditTrackMusicTags(track.copy(sourceId = "unknown"), listOf(
            SourceWithStatus(ImportSource("source", ImportSourceType.LOCAL_FOLDER, "Local", "/Music")))))
    }

    @Test fun `navigation actions keep their order and are independently optional`() {
        for (artistAvailable in listOf(false, true)) {
            for (albumAvailable in listOf(false, true)) {
                val events = mutableListOf<String>()
                val actions = trackListMenuActions(
                    favorite = TrackFavoriteUiState(false),
                    onSetFavorite = { events += "favorite" },
                    onAddToPlaylist = { events += "playlist" },
                    onTrackInfo = { events += "info" },
                    onOfflineDownload = { events += "offline" },
                    onArtistClick = if (artistAvailable) ({ events += "artist" }) else null,
                    onAlbumClick = if (albumAvailable) ({ events += "album" }) else null,
                )
                val navigationIds = listOfNotNull("artist".takeIf { artistAvailable }, "album".takeIf { albumAvailable })
                assertEquals(listOf("favorite", "playlist") + navigationIds + listOf("info", "offline"), actions.map { it.id })
                actions.filter { it.id in navigationIds }.forEach { it.onClick() }
                assertEquals(navigationIds, events)
            }
        }
    }

    @Test fun `actions have stable order and omit unsupported downloads`() {
        val actions = trackListMenuActions(TrackFavoriteUiState(false), {}, {}, {}, {})
        assertEquals(listOf("favorite", "playlist", "info", "offline"), actions.map { it.id })
        assertTrue(actions.all { it.enabled })
        val unavailable = trackListMenuActions(TrackFavoriteUiState(false, canChange = false), {}, null, {}, null)
        assertEquals(listOf("favorite", "playlist", "info"), unavailable.map { it.id })
        assertFalse(unavailable[0].enabled)
        assertFalse(unavailable[1].enabled)
        assertTrue(unavailable[2].enabled)
    }

    @Test fun `unknown state requests idempotent like and known liked state requests unlike`() {
        val calls = mutableListOf<Boolean>()
        for (state in listOf(TrackFavoriteUiState(true, isKnown = false), TrackFavoriteUiState(false), TrackFavoriteUiState(true))) {
            trackListMenuActions(state, { calls += it }, null, {}, null).first().onClick()
        }
        assertEquals(listOf(true, true, false), calls)
    }
}
