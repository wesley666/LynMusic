package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.DesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.WorkflowLyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

class SettingsRepositoryTest {

    @Test
    fun `theme preferences default to classic theme`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        assertEquals(AppThemeId.Classic, repository.selectedTheme.value)
        assertEquals(defaultCustomThemeTokens(), repository.customThemeTokens.value)
        assertEquals(defaultThemeTextPalettePreferences(), repository.textPalettePreferences.value)
        assertEquals(AppThemeTextPalette.Black, repository.textPalettePreferences.value.forest)
        assertEquals(AppThemeTextPalette.Black, repository.textPalettePreferences.value.ocean)
    }

    @Test
    fun `setting theme preferences writes through to preference store`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)
        val customTokens = AppThemeTokens(
            backgroundArgb = 0xFF101820.toInt(),
            accentArgb = 0xFF2F9E44.toInt(),
            focusArgb = 0xFFF2C078.toInt(),
        )

        repository.setSelectedTheme(AppThemeId.Ocean)
        repository.setCustomThemeTokens(customTokens)
        repository.setTextPalette(AppThemeId.Ocean, AppThemeTextPalette.Black)

        assertEquals(AppThemeId.Ocean, preferences.selectedTheme.value)
        assertEquals(customTokens, preferences.customThemeTokens.value)
        assertEquals(AppThemeTextPalette.Black, preferences.textPalettePreferences.value.ocean)
        assertEquals(AppThemeId.Ocean, repository.selectedTheme.value)
        assertEquals(customTokens, repository.customThemeTokens.value)
        assertEquals(AppThemeTextPalette.Black, repository.textPalettePreferences.value.ocean)
    }

    @Test
    fun `ensure defaults merges legacy lrclib entries into one built in source`() = runTest {
        val database = createSettingsTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            directEntity(id = "lrclib-synced", name = "LRCLIB Synced").copy(
                urlTemplate = "https://lrclib.net/api/search",
                queryTemplate = "track_name={title}&artist_name={artist}&duration={duration_seconds}",
                extractor = "json-map:lyrics=syncedLyrics,title=trackName",
                priority = 100,
                enabled = true,
            ),
        )
        database.lyricsSourceConfigDao().upsert(
            directEntity(id = "lrclib-plain", name = "LRCLIB Plain").copy(
                urlTemplate = "https://lrclib.net/api/search",
                queryTemplate = "track_name={title}&artist_name={artist}&album_name={album}",
                extractor = "json-map:lyrics=plainLyrics,title=trackName",
                priority = 90,
                enabled = false,
            ),
        )
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.ensureDefaults()

        val saved = database.lyricsSourceConfigDao().getAll()
        assertEquals(1, saved.size)
        assertEquals("lrclib", saved.single().id)
        assertEquals("LRCLIB", saved.single().name)
        assertEquals("https://lrclib.net/api/search", saved.single().urlTemplate)
        assertEquals("track_name={title}&artist_name={artist}", saved.single().queryTemplate)
        assertEquals(LRCLIB_JSON_MAP_EXTRACTOR, saved.single().extractor)
        assertEquals(100, saved.single().priority)
        assertEquals(true, saved.single().enabled)
    }

    @Test
    fun `saving direct source rejects duplicate direct name ignoring case and trim`() = runTest {
        val database = createSettingsTestDatabase()
        database.lyricsSourceConfigDao().upsert(directEntity(id = "direct-1", name = "LRCLIB"))
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        val error = assertFailsWith<IllegalStateException> {
            repository.saveLyricsSource(
                directConfig(id = "direct-2", name = " lrclib "),
            )
        }

        assertEquals("歌词源名称已存在。", error.message)
    }

    @Test
    fun `saving direct source rejects duplicate workflow name`() = runTest {
        val database = createSettingsTestDatabase()
        database.workflowLyricsSourceConfigDao().upsert(workflowEntity(id = "wf-1", name = "QQ Lyrics"))
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        val error = assertFailsWith<IllegalStateException> {
            repository.saveLyricsSource(
                directConfig(id = "direct-2", name = " qq lyrics "),
            )
        }

        assertEquals("歌词源名称已存在。", error.message)
    }

    @Test
    fun `editing direct source can keep original name`() = runTest {
        val database = createSettingsTestDatabase()
        database.lyricsSourceConfigDao().upsert(directEntity(id = "direct-1", name = "My Lyrics"))
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.saveLyricsSource(
            directConfig(
                id = "direct-1",
                name = " my lyrics ",
                urlTemplate = "https://lyrics.example/v2",
            ),
        )

        val saved = database.lyricsSourceConfigDao().getAll().single()
        assertEquals(" my lyrics ", saved.name)
        assertEquals("https://lyrics.example/v2", saved.urlTemplate)
    }

    @Test
    fun `saving workflow rejects duplicate name across all lyrics sources`() = runTest {
        val database = createSettingsTestDatabase()
        database.lyricsSourceConfigDao().upsert(directEntity(id = "direct-1", name = "Shared Name"))
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        val error = assertFailsWith<IllegalStateException> {
            repository.saveWorkflowLyricsSource(
                rawJson = workflowJson(id = "wf-1", name = " shared name "),
                editingId = null,
            )
        }

        assertEquals("歌词源名称已存在。", error.message)
    }

    @Test
    fun `editing workflow can rename to unique name`() = runTest {
        val database = createSettingsTestDatabase()
        database.workflowLyricsSourceConfigDao().upsert(workflowEntity(id = "wf-1", name = "Old Workflow"))
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        val saved = repository.saveWorkflowLyricsSource(
            rawJson = workflowJson(id = "wf-1", name = "New Workflow"),
            editingId = "wf-1",
        )

        assertEquals("wf-1", saved.id)
        assertEquals("New Workflow", saved.name)
        assertEquals("New Workflow", database.workflowLyricsSourceConfigDao().getById("wf-1")?.name)
    }

    @Test
    fun `editing workflow cannot change id`() = runTest {
        val database = createSettingsTestDatabase()
        database.workflowLyricsSourceConfigDao().upsert(workflowEntity(id = "wf-1", name = "Old Workflow"))
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        val error = assertFailsWith<IllegalStateException> {
            repository.saveWorkflowLyricsSource(
                rawJson = workflowJson(id = "wf-2", name = "Renamed Workflow"),
                editingId = "wf-1",
            )
        }

        assertEquals("Workflow 源 id 不支持修改。", error.message)
    }
}

private fun createSettingsTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-settings", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private class FakePreferencesStore : SambaCachePreferencesStore, ThemePreferencesStore, DesktopVlcPreferencesStore {
    override val useSambaCache = MutableStateFlow(true)
    override val selectedTheme = MutableStateFlow(AppThemeId.Classic)
    override val customThemeTokens = MutableStateFlow(defaultCustomThemeTokens())
    override val textPalettePreferences = MutableStateFlow(defaultThemeTextPalettePreferences())
    override val desktopVlcManualPath = MutableStateFlow<String?>(null)
    override val desktopVlcAutoDetectedPath = MutableStateFlow<String?>(null)
    override val desktopVlcEffectivePath = MutableStateFlow<String?>(null)

    override suspend fun setUseSambaCache(enabled: Boolean) {
        useSambaCache.value = enabled
    }

    override suspend fun setSelectedTheme(themeId: AppThemeId) {
        selectedTheme.value = themeId
    }

    override suspend fun setCustomThemeTokens(tokens: AppThemeTokens) {
        customThemeTokens.value = tokens
    }

    override suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette) {
        textPalettePreferences.value = textPalettePreferences.value.withThemePalette(themeId, palette)
    }

    override suspend fun setDesktopVlcManualPath(path: String?) {
        desktopVlcManualPath.value = path
        desktopVlcEffectivePath.value = path ?: desktopVlcAutoDetectedPath.value
    }

    override suspend fun setDesktopVlcAutoDetectedPath(path: String?) {
        desktopVlcAutoDetectedPath.value = path
        desktopVlcEffectivePath.value = desktopVlcManualPath.value ?: path
    }
}

private fun directConfig(
    id: String,
    name: String,
    urlTemplate: String = "https://lyrics.example/api",
): LyricsSourceConfig {
    return LyricsSourceConfig(
        id = id,
        name = name,
        method = RequestMethod.GET,
        urlTemplate = urlTemplate,
        responseFormat = LyricsResponseFormat.JSON,
        extractor = "json-map:lyrics=plainLyrics,title=trackName",
        priority = 0,
        enabled = true,
    )
}

private fun directEntity(
    id: String,
    name: String,
): LyricsSourceConfigEntity {
    return LyricsSourceConfigEntity(
        id = id,
        name = name,
        method = "GET",
        urlTemplate = "https://lyrics.example/api",
        headersTemplate = "",
        queryTemplate = "",
        bodyTemplate = "",
        responseFormat = "JSON",
        extractor = "json-map:lyrics=plainLyrics,title=trackName",
        priority = 0,
        enabled = true,
    )
}

private fun workflowEntity(
    id: String,
    name: String,
): WorkflowLyricsSourceConfigEntity {
    return WorkflowLyricsSourceConfigEntity(
        id = id,
        name = name,
        priority = 0,
        enabled = true,
        rawJson = workflowJson(id = id, name = name),
    )
}

private fun workflowJson(
    id: String,
    name: String,
): String {
    return """
        {
          "id": "$id",
          "name": "$name",
          "kind": "workflow",
          "enabled": true,
          "priority": 0,
          "search": {
            "method": "GET",
            "url": "https://lyrics.example/search",
            "responseFormat": "JSON",
            "resultPath": "items",
            "mapping": {
              "id": "id",
              "title": "title",
              "artists": "artists"
            }
          },
          "lyrics": {
            "steps": [
              {
                "method": "GET",
                "url": "https://lyrics.example/item/{candidate.id}",
                "responseFormat": "JSON",
                "payloadPath": "lyrics",
                "format": "TEXT"
              }
            ]
          }
        }
    """.trimIndent()
}
