package top.iwesley.lyn.music

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.DesktopLyricsPosition
import top.iwesley.lyn.music.platform.JvmAppPreferencesStore
import top.iwesley.lyn.music.platform.JvmSettingsPropertiesFile
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter

class JvmAppPreferencesStoreTest {
    @Test
    fun `conditional source updates reject stale values and preserve selection after reload`() = runTest {
        val temporaryDirectory = Files.createTempDirectory("lynmusic-source-filter-conditional")
        try {
            val settingsFile = temporaryDirectory.resolve("settings.properties").toFile()
            val store = JvmAppPreferencesStore(settingsFile)
            val first = LibrarySourceFilter.Source("dav-a")
            val second = LibrarySourceFilter.Source("dav-b")
            for (favorites in listOf(false, true)) {
                val setFilter: suspend (LibrarySourceFilter) -> Unit =
                    if (favorites) store::setFavoritesSourceFilter else store::setLibrarySourceFilter
                val compareAndSet: suspend (LibrarySourceFilter, LibrarySourceFilter) -> Boolean =
                    if (favorites) store::compareAndSetFavoritesSourceFilter else store::compareAndSetLibrarySourceFilter
                val current = if (favorites) store.favoritesSourceFilter else store.librarySourceFilter
                setFilter(first)
                assertTrue(compareAndSet(first, LibrarySourceFilter.ALL))
                assertEquals(LibrarySourceFilter.ALL, current.value)
                setFilter(second)
                assertFalse(compareAndSet(first, LibrarySourceFilter.ALL))
                assertEquals(second, current.value)
                val reloaded = JvmAppPreferencesStore(settingsFile)
                assertEquals(second, if (favorites) reloaded.favoritesSourceFilter.value else reloaded.librarySourceFilter.value)
            }
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `conditional settings update checks the expected value after the pending write finishes`() = runBlocking {
        val temporaryDirectory = Files.createTempDirectory("lynmusic-source-filter-write-race")
        val releaseFirstWrite = CountDownLatch(1)
        try {
            val settingsFile = temporaryDirectory.resolve("settings.properties").toFile()
            val propertiesFile = JvmSettingsPropertiesFile(settingsFile)
            val first = LibrarySourceFilter.Source("dav-a")
            val second = LibrarySourceFilter.Source("dav-b")
            val current = MutableStateFlow<LibrarySourceFilter>(first)
            val firstWriteStarted = CountDownLatch(1)
            val conditionChecked = CountDownLatch(1)
            val userWrite = async(Dispatchers.IO) {
                propertiesFile.update(
                    mutate = {
                        firstWriteStarted.countDown()
                        check(releaseFirstWrite.await(5, TimeUnit.SECONDS))
                        setProperty("library_source_filter", "source:dav-b")
                    },
                    onPersisted = { current.value = second },
                )
            }
            assertTrue(firstWriteStarted.await(5, TimeUnit.SECONDS))
            val fallback = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                propertiesFile.updateIf(
                    condition = { conditionChecked.countDown(); current.value == first },
                    mutate = { setProperty("library_source_filter", "ALL") },
                    onPersisted = { current.value = LibrarySourceFilter.ALL },
                )
            }
            assertEquals(1L, conditionChecked.count)
            releaseFirstWrite.countDown()
            userWrite.await()
            assertFalse(fallback.await())
            assertEquals(second, current.value)
            assertEquals(second, JvmAppPreferencesStore(settingsFile).librarySourceFilter.value)
        } finally {
            releaseFirstWrite.countDown()
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `source selections survive file round trip and replace old protocol selections`() = runTest {
        val temporaryDirectory = Files.createTempDirectory("lynmusic-source-filter")
        try {
            val settingsFile = temporaryDirectory.resolve("settings.properties")
            Files.writeString(settingsFile, "library_source_filter=WEBDAV\nfavorites_source_filter=NAVIDROME\n")
            val store = JvmAppPreferencesStore(settingsFile.toFile())
            assertEquals(LibrarySourceFilter.ALL, store.librarySourceFilter.value)
            assertEquals(LibrarySourceFilter.ALL, store.favoritesSourceFilter.value)
            store.setLibrarySourceFilter(LibrarySourceFilter.Source("dav-1"))
            store.setFavoritesSourceFilter(LibrarySourceFilter.Source("nav-2"))

            val reloaded = JvmAppPreferencesStore(settingsFile.toFile())
            assertEquals(LibrarySourceFilter.Source("dav-1"), reloaded.librarySourceFilter.value)
            assertEquals(LibrarySourceFilter.Source("nav-2"), reloaded.favoritesSourceFilter.value)
            reloaded.setLibrarySourceFilter(LibrarySourceFilter.LOCAL_FOLDER)
            reloaded.setFavoritesSourceFilter(LibrarySourceFilter.LOCAL_FOLDER)
            val localSelection = JvmAppPreferencesStore(settingsFile.toFile())
            assertEquals(LibrarySourceFilter.LOCAL_FOLDER, localSelection.librarySourceFilter.value)
            assertEquals(LibrarySourceFilter.LOCAL_FOLDER, localSelection.favoritesSourceFilter.value)
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `valid desktop lyrics position survives a file round trip`() = runTest {
        val temporaryDirectory = Files.createTempDirectory("lynmusic-desktop-lyrics-position")
        try {
            val settingsFile = temporaryDirectory.resolve("settings.properties").toFile()
            val store = JvmAppPreferencesStore(settingsFile)

            assertEquals(null, store.desktopLyricsPosition.value)
            store.setDesktopLyricsPosition(
                DesktopLyricsPosition(
                    centerXFraction = 0.8f,
                    centerYFraction = 0.1f,
                    displayId = "secondary-display",
                ),
            )

            val reloadedStore = JvmAppPreferencesStore(settingsFile)
            assertEquals(
                DesktopLyricsPosition(0.8f, 0.1f, "secondary-display"),
                reloadedStore.desktopLyricsPosition.value,
            )
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `invalid desktop lyrics position is treated as missing`() {
        val temporaryDirectory = Files.createTempDirectory("lynmusic-invalid-desktop-lyrics-position")
        try {
            val settingsFile = temporaryDirectory.resolve("settings.properties")
            Files.writeString(
                settingsFile,
                "desktop_lyrics_position_x=not-a-number\n" +
                    "desktop_lyrics_position_y=0.5\n" +
                    "desktop_lyrics_position_display_id=secondary-display\n",
            )

            assertEquals(
                null,
                JvmAppPreferencesStore(settingsFile.toFile()).desktopLyricsPosition.value,
            )
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `out of range desktop lyrics position is treated as missing`() {
        val temporaryDirectory = Files.createTempDirectory("lynmusic-out-of-range-desktop-lyrics-position")
        try {
            val settingsFile = temporaryDirectory.resolve("settings.properties")
            Files.writeString(
                settingsFile,
                "desktop_lyrics_position_x=1.01\n" +
                    "desktop_lyrics_position_y=0.5\n" +
                    "desktop_lyrics_position_display_id=secondary-display\n",
            )

            assertEquals(
                null,
                JvmAppPreferencesStore(settingsFile.toFile()).desktopLyricsPosition.value,
            )
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `auto open player on startup preference defaults to false and survives a file round trip`() = runTest {
        val temporaryDirectory = Files.createTempDirectory("lynmusic-auto-open-player-preference")
        try {
            val settingsFile = temporaryDirectory.resolve("settings.properties").toFile()
            val store = JvmAppPreferencesStore(settingsFile)

            assertFalse(store.autoOpenPlayerOnStartup.value)
            store.setAutoOpenPlayerOnStartup(true)

            val reloadedStore = JvmAppPreferencesStore(settingsFile)
            assertTrue(reloadedStore.autoOpenPlayerOnStartup.value)
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `minimize window on close preference survives a file round trip`() = runTest {
        val temporaryDirectory = Files.createTempDirectory("lynmusic-preferences-roundtrip")
        try {
            val settingsFile = temporaryDirectory.resolve("settings.properties").toFile()
            val store = JvmAppPreferencesStore(settingsFile)

            assertTrue(store.minimizeWindowOnClose.value)
            store.setMinimizeWindowOnClose(false)

            val reloadedStore = JvmAppPreferencesStore(settingsFile)
            assertFalse(reloadedStore.minimizeWindowOnClose.value)
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `properties file serializes complete read modify write transactions`() = runBlocking {
        val temporaryDirectory = Files.createTempDirectory("lynmusic-preferences-serialized")
        val releaseFirstMutation = CountDownLatch(1)
        try {
            val propertiesFile = JvmSettingsPropertiesFile(
                temporaryDirectory.resolve("settings.properties").toFile(),
            )
            val firstMutationStarted = CountDownLatch(1)
            val secondMutationStarted = CountDownLatch(1)
            val firstUpdate = async(Dispatchers.IO) {
                propertiesFile.update(
                    mutate = {
                        firstMutationStarted.countDown()
                        check(releaseFirstMutation.await(5, TimeUnit.SECONDS))
                        setProperty("first", "1")
                    },
                )
            }
            assertTrue(firstMutationStarted.await(5, TimeUnit.SECONDS))

            val secondUpdate = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                propertiesFile.update(
                    mutate = {
                        secondMutationStarted.countDown()
                        setProperty("second", "2")
                    },
                )
            }

            assertEquals(1L, secondMutationStarted.count)
            releaseFirstMutation.countDown()
            awaitAll(firstUpdate, secondUpdate)

            val persisted = propertiesFile.load()
            assertEquals("1", persisted.getProperty("first"))
            assertEquals("2", persisted.getProperty("second"))
        } finally {
            releaseFirstMutation.countDown()
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `concurrent preference setters preserve every updated key`() = runTest {
        val temporaryDirectory = Files.createTempDirectory("lynmusic-preferences-concurrent")
        try {
            val settingsFile = temporaryDirectory.resolve("settings.properties").toFile()
            val store = JvmAppPreferencesStore(settingsFile)

            awaitAll(
                async(Dispatchers.Default) { store.setAutoPlayOnStartup(true) },
                async(Dispatchers.Default) { store.setMinimizeWindowOnClose(false) },
                async(Dispatchers.Default) { store.setShowDesktopLyrics(true) },
                async(Dispatchers.Default) { store.setPlaybackVolume(0.42f) },
                async(Dispatchers.Default) { store.setSelectedTheme(AppThemeId.Forest) },
            )

            val reloadedStore = JvmAppPreferencesStore(settingsFile)
            assertTrue(reloadedStore.autoPlayOnStartup.value)
            assertFalse(reloadedStore.minimizeWindowOnClose.value)
            assertTrue(reloadedStore.showDesktopLyrics.value)
            assertEquals(0.42f, reloadedStore.playbackVolume.value)
            assertEquals(AppThemeId.Forest, reloadedStore.selectedTheme.value)
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }
}
