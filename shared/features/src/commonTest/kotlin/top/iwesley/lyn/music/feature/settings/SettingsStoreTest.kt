package top.iwesley.lyn.music.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.LyricsSourceDefinition
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.WorkflowLyricsConfig
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.WorkflowLyricsStepConfig
import top.iwesley.lyn.music.core.model.WorkflowRequestConfig
import top.iwesley.lyn.music.core.model.WorkflowSearchConfig
import top.iwesley.lyn.music.data.repository.SettingsRepository
import top.iwesley.lyn.music.domain.MANAGED_MUSICMATCH_SOURCE_ID
import top.iwesley.lyn.music.domain.buildManagedMusicmatchWorkflowJson
import top.iwesley.lyn.music.domain.parseWorkflowLyricsSourceConfig

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

    @Test
    fun `selecting direct source enters direct edit state`() = runTest {
        val direct = sampleDirectSource()
        val repository = FakeSettingsRepository(listOf(direct, sampleWorkflowSource()))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.SelectConfig(direct))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("direct-1", state.editingId)
        assertEquals("My Direct Source", state.name)
        assertEquals("https://lyrics.example/direct", state.urlTemplate)
        assertEquals(null, state.editingWorkflowId)
        scope.cancel()
    }

    @Test
    fun `selecting workflow source enters workflow edit state and backfills json`() = runTest {
        val workflow = sampleWorkflowSource()
        val repository = FakeSettingsRepository(listOf(sampleDirectSource(), workflow))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(workflow))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("wf-1", state.editingWorkflowId)
        assertEquals(workflow.rawJson, state.workflowJsonInput)
        scope.cancel()
    }

    @Test
    fun `creating new direct source from edit saves a second source`() = runTest {
        val direct = sampleDirectSource()
        val repository = FakeSettingsRepository(listOf(direct))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.SelectConfig(direct))
        store.dispatch(SettingsIntent.NameChanged("Forked Source"))
        store.dispatch(SettingsIntent.CreateNew)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("歌词源已新建。", state.message)
        assertEquals("Forked Source", state.name)
        assertEquals("https://lyrics.example/direct", state.urlTemplate)
        assertEquals(2, repository.currentSources().size)
        assertEquals(setOf("My Direct Source", "Forked Source"), repository.currentSources().map { it.name }.toSet())
        scope.cancel()
    }

    @Test
    fun `workflow json edits keep current workflow edit state`() = runTest {
        val workflow = sampleWorkflowSource()
        val repository = FakeSettingsRepository(listOf(workflow))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(workflow))
        store.dispatch(SettingsIntent.WorkflowJsonChanged(workflow.rawJson.replace("Workflow Source", "Workflow Source v2")))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("wf-1", state.editingWorkflowId)
        assertEquals(true, "Workflow Source v2" in state.workflowJsonInput)
        scope.cancel()
    }

    @Test
    fun `creating new workflow from edit saves a second source with a new id`() = runTest {
        val workflow = sampleWorkflowSource()
        val repository = FakeSettingsRepository(listOf(workflow))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(workflow))
        store.dispatch(SettingsIntent.WorkflowJsonChanged(workflow.rawJson.replace("Workflow Source", "Forked Workflow")))
        store.dispatch(SettingsIntent.CreateNewWorkflow)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("Workflow 源已新建。", state.message)
        val workflowSources = repository.currentSources().filterIsInstance<WorkflowLyricsSourceConfig>()
        assertEquals(2, workflowSources.size)
        assertEquals(setOf("Workflow Source", "Forked Workflow"), workflowSources.map { it.name }.toSet())
        assertEquals(true, workflowSources.any { it.id != "wf-1" && it.name == "Forked Workflow" })
        assertEquals(true, "Forked Workflow" in state.workflowJsonInput)
        scope.cancel()
    }

    @Test
    fun `duplicate direct save failure sets message and preserves form`() = runTest {
        val repository = FakeSettingsRepository(
            listOf(
                sampleDirectSource(name = "Taken Name"),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.NameChanged(" taken name "))
        store.dispatch(SettingsIntent.UrlChanged("https://lyrics.example/new"))
        store.dispatch(SettingsIntent.Save)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("歌词源名称已存在。", state.message)
        assertEquals(" taken name ", state.name)
        assertEquals("https://lyrics.example/new", state.urlTemplate)
        assertEquals(null, state.editingId)
        scope.cancel()
    }

    @Test
    fun `clearing workflow edit resets workflow editor and keeps sources`() = runTest {
        val workflow = sampleWorkflowSource()
        val repository = FakeSettingsRepository(listOf(sampleDirectSource(), workflow))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(workflow))
        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(null))
        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(null))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(null, state.editingWorkflowId)
        assertEquals("", state.workflowJsonInput)
        assertEquals(2, state.sources.size)
        scope.cancel()
    }

    @Test
    fun `saving musicmatch token creates managed workflow source`() = runTest {
        val repository = FakeSettingsRepository(emptyList())
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.MusicmatchUserTokenChanged("token-123"))
        store.dispatch(SettingsIntent.SaveMusicmatch)
        advanceUntilIdle()

        val state = store.state.value
        val managed = repository.currentSources()
            .filterIsInstance<WorkflowLyricsSourceConfig>()
            .single()

        assertEquals("Musicmatch 已保存。", state.message)
        assertEquals("token-123", state.musicmatchUserToken)
        assertEquals(true, state.hasMusicmatchSource)
        assertEquals(MANAGED_MUSICMATCH_SOURCE_ID, managed.id)
        scope.cancel()
    }

    @Test
    fun `clearing musicmatch removes managed workflow source`() = runTest {
        val repository = FakeSettingsRepository(listOf(sampleMusicmatchSource()))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ClearMusicmatch)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("", state.musicmatchUserToken)
        assertEquals(false, state.hasMusicmatchSource)
        assertEquals(emptyList(), repository.currentSources())
        scope.cancel()
    }

    @Test
    fun `viewing managed musicmatch workflow keeps raw workflow editor closed`() = runTest {
        val musicmatch = sampleMusicmatchSource()
        val repository = FakeSettingsRepository(listOf(musicmatch))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(repository, scope)

        advanceUntilIdle()
        store.dispatch(SettingsIntent.ViewWorkflow(musicmatch))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("token-123", state.musicmatchUserToken)
        assertEquals(true, state.hasMusicmatchSource)
        assertEquals(null, state.editingWorkflowId)
        assertEquals("", state.workflowJsonInput)
        scope.cancel()
    }
}

private class FakeSettingsRepository(
    sources: List<LyricsSourceDefinition> = emptyList(),
) : SettingsRepository {
    private val mutableSources = MutableStateFlow(sources)
    private val mutableUseSambaCache = MutableStateFlow(true)

    override val lyricsSources: Flow<List<LyricsSourceDefinition>> = mutableSources.asStateFlow()
    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()

    fun currentSources(): List<LyricsSourceDefinition> = mutableSources.value

    override suspend fun ensureDefaults() = Unit

    override suspend fun setUseSambaCache(enabled: Boolean) {
        mutableUseSambaCache.value = enabled
    }

    override suspend fun saveLyricsSource(config: LyricsSourceConfig) {
        val normalized = normalizeName(config.name)
        if (mutableSources.value.any { it.id != config.id && normalizeName(it.name) == normalized }) {
            error("歌词源名称已存在。")
        }
        mutableSources.value = (mutableSources.value.filterNot { it.id == config.id } + config)
            .sortedWith(compareByDescending<LyricsSourceDefinition> { it.priority }.thenBy { it.name.lowercase() })
    }

    override suspend fun saveWorkflowLyricsSource(rawJson: String, editingId: String?): WorkflowLyricsSourceConfig {
        val parsed = parseWorkflowLyricsSourceConfig(rawJson)
        val config = parsed
        if (editingId != null && config.id != editingId) {
            error("Workflow 源 id 不支持修改。")
        }
        val normalized = normalizeName(config.name)
        if (mutableSources.value.any { it.id != config.id && normalizeName(it.name) == normalized }) {
            error("歌词源名称已存在。")
        }
        mutableSources.value = (mutableSources.value.filterNot { it.id == config.id } + config)
            .sortedWith(compareByDescending<LyricsSourceDefinition> { it.priority }.thenBy { it.name.lowercase() })
        return config
    }

    override suspend fun setLyricsSourceEnabled(sourceId: String, enabled: Boolean) = Unit

    override suspend fun deleteLyricsSource(configId: String) {
        mutableSources.value = mutableSources.value.filterNot { it.id == configId }
    }
}

private fun sampleDirectSource(
    id: String = "direct-1",
    name: String = "My Direct Source",
): LyricsSourceConfig {
    return LyricsSourceConfig(
        id = id,
        name = name,
        method = RequestMethod.GET,
        urlTemplate = "https://lyrics.example/direct",
        responseFormat = LyricsResponseFormat.JSON,
        extractor = "json-map:lyrics=plainLyrics,title=trackName",
        priority = 10,
        enabled = true,
    )
}

private fun sampleWorkflowSource(
    id: String = "wf-1",
    name: String = "Workflow Source",
    rawJson: String = workflowJson(id, name),
): WorkflowLyricsSourceConfig {
    return WorkflowLyricsSourceConfig(
        id = id,
        name = name,
        priority = 5,
        enabled = true,
        search = WorkflowSearchConfig(
            request = WorkflowRequestConfig(
                method = RequestMethod.GET,
                url = "https://lyrics.example/search",
                responseFormat = LyricsResponseFormat.JSON,
            ),
            resultPath = "items",
            mapping = mapOf(
                "id" to "id",
                "title" to "title",
                "artists" to "artists",
            ),
        ),
        lyrics = WorkflowLyricsConfig(
            steps = listOf(
                WorkflowLyricsStepConfig(
                    request = WorkflowRequestConfig(
                        method = RequestMethod.GET,
                        url = "https://lyrics.example/item/{candidate.id}",
                        responseFormat = LyricsResponseFormat.JSON,
                    ),
                    payloadPath = "lyrics",
                    format = LyricsResponseFormat.TEXT,
                ),
            ),
        ),
        rawJson = rawJson,
    )
}

private fun sampleMusicmatchSource(): WorkflowLyricsSourceConfig {
    return parseWorkflowLyricsSourceConfig(buildManagedMusicmatchWorkflowJson("token-123"))
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
          "priority": 5,
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

private fun normalizeName(value: String): String = value.trim().lowercase()
