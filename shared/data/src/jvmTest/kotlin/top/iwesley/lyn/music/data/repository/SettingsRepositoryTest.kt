package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.WorkflowLyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.domain.PRESET_OIAPI_QQMUSIC_SOURCE_ID
import top.iwesley.lyn.music.domain.PRESET_OIAPI_QQMUSIC_SOURCE_NAME
import top.iwesley.lyn.music.domain.PRESET_OIAPI_QQMUSIC_SOURCE_PRIORITY

class SettingsRepositoryTest {

    @Test
    fun `theme preferences default to classic white theme`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        assertEquals(AppThemeId.Ocean, repository.selectedTheme.value)
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
    fun `compact player lyrics preference defaults to false`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            compactPlayerLyricsPreferencesStore = preferences,
        )

        assertEquals(false, repository.showCompactPlayerLyrics.value)
    }

    @Test
    fun `setting compact player lyrics preference writes through to preference store`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            compactPlayerLyricsPreferencesStore = preferences,
        )

        repository.setShowCompactPlayerLyrics(true)

        assertEquals(true, preferences.showCompactPlayerLyrics.value)
        assertEquals(true, repository.showCompactPlayerLyrics.value)
    }

    @Test
    fun `navidrome audio quality preferences default to wifi original and mobile 192`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            navidromeAudioQualityPreferencesStore = preferences,
        )

        assertEquals(NavidromeAudioQuality.Original, repository.navidromeWifiAudioQuality.value)
        assertEquals(NavidromeAudioQuality.Kbps192, repository.navidromeMobileAudioQuality.value)
    }

    @Test
    fun `setting navidrome audio quality preferences writes through to preference store`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            navidromeAudioQualityPreferencesStore = preferences,
        )

        repository.setNavidromeWifiAudioQuality(NavidromeAudioQuality.Kbps320)
        repository.setNavidromeMobileAudioQuality(NavidromeAudioQuality.Kbps128)

        assertEquals(NavidromeAudioQuality.Kbps320, preferences.navidromeWifiAudioQuality.value)
        assertEquals(NavidromeAudioQuality.Kbps128, preferences.navidromeMobileAudioQuality.value)
        assertEquals(NavidromeAudioQuality.Kbps320, repository.navidromeWifiAudioQuality.value)
        assertEquals(NavidromeAudioQuality.Kbps128, repository.navidromeMobileAudioQuality.value)
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
    fun `ensure defaults seeds oiapi qqmusic preset workflow when absent`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.ensureDefaults()

        val direct = database.lyricsSourceConfigDao().getAll()
        val workflows = database.workflowLyricsSourceConfigDao().getAll()
        assertEquals(1, direct.size)
        assertEquals("lrclib", direct.single().id)
        assertEquals(1, workflows.size)
        assertEquals(PRESET_OIAPI_QQMUSIC_SOURCE_ID, workflows.single().id)
        assertEquals(PRESET_OIAPI_QQMUSIC_SOURCE_NAME, workflows.single().name)
        assertEquals(PRESET_OIAPI_QQMUSIC_SOURCE_PRIORITY, workflows.single().priority)
        assertEquals(true, workflows.single().enabled)
        assertEquals(true, workflows.single().rawJson.contains("https://oiapi.net/api/QQMusicLyric"))
        assertEquals(true, workflows.single().rawJson.contains("\"minScore\": 0.9"))
    }

    @Test
    fun `ensure defaults keeps existing workflow with preset id`() = runTest {
        val database = createSettingsTestDatabase()
        database.workflowLyricsSourceConfigDao().upsert(
            workflowEntity(id = PRESET_OIAPI_QQMUSIC_SOURCE_ID, name = "My QQMusic Source"),
        )
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.ensureDefaults()

        val workflows = database.workflowLyricsSourceConfigDao().getAll()
        assertEquals(1, workflows.size)
        assertEquals(PRESET_OIAPI_QQMUSIC_SOURCE_ID, workflows.single().id)
        assertEquals("My QQMusic Source", workflows.single().name)
    }

    @Test
    fun `ensure defaults skips preset workflow when name already exists`() = runTest {
        val database = createSettingsTestDatabase()
        database.workflowLyricsSourceConfigDao().upsert(
            workflowEntity(id = "wf-oiapi", name = " ${PRESET_OIAPI_QQMUSIC_SOURCE_NAME.lowercase()} "),
        )
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.ensureDefaults()

        val workflows = database.workflowLyricsSourceConfigDao().getAll()
        assertEquals(1, workflows.size)
        assertEquals("wf-oiapi", workflows.single().id)
        assertEquals(" ${PRESET_OIAPI_QQMUSIC_SOURCE_NAME.lowercase()} ", workflows.single().name)
    }

    @Test
    fun `workflow lyrics sources synchronize raw json enabled flag from entity state`() = runTest {
        val database = createSettingsTestDatabase()
        database.workflowLyricsSourceConfigDao().upsert(
            workflowEntity(
                id = "wf-1",
                name = "Workflow Source",
                enabled = true,
                rawJson = workflowJson(id = "wf-1", name = "Workflow Source", enabled = false),
            ),
        )
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        val workflow = repository.lyricsSources.first()
            .filterIsInstance<WorkflowLyricsSourceConfig>()
            .single()

        assertEquals(true, workflow.enabled)
        assertTrue(workflow.rawJson.contains("\"enabled\": true"))
    }

    @Test
    fun `toggling workflow enabled rewrites persisted raw json enabled flag`() = runTest {
        val database = createSettingsTestDatabase()
        database.workflowLyricsSourceConfigDao().upsert(
            workflowEntity(
                id = "wf-1",
                name = "Workflow Source",
                enabled = false,
                rawJson = workflowJson(id = "wf-1", name = "Workflow Source", enabled = false),
            ),
        )
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.setLyricsSourceEnabled("wf-1", enabled = true)

        val saved = database.workflowLyricsSourceConfigDao().getById("wf-1")
        assertEquals(true, saved?.enabled)
        assertTrue(saved?.rawJson?.contains("\"enabled\": true") == true)
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

private class FakePreferencesStore : SambaCachePreferencesStore, ThemePreferencesStore, DesktopVlcPreferencesStore,
    CompactPlayerLyricsPreferencesStore, NavidromeAudioQualityPreferencesStore {
    override val useSambaCache = MutableStateFlow(true)
    override val showCompactPlayerLyrics = MutableStateFlow(false)
    override val navidromeWifiAudioQuality = MutableStateFlow(NavidromeAudioQuality.Original)
    override val navidromeMobileAudioQuality = MutableStateFlow(NavidromeAudioQuality.Kbps192)
    override val selectedTheme = MutableStateFlow(AppThemeId.Ocean)
    override val customThemeTokens = MutableStateFlow(defaultCustomThemeTokens())
    override val textPalettePreferences = MutableStateFlow(defaultThemeTextPalettePreferences())
    override val desktopVlcManualPath = MutableStateFlow<String?>(null)
    override val desktopVlcAutoDetectedPath = MutableStateFlow<String?>(null)
    override val desktopVlcEffectivePath = MutableStateFlow<String?>(null)

    override suspend fun setUseSambaCache(enabled: Boolean) {
        useSambaCache.value = enabled
    }

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        showCompactPlayerLyrics.value = enabled
    }

    override suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality) {
        navidromeWifiAudioQuality.value = quality
    }

    override suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality) {
        navidromeMobileAudioQuality.value = quality
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
    enabled: Boolean = true,
    rawJson: String = workflowJson(id = id, name = name, enabled = enabled),
): WorkflowLyricsSourceConfigEntity {
    return WorkflowLyricsSourceConfigEntity(
        id = id,
        name = name,
        priority = 0,
        enabled = enabled,
        rawJson = rawJson,
    )
}

private fun workflowJson(
    id: String,
    name: String,
    enabled: Boolean = true,
): String {
    return """
        {
          "id": "$id",
          "name": "$name",
          "kind": "workflow",
          "enabled": $enabled,
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
