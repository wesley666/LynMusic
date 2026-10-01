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
import top.iwesley.lyn.music.core.model.AutoPlayOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.AutoOpenPlayerOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.MenuBarLyricsControlsPreferencesStore
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackDecoderPreferencesStore
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.PlayerArtworkStylePreferencesStore
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.WindowClosePreferencesStore
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.WorkflowLyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.domain.DEFAULT_LRCAPI_URL
import top.iwesley.lyn.music.domain.MANAGED_LRCAPI_SOURCE_ID
import top.iwesley.lyn.music.domain.MANAGED_LRCAPI_SOURCE_NAME
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
    fun `desktop lyrics preference defaults to false`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            desktopLyricsPreferencesStore = preferences,
        )

        assertEquals(false, repository.showDesktopLyrics.value)
    }

    @Test
    fun `setting desktop lyrics preference writes through to preference store`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            desktopLyricsPreferencesStore = preferences,
        )

        repository.setShowDesktopLyrics(true)

        assertEquals(true, preferences.showDesktopLyrics.value)
        assertEquals(true, repository.showDesktopLyrics.value)
    }

    @Test
    fun `menu bar lyrics controls preference defaults to false`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            menuBarLyricsControlsPreferencesStore = preferences,
        )

        assertEquals(false, repository.showMenuBarLyricsControls.value)
    }

    @Test
    fun `setting menu bar lyrics controls preference writes through to preference store`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            menuBarLyricsControlsPreferencesStore = preferences,
        )

        repository.setShowMenuBarLyricsControls(true)

        assertEquals(true, preferences.showMenuBarLyricsControls.value)
        assertEquals(true, repository.showMenuBarLyricsControls.value)
    }

    @Test
    fun `auto play on startup preference defaults to false`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            autoPlayOnStartupPreferencesStore = preferences,
        )

        assertEquals(false, repository.autoPlayOnStartup.value)
    }

    @Test
    fun `setting auto play on startup preference writes through to preference store`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            autoPlayOnStartupPreferencesStore = preferences,
        )

        repository.setAutoPlayOnStartup(true)

        assertEquals(true, preferences.autoPlayOnStartup.value)
        assertEquals(true, repository.autoPlayOnStartup.value)

        repository.setAutoPlayOnStartup(false)

        assertEquals(false, preferences.autoPlayOnStartup.value)
        assertEquals(false, repository.autoPlayOnStartup.value)
    }

    @Test
    fun `auto open player on startup preference defaults to false`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            autoOpenPlayerOnStartupPreferencesStore = preferences,
        )

        assertEquals(false, repository.autoOpenPlayerOnStartup.value)
    }

    @Test
    fun `setting auto open player on startup preference writes through to preference store`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            autoOpenPlayerOnStartupPreferencesStore = preferences,
        )

        repository.setAutoOpenPlayerOnStartup(true)

        assertEquals(true, preferences.autoOpenPlayerOnStartup.value)
        assertEquals(true, repository.autoOpenPlayerOnStartup.value)

        repository.setAutoOpenPlayerOnStartup(false)

        assertEquals(false, preferences.autoOpenPlayerOnStartup.value)
        assertEquals(false, repository.autoOpenPlayerOnStartup.value)
    }

    @Test
    fun `minimize window on close preference defaults to true`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            windowClosePreferencesStore = preferences,
        )

        assertEquals(true, repository.minimizeWindowOnClose.value)
    }

    @Test
    fun `setting minimize window on close preference writes through to preference store`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            windowClosePreferencesStore = preferences,
        )

        repository.setMinimizeWindowOnClose(false)

        assertEquals(false, preferences.minimizeWindowOnClose.value)
        assertEquals(false, repository.minimizeWindowOnClose.value)

        repository.setMinimizeWindowOnClose(true)

        assertEquals(true, preferences.minimizeWindowOnClose.value)
        assertEquals(true, repository.minimizeWindowOnClose.value)
    }

    @Test
    fun `android extension decoder preference defaults to false`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            playbackDecoderPreferencesStore = preferences,
        )

        assertEquals(false, repository.useAndroidExtensionDecoder.value)
    }

    @Test
    fun `setting android extension decoder preference writes through to preference store`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            playbackDecoderPreferencesStore = preferences,
        )

        repository.setUseAndroidExtensionDecoder(true)

        assertEquals(true, preferences.useAndroidExtensionDecoder.value)
        assertEquals(true, repository.useAndroidExtensionDecoder.value)
    }

    @Test
    fun `player artwork style preference defaults to vinyl`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            playerArtworkStylePreferencesStore = preferences,
        )

        assertEquals(PlayerArtworkStyle.VINYL, repository.playerArtworkStyle.value)
    }

    @Test
    fun `setting player artwork style preference writes through to preference store`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(
            database = database,
            sambaCachePreferencesStore = preferences,
            themePreferencesStore = preferences,
            desktopVlcPreferencesStore = preferences,
            playerArtworkStylePreferencesStore = preferences,
        )

        repository.setPlayerArtworkStyle(PlayerArtworkStyle.HALF_RECORD)

        assertEquals(PlayerArtworkStyle.HALF_RECORD, preferences.playerArtworkStyle.value)
        assertEquals(PlayerArtworkStyle.HALF_RECORD, repository.playerArtworkStyle.value)
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
        assertEquals(listOf("lrclib", MANAGED_LRCAPI_SOURCE_ID), saved.map { it.id }.sorted())
        val lrclib = saved.single { it.id == "lrclib" }
        assertEquals("LRCLIB", lrclib.name)
        assertEquals("https://lrclib.net/api/search", lrclib.urlTemplate)
        assertEquals("track_name={title}&artist_name={artist}", lrclib.queryTemplate)
        assertEquals(LRCLIB_JSON_MAP_EXTRACTOR, lrclib.extractor)
        assertEquals(100, lrclib.priority)
        assertEquals(true, lrclib.enabled)
    }

    @Test
    fun `ensure defaults seeds oiapi qqmusic preset workflow when absent`() = runTest {
        val database = createSettingsTestDatabase()
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.ensureDefaults()

        val direct = database.lyricsSourceConfigDao().getAll()
        val workflows = database.workflowLyricsSourceConfigDao().getAll()
        assertEquals(listOf("lrclib", MANAGED_LRCAPI_SOURCE_ID), direct.map { it.id }.sorted())
        val lrcApi = direct.single { it.id == MANAGED_LRCAPI_SOURCE_ID }
        assertEquals(MANAGED_LRCAPI_SOURCE_NAME, lrcApi.name)
        assertEquals(DEFAULT_LRCAPI_URL, lrcApi.urlTemplate)
        assertEquals(110, lrcApi.priority)
        assertEquals(true, lrcApi.enabled)
        assertEquals("lrclib", direct.single { it.id == "lrclib" }.id)
        assertEquals(1, workflows.size)
        assertEquals(PRESET_OIAPI_QQMUSIC_SOURCE_ID, workflows.single().id)
        assertEquals(PRESET_OIAPI_QQMUSIC_SOURCE_NAME, workflows.single().name)
        assertEquals(PRESET_OIAPI_QQMUSIC_SOURCE_PRIORITY, workflows.single().priority)
        assertEquals(true, workflows.single().enabled)
        assertEquals(true, workflows.single().rawJson.contains("https://oiapi.net/api/QQMusicLyric"))
        assertEquals(true, workflows.single().rawJson.contains("\"minScore\": 0.9"))
    }

    @Test
    fun `ensure defaults adds lrcapi to existing direct sources`() = runTest {
        val database = createSettingsTestDatabase()
        database.lyricsSourceConfigDao().upsert(directEntity(id = "custom-direct", name = "Custom Direct"))
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.ensureDefaults()

        val direct = database.lyricsSourceConfigDao().getAll()
        assertEquals(true, direct.any { it.id == "custom-direct" })
        assertEquals(false, direct.any { it.id == "lrclib" })
        val lrcApi = direct.single { it.id == MANAGED_LRCAPI_SOURCE_ID }
        assertEquals(DEFAULT_LRCAPI_URL, lrcApi.urlTemplate)
        assertEquals(MANAGED_LRCAPI_SOURCE_NAME, lrcApi.name)
    }

    @Test
    fun `ensure defaults keeps existing custom lrcapi source`() = runTest {
        val database = createSettingsTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            directEntity(id = MANAGED_LRCAPI_SOURCE_ID, name = MANAGED_LRCAPI_SOURCE_NAME).copy(
                urlTemplate = "https://lyrics.example/custom-lrcapi",
                priority = 9,
                enabled = false,
            ),
        )
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.ensureDefaults()

        val lrcApi = database.lyricsSourceConfigDao().getAll().single { it.id == MANAGED_LRCAPI_SOURCE_ID }
        assertEquals("https://lyrics.example/custom-lrcapi", lrcApi.urlTemplate)
        assertEquals(9, lrcApi.priority)
        assertEquals(false, lrcApi.enabled)
    }

    @Test
    fun `ensure defaults skips lrcapi when another source already uses its name`() = runTest {
        val database = createSettingsTestDatabase()
        database.lyricsSourceConfigDao().upsert(directEntity(id = "custom-lrcapi", name = " LrcAPI "))
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.ensureDefaults()

        val direct = database.lyricsSourceConfigDao().getAll()
        assertEquals(false, direct.any { it.id == MANAGED_LRCAPI_SOURCE_ID })
        assertEquals(true, direct.any { it.id == "custom-lrcapi" })
    }

    @Test
    fun `ensure defaults skips lrcapi when workflow source already uses its name`() = runTest {
        val database = createSettingsTestDatabase()
        database.workflowLyricsSourceConfigDao().upsert(workflowEntity(id = "wf-lrcapi", name = " LrcAPI "))
        val preferences = FakePreferencesStore()
        val repository = DefaultSettingsRepository(database, preferences, preferences, preferences)

        repository.ensureDefaults()

        val direct = database.lyricsSourceConfigDao().getAll()
        assertEquals(false, direct.any { it.id == MANAGED_LRCAPI_SOURCE_ID })
        assertEquals(true, database.workflowLyricsSourceConfigDao().getAll().any { it.id == "wf-lrcapi" })
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

        assertEquals("lyrics_source_name_already_exists", error.message)
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

        assertEquals("lyrics_source_name_already_exists", error.message)
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

        assertEquals("lyrics_source_name_already_exists", error.message)
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

        assertEquals("lyrics_workflow_id_immutable", error.message)
    }
}

private fun createSettingsTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-settings", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private class FakePreferencesStore : SambaCachePreferencesStore, ThemePreferencesStore, DesktopVlcPreferencesStore,
    AutoPlayOnStartupPreferencesStore, AutoOpenPlayerOnStartupPreferencesStore, WindowClosePreferencesStore,
    CompactPlayerLyricsPreferencesStore, DesktopLyricsPreferencesStore, MenuBarLyricsControlsPreferencesStore,
    NavidromeAudioQualityPreferencesStore, PlaybackDecoderPreferencesStore, PlayerArtworkStylePreferencesStore {
    override val useSambaCache = MutableStateFlow(true)
    override val showCompactPlayerLyrics = MutableStateFlow(false)
    override val showDesktopLyrics = MutableStateFlow(false)
    override val showMenuBarLyricsControls = MutableStateFlow(false)
    override val autoPlayOnStartup = MutableStateFlow(false)
    override val autoOpenPlayerOnStartup = MutableStateFlow(false)
    override val minimizeWindowOnClose = MutableStateFlow(true)
    override val useAndroidExtensionDecoder = MutableStateFlow(false)
    override val playerArtworkStyle = MutableStateFlow(PlayerArtworkStyle.VINYL)
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

    override suspend fun setShowDesktopLyrics(enabled: Boolean) {
        showDesktopLyrics.value = enabled
    }

    override suspend fun setShowMenuBarLyricsControls(enabled: Boolean) {
        showMenuBarLyricsControls.value = enabled
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        autoPlayOnStartup.value = enabled
    }

    override suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean) {
        autoOpenPlayerOnStartup.value = enabled
    }

    override suspend fun setMinimizeWindowOnClose(enabled: Boolean) {
        minimizeWindowOnClose.value = enabled
    }

    override suspend fun setUseAndroidExtensionDecoder(enabled: Boolean) {
        useAndroidExtensionDecoder.value = enabled
    }

    override suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle) {
        playerArtworkStyle.value = style
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
