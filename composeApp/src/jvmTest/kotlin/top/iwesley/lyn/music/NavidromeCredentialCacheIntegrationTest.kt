package top.iwesley.lyn.music

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.buildNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.withSecureInMemoryCache
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.domain.resolveNavidromeCoverArtUrl
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl

class NavidromeCredentialCacheIntegrationTest {

    @Test
    fun `navidrome stream and cover resolution reuse cached credential`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val delegate = CountingSecureCredentialStore(
            linkedMapOf("nav-cred" to "plain-pass"),
        )
        val store = delegate.withSecureInMemoryCache()

        val firstStream = resolveNavidromeStreamUrl(
            database = database,
            secureCredentialStore = store,
            locator = buildNavidromeSongLocator("nav-source", "song-1"),
        )
        val secondStream = resolveNavidromeStreamUrl(
            database = database,
            secureCredentialStore = store,
            locator = buildNavidromeSongLocator("nav-source", "song-1"),
        )
        val firstCover = resolveNavidromeCoverArtUrl(
            database = database,
            secureCredentialStore = store,
            locator = buildNavidromeCoverLocator("nav-source", "cover-1"),
        )
        val secondCover = resolveNavidromeCoverArtUrl(
            database = database,
            secureCredentialStore = store,
            locator = buildNavidromeCoverLocator("nav-source", "cover-1"),
        )

        assertNotNull(firstStream)
        assertNotNull(secondStream)
        assertNotNull(firstCover)
        assertNotNull(secondCover)
        assertEquals(1, delegate.getCount("nav-cred"))
    }

    private fun createTestDatabase(): LynMusicDatabase {
        val path = Files.createTempFile("lynmusic-navidrome-cache", ".db")
        return buildLynMusicDatabase(
            Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
        )
    }

    private suspend fun seedNavidromeSource(database: LynMusicDatabase) {
        database.importSourceDao().upsert(
            ImportSourceEntity(
                id = "nav-source",
                type = "NAVIDROME",
                label = "Navidrome",
                rootReference = "https://demo.example.com/navidrome",
                server = null,
                shareName = null,
                directoryPath = null,
                username = "demo",
                credentialKey = "nav-cred",
                allowInsecureTls = false,
                lastScannedAt = null,
                createdAt = 1L,
            ),
        )
    }
}

private class CountingSecureCredentialStore(
    private val values: MutableMap<String, String> = linkedMapOf(),
) : SecureCredentialStore {
    private val getCounts = linkedMapOf<String, Int>()

    override suspend fun put(key: String, value: String) {
        values[key] = value
    }

    override suspend fun get(key: String): String? {
        getCounts[key] = getCount(key) + 1
        return values[key]
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    fun getCount(key: String): Int = getCounts[key] ?: 0
}
