package top.iwesley.lyn.music.domain

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.requireUi
import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.uiText
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.decodeURLPart
import io.ktor.http.encodedPath
import io.ktor.http.parseUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.iwesley.lyn.music.core.model.DiagnosticLogLevel
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.EmbyCredential
import top.iwesley.lyn.music.core.model.EmbySourceDraft
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportScanWarning
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.NonNavidromeAudioScanResult
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.buildEmbyCoverLocator
import top.iwesley.lyn.music.core.model.buildEmbySongLocator
import top.iwesley.lyn.music.core.model.classifyAudioExtensionForImport
import top.iwesley.lyn.music.core.model.parseEmbyCoverLocator
import top.iwesley.lyn.music.core.model.parseEmbySongLocator
import top.iwesley.lyn.music.core.model.unsupportedAudioImportFailure
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import kotlin.random.Random
import kotlin.time.Instant

private const val EMBY_CLIENT_NAME = "LynMusic"
private const val EMBY_DEVICE_NAME = "LynMusic"
private const val EMBY_VERSION = "1.0.0"
private const val EMBY_PAGE_SIZE = 200
private const val EMBY_IMPORT_PAGE_SIZE = 100
private val EMBY_IMPORT_RETRY_PAGE_SIZES = listOf(EMBY_IMPORT_PAGE_SIZE, 50, 25)
private val embyJson = Json { ignoreUnknownKeys = true }

const val EMBY_LYRICS_SOURCE_ID = "emby-lyrics"
internal const val EMBY_DEVICE_ID_CREDENTIAL_KEY = "emby-device-id"

data class EmbyResolvedSource(
    val sourceId: String,
    val baseUrl: String,
    val wanBaseUrl: String? = null,
    val addressSelector: RemoteSourceAddressSelector? = null,
    val credential: EmbyCredential,
    val deviceId: String,
)

data class EmbyFavoriteItem(
    val itemId: String,
    val favoritedAt: Long?,
)

data class EmbyPlaylistSummaryPayload(
    val id: String,
    val name: String,
)

data class EmbyPlaylistEntryPayload(
    val itemId: String,
    val playlistItemId: String?,
)

data class EmbyRecentTrackPayload(
    val itemId: String,
    val albumId: String?,
    val playedAt: Long?,
    val playCount: Int?,
)

fun normalizeEmbyBaseUrl(rawUrl: String?): String {
    val value = rawUrl.orEmpty().trim()
    requireUi(value.isNotBlank()) { uiText(Res.string.server_address_required, "Emby") }
    requireUi('?' !in value && '#' !in value) { uiText(Res.string.server_address_no_query, "Emby") }
    val parsed = parseUrl(value) ?: throw UiTextException(uiText(Res.string.server_address_invalid, "Emby"))
    requireUi(parsed.protocol.name in setOf("http", "https")) { uiText(Res.string.server_address_http_required, "Emby") }
    requireUi(parsed.host.isNotBlank()) { uiText(Res.string.server_address_host_required, "Emby") }
    requireUi(parsed.user == null && parsed.password == null) { uiText(Res.string.server_address_no_credentials, "Emby") }
    val decodedSegments = parsed.encodedPath
        .split('/')
        .filter { it.isNotBlank() }
        .map { it.decodeURLPart() }
    val normalizedPath = URLBuilder().apply {
        encodedPath = "/"
        if (decodedSegments.isNotEmpty()) {
            appendPathSegments(decodedSegments)
        }
    }.encodedPath.removeSuffix("/").ifBlank { "/" }
    return URLBuilder(parsed).apply {
        encodedUser = null
        encodedPassword = null
        encodedParameters.clear()
        encodedFragment = ""
        encodedPath = normalizedPath
        if (port == protocol.defaultPort) {
            port = DEFAULT_PORT
        }
    }.buildString().removeSuffix("/")
}

suspend fun resolveEmbyDeviceId(secureCredentialStore: SecureCredentialStore): String {
    secureCredentialStore.get(EMBY_DEVICE_ID_CREDENTIAL_KEY)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    val deviceId = generateEmbyDeviceId()
    secureCredentialStore.put(EMBY_DEVICE_ID_CREDENTIAL_KEY, deviceId)
    return deviceId
}

suspend fun authenticateEmby(
    draft: EmbySourceDraft,
    deviceId: String,
    httpClient: LyricsHttpClient,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    timeoutMillis: Long? = null,
): EmbyCredential {
    requireUi(draft.username.isNotBlank()) { uiText(Res.string.server_username_required, "Emby") }
    requireUi(draft.password.isNotBlank()) { uiText(Res.string.server_password_required, "Emby") }
    requireUi(deviceId.isNotBlank()) { uiText(Res.string.server_field_empty, "Emby", "DeviceId") }
    val baseUrl = normalizeEmbyBaseUrl(draft.baseUrl)
    val request = LyricsRequest(
        method = RequestMethod.POST,
        url = buildEmbyApiUrl(baseUrl, "Users", "AuthenticateByName"),
        headers = mapOf(
            "Content-Type" to "application/json",
            "X-Emby-Authorization" to embyAuthorizationHeader(deviceId = deviceId),
        ),
        body = JsonObject(
            mapOf(
                "Username" to JsonPrimitive(draft.username.trim()),
                "Pw" to JsonPrimitive(draft.password),
            ),
        ).toString(),
        timeoutMillis = timeoutMillis,
    )
    logEmbyRequest(logger, "AuthenticateByName", request.url)
    val response = httpClient.request(request).getOrElse { throwable ->
        throw RemoteSourceRequestException(uiText(Res.string.server_request_failed, "Emby", "AuthenticateByName", throwable.uiErrorDetail()), throwable)
    }
    if (response.statusCode !in 200..299) {
        throw RemoteSourceHttpArgumentException(response.statusCode, uiText(Res.string.server_http_failed, "Emby", "AuthenticateByName", response.statusCode))
    }
    val payload = parseEmbyObject(response.body, "AuthenticateByName")
    val token = payload.string("AccessToken") ?: throw UiTextException(uiText(Res.string.server_response_field_missing, "Emby", "AccessToken"))
    val userId = payload["User"].asObjectOrNull()?.string("Id") ?: throw UiTextException(uiText(Res.string.server_response_field_missing, "Emby", "User.Id"))
    requireUi(token.isNotBlank()) { uiText(Res.string.server_response_field_empty, "Emby", "AccessToken") }
    requireUi(userId.isNotBlank()) { uiText(Res.string.server_response_field_empty, "Emby", "User.Id") }
    return EmbyCredential(userId = userId, accessToken = token)
}

suspend fun testEmbyConnection(
    draft: EmbySourceDraft,
    deviceId: String,
    httpClient: LyricsHttpClient,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    timeoutMillis: Long? = null,
): EmbyCredential {
    return authenticateEmby(draft, deviceId, httpClient, logger, timeoutMillis)
}

suspend fun testEmbyConnection(
    draft: EmbySourceDraft,
    credential: EmbyCredential,
    deviceId: String,
    httpClient: LyricsHttpClient,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    timeoutMillis: Long? = null,
) {
    requireUi(credential.userId.isNotBlank()) { uiText(Res.string.server_field_empty, "Emby", "User.Id") }
    requireUi(credential.accessToken.isNotBlank()) { uiText(Res.string.server_field_empty, "Emby", "AccessToken") }
    requireUi(deviceId.isNotBlank()) { uiText(Res.string.server_field_empty, "Emby", "DeviceId") }
    requestEmbyJson(
        httpClient = httpClient,
        credential = credential,
        deviceId = deviceId,
        url = buildEmbyApiUrl(
            normalizeEmbyBaseUrl(draft.baseUrl),
            "Users",
            credential.userId,
            "Items",
            parameters = mapOf(
                "Recursive" to "true",
                "IncludeItemTypes" to "Audio",
                "MediaTypes" to "Audio",
                "Limit" to "1",
            ),
        ),
        method = RequestMethod.GET,
        body = null,
        operation = "ConnectionTest",
        logger = logger,
        timeoutMillis = timeoutMillis,
    )
}

suspend fun scanEmbyLibrary(
    draft: EmbySourceDraft,
    credential: EmbyCredential,
    deviceId: String,
    sourceId: String,
    httpClient: LyricsHttpClient,
    supportedImportExtensions: Set<String>,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    progressSink: ImportScanProgressSink = ImportScanProgressSink.NoOp,
    timeoutMillis: Long? = null,
): ImportScanReport {
    val baseUrl = normalizeEmbyBaseUrl(draft.baseUrl)
    requireUi(credential.userId.isNotBlank()) { uiText(Res.string.server_field_empty, "Emby", "User.Id") }
    requireUi(credential.accessToken.isNotBlank()) { uiText(Res.string.server_field_empty, "Emby", "AccessToken") }
    requireUi(deviceId.isNotBlank()) { uiText(Res.string.server_field_empty, "Emby", "DeviceId") }
    val tracks = mutableListOf<ImportedTrackCandidate>()
    val failures = mutableListOf<ImportScanFailure>()
    val seenItemIds = linkedSetOf<String>()
    var discoveredAudioFileCount = 0
    var startIndex = 0
    var pageSize = EMBY_IMPORT_PAGE_SIZE
    var totalTrackCount: Int? = null
    while (true) {
        val page = requestEmbyItemsPageWithRetry(
            baseUrl = baseUrl,
            credential = credential,
            deviceId = deviceId,
            startIndex = startIndex,
            pageSize = pageSize,
            httpClient = httpClient,
            logger = logger,
            timeoutMillis = timeoutMillis,
        )
        pageSize = page.pageSize
        totalTrackCount = page.totalRecordCount
        progressSink.onProgress(
            ImportScanProgress(
                sourceId = sourceId,
                phase = ImportScanPhase.Scanning,
                importedTrackCount = tracks.size,
                totalTrackCount = totalTrackCount,
            ),
        )
        val items = page.items
        items.forEach { item ->
            val itemId = item.itemId
            if (itemId.isBlank() || !seenItemIds.add(itemId)) return@forEach
            discoveredAudioFileCount += 1
            val suffix = item.suffix()
            when (classifyAudioExtensionForImport(suffix, supportedImportExtensions)) {
                NonNavidromeAudioScanResult.IMPORT_SUPPORTED -> {
                    tracks += item.toImportedTrackCandidate(sourceId)
                    progressSink.onProgress(
                        ImportScanProgress(
                            sourceId = sourceId,
                            phase = ImportScanPhase.Scanning,
                            importedTrackCount = tracks.size,
                            totalTrackCount = totalTrackCount,
                        ),
                    )
                }
                NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED,
                NonNavidromeAudioScanResult.NOT_AUDIO,
                -> failures += unsupportedAudioImportFailure(item.relativePath())
            }
        }
        val nextStart = startIndex + items.size
        if (items.isEmpty() || nextStart >= page.totalRecordCount) break
        startIndex = nextStart
    }
    logger.log(
        level = DiagnosticLogLevel.INFO,
        tag = "Emby",
        message = "scan-complete source=$sourceId baseUrl=$baseUrl discovered=$discoveredAudioFileCount imported=${tracks.size} failures=${failures.size}",
    )
    return ImportScanReport(
        tracks = tracks,
        warnings = if (discoveredAudioFileCount == 0) listOf(
            ImportScanWarning(
                diagnostic = "当前 Emby 账号下没有可同步的歌曲。",
                text = uiText(Res.string.source_account_no_syncable_tracks, "Emby"),
            ),
        ) else emptyList(),
        discoveredAudioFileCount = discoveredAudioFileCount,
        failures = failures,
        totalTrackCount = totalTrackCount,
    )
}

fun serializeEmbyCredential(credential: EmbyCredential): String {
    return credential.userId + "\n" + credential.accessToken
}

fun parseEmbyCredential(value: String?): EmbyCredential? {
    val lines = value.orEmpty().lines()
    val userId = lines.firstOrNull()?.trim().orEmpty()
    val token = lines.drop(1).joinToString("\n").trim()
    if (userId.isBlank() || token.isBlank()) return null
    return EmbyCredential(userId = userId, accessToken = token)
}

internal suspend fun resolveEmbySource(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    sourceId: String,
    addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
): EmbyResolvedSource? {
    val source = database.importSourceDao().getById(sourceId)
        ?.takeIf { it.type == ImportSourceType.EMBY.name && it.enabled }
        ?: return null
    val credential = parseEmbyCredential(source.credentialKey?.let { secureCredentialStore.get(it) }) ?: return null
    val (lanBaseUrl, wanBaseUrl) = normalizeRemoteSourceBaseUrls(
        sourceType = ImportSourceType.EMBY,
        lanBaseUrl = source.rootReference,
        wanBaseUrl = source.wanRootReference,
        normalizeBaseUrl = ::normalizeEmbyBaseUrl,
    )
    return EmbyResolvedSource(
        sourceId = source.id,
        baseUrl = lanBaseUrl,
        wanBaseUrl = wanBaseUrl,
        addressSelector = addressSelector,
        credential = credential,
        deviceId = resolveEmbyDeviceId(secureCredentialStore),
    )
}

suspend fun resolveEmbyStreamUrl(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
): String? {
    val parsed = parseEmbySongLocator(locator) ?: return null
    val source = resolveEmbySource(database, secureCredentialStore, parsed.first, addressSelector) ?: return null
    return buildEmbyStreamUrl(
        baseUrl = source.preferredBaseUrl(),
        itemId = parsed.second,
        accessToken = source.credential.accessToken,
    )
}

suspend fun resolveEmbyStreamUrlCandidates(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
): List<RemoteSourceResolvedUrl>? {
    val parsed = parseEmbySongLocator(locator) ?: return null
    val source = resolveEmbySource(database, secureCredentialStore, parsed.first, addressSelector) ?: return null
    return source.addressCandidates().map { candidate ->
        RemoteSourceResolvedUrl(
            sourceId = source.sourceId,
            kind = candidate.kind,
            value = buildEmbyStreamUrl(
                baseUrl = candidate.value,
                itemId = parsed.second,
                accessToken = source.credential.accessToken,
            ),
        )
    }
}

suspend fun resolveEmbyDownloadUrl(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
): String? {
    val parsed = parseEmbySongLocator(locator) ?: return null
    val source = resolveEmbySource(database, secureCredentialStore, parsed.first, addressSelector) ?: return null
    return buildEmbyDownloadUrl(
        baseUrl = source.preferredBaseUrl(),
        itemId = parsed.second,
        accessToken = source.credential.accessToken,
    )
}

suspend fun resolveEmbyDownloadUrlCandidates(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
): List<RemoteSourceResolvedUrl>? {
    val parsed = parseEmbySongLocator(locator) ?: return null
    val source = resolveEmbySource(database, secureCredentialStore, parsed.first, addressSelector) ?: return null
    return source.addressCandidates().map { candidate ->
        RemoteSourceResolvedUrl(
            sourceId = source.sourceId,
            kind = candidate.kind,
            value = buildEmbyDownloadUrl(
                baseUrl = candidate.value,
                itemId = parsed.second,
                accessToken = source.credential.accessToken,
            ),
        )
    }
}

suspend fun resolveEmbyCoverArtUrl(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
): String? {
    val parsed = parseEmbyCoverLocator(locator) ?: return null
    val source = resolveEmbySource(database, secureCredentialStore, parsed.first, addressSelector) ?: return null
    return buildEmbyCoverArtUrl(
        baseUrl = source.preferredBaseUrl(),
        itemId = parsed.second,
        accessToken = source.credential.accessToken,
    )
}

suspend fun resolveEmbyCoverArtUrlCandidates(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
): List<RemoteSourceResolvedUrl>? {
    val parsed = parseEmbyCoverLocator(locator) ?: return null
    val source = resolveEmbySource(database, secureCredentialStore, parsed.first, addressSelector) ?: return null
    return source.addressCandidates().map { candidate ->
        RemoteSourceResolvedUrl(
            sourceId = source.sourceId,
            kind = candidate.kind,
            value = buildEmbyCoverArtUrl(
                baseUrl = candidate.value,
                itemId = parsed.second,
                accessToken = source.credential.accessToken,
            ),
        )
    }
}

fun buildEmbyStreamUrl(
    baseUrl: String,
    itemId: String,
    accessToken: String,
): String {
    return URLBuilder(normalizeEmbyBaseUrl(baseUrl)).apply {
        appendPathSegments("Audio", itemId, "stream")
        parameters.append("Static", "true")
        parameters.append("api_key", accessToken)
    }.buildString()
}

fun buildEmbyDownloadUrl(
    baseUrl: String,
    itemId: String,
    accessToken: String,
): String {
    return URLBuilder(normalizeEmbyBaseUrl(baseUrl)).apply {
        appendPathSegments("Items", itemId, "Download")
        parameters.append("api_key", accessToken)
    }.buildString()
}

fun buildEmbyCoverArtUrl(
    baseUrl: String,
    itemId: String,
    accessToken: String,
): String {
    return URLBuilder(normalizeEmbyBaseUrl(baseUrl)).apply {
        appendPathSegments("Items", itemId, "Images", "Primary")
        parameters.append("api_key", accessToken)
    }.buildString()
}

internal suspend fun setEmbyFavorite(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    itemId: String,
    favorite: Boolean,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    val pathSegments = if (favorite) {
        arrayOf("Users", source.credential.userId, "FavoriteItems", itemId)
    } else {
        arrayOf("Users", source.credential.userId, "FavoriteItems", itemId, "Delete")
    }
    requestEmbyUnit(
        httpClient = httpClient,
        source = source,
        method = RequestMethod.POST,
        pathSegments = pathSegments,
        operation = if (favorite) "FavoriteItems" else "FavoriteItems/Delete",
        logger = logger,
    )
}

internal suspend fun fetchEmbyFavorites(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): List<EmbyFavoriteItem> {
    return requestEmbyPagedItems(
        httpClient = httpClient,
        source = source,
        operation = "FavoriteItems",
        parameters = mapOf(
            "Recursive" to "true",
            "MediaTypes" to "Audio",
            "IncludeItemTypes" to "Audio",
            "Filters" to "IsFavorite",
            "Fields" to "UserData",
            "EnableUserData" to "true",
        ),
        logger = logger,
    ).mapNotNull { item ->
        val itemId = item.string("Id") ?: return@mapNotNull null
        EmbyFavoriteItem(
            itemId = itemId,
            favoritedAt = null,
        )
    }
}

internal suspend fun fetchEmbyPlaylists(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): List<EmbyPlaylistSummaryPayload> {
    return requestEmbyPagedItems(
        httpClient = httpClient,
        source = source,
        operation = "Playlists",
        parameters = mapOf(
            "Recursive" to "true",
            "IncludeItemTypes" to "Playlist",
            "MediaTypes" to "Audio",
        ),
        logger = logger,
    ).mapNotNull { item ->
        val id = item.string("Id") ?: return@mapNotNull null
        val name = item.string("Name")?.trim().orEmpty().ifBlank { "未命名歌单" }
        EmbyPlaylistSummaryPayload(id = id, name = name)
    }
}

internal suspend fun createEmbyPlaylist(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    name: String,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): EmbyPlaylistSummaryPayload {
    val payload = requestEmbyJson(
        httpClient = httpClient,
        source = source,
        method = RequestMethod.POST,
        pathSegments = arrayOf("Playlists"),
        parameters = mapOf(
            "UserId" to source.credential.userId,
            "Name" to name,
            "MediaType" to "Audio",
        ),
        operation = "CreatePlaylist",
        logger = logger,
    )
    val id = payload.string("Id") ?: throw UiTextException(uiText(Res.string.server_response_field_missing, "Emby", "Id"))
    return EmbyPlaylistSummaryPayload(
        id = id,
        name = payload.string("Name")?.trim().orEmpty().ifBlank { name },
    )
}

internal suspend fun deleteEmbyPlaylist(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    playlistId: String,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    requestEmbyUnit(
        httpClient = httpClient,
        source = source,
        method = RequestMethod.DELETE,
        pathSegments = arrayOf("Items", playlistId),
        operation = "DeletePlaylist",
        logger = logger,
    )
}

internal suspend fun updateEmbyPlaylistName(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    playlistId: String,
    name: String,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    val currentItem = requestEmbyJson(
        httpClient = httpClient,
        source = source,
        method = RequestMethod.GET,
        pathSegments = arrayOf("Users", source.credential.userId, "Items", playlistId),
        operation = "PlaylistItem",
        logger = logger,
    )
    val updatedItem = JsonObject(
        currentItem.toMutableMap().apply {
            put("Id", JsonPrimitive(playlistId))
            put("Name", JsonPrimitive(name))
        },
    )
    requestEmbyUnit(
        httpClient = httpClient,
        source = source,
        method = RequestMethod.POST,
        pathSegments = arrayOf("Items", playlistId),
        parameters = mapOf("ItemId" to playlistId),
        body = updatedItem.toString(),
        operation = "UpdatePlaylist",
        logger = logger,
    )
}

internal suspend fun addEmbyPlaylistItem(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    playlistId: String,
    itemId: String,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    requestEmbyUnit(
        httpClient = httpClient,
        source = source,
        method = RequestMethod.POST,
        pathSegments = arrayOf("Playlists", playlistId, "Items"),
        parameters = mapOf(
            "UserId" to source.credential.userId,
            "Ids" to itemId,
        ),
        operation = "AddPlaylistItem",
        logger = logger,
    )
}

internal suspend fun removeEmbyPlaylistEntries(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    playlistId: String,
    entryIds: List<String>,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    if (entryIds.isEmpty()) return
    requestEmbyUnit(
        httpClient = httpClient,
        source = source,
        method = RequestMethod.POST,
        pathSegments = arrayOf("Playlists", playlistId, "Items", "Delete"),
        parameters = mapOf("EntryIds" to entryIds.joinToString(",")),
        operation = "RemovePlaylistItems",
        logger = logger,
    )
}

internal suspend fun fetchEmbyPlaylistEntries(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    playlistId: String,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): List<EmbyPlaylistEntryPayload> {
    return requestEmbyPagedPlaylistItems(
        httpClient = httpClient,
        source = source,
        playlistId = playlistId,
        logger = logger,
    ).mapNotNull { item ->
        val itemId = item.string("Id") ?: return@mapNotNull null
        EmbyPlaylistEntryPayload(
            itemId = itemId,
            playlistItemId = item.string("PlaylistItemId"),
        )
    }
}

internal suspend fun reportEmbyNowPlaying(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    itemId: String,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    requestEmbyUnit(
        httpClient = httpClient,
        source = source,
        method = RequestMethod.POST,
        pathSegments = arrayOf("Users", source.credential.userId, "PlayingItems", itemId),
        operation = "PlayingItems",
        logger = logger,
    )
}

internal suspend fun submitEmbyPlay(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    itemId: String,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    requestEmbyUnit(
        httpClient = httpClient,
        source = source,
        method = RequestMethod.POST,
        pathSegments = arrayOf("Users", source.credential.userId, "PlayedItems", itemId),
        operation = "PlayedItems",
        logger = logger,
    )
}

internal suspend fun fetchEmbyRecentTracks(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    limit: Int,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): List<EmbyRecentTrackPayload> {
    return requestEmbyPagedItems(
        httpClient = httpClient,
        source = source,
        operation = "RecentItems",
        parameters = mapOf(
            "Recursive" to "true",
            "MediaTypes" to "Audio",
            "IncludeItemTypes" to "Audio",
            "IsPlayed" to "true",
            "SortBy" to "DatePlayed",
            "SortOrder" to "Descending",
            "Fields" to "UserData",
            "EnableUserData" to "true",
            "Limit" to limit.coerceAtLeast(1).toString(),
        ),
        logger = logger,
        pageSize = limit.coerceAtLeast(1),
        maxItems = limit.coerceAtLeast(1),
    ).mapNotNull { item ->
        val itemId = item.string("Id") ?: return@mapNotNull null
        val userData = item["UserData"].asObjectOrNull()
        val playedAt = userData?.string("LastPlayedDate")?.let(::parseEmbyTimestampMillis)
        if (playedAt == null) return@mapNotNull null
        EmbyRecentTrackPayload(
            itemId = itemId,
            albumId = item.string("AlbumId"),
            playedAt = playedAt,
            playCount = userData.int("PlayCount"),
        )
    }
}

internal suspend fun requestEmbyLyricsDocument(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    itemId: String,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): LyricsDocument? {
    val payload = requestEmbyTextWithSourceAddressFallback(
        httpClient = httpClient,
        source = source,
        method = RequestMethod.GET,
        pathSegments = arrayOf("Items", itemId, "Lyrics"),
        parameters = emptyMap(),
        body = null,
        operation = "Lyrics",
        logger = logger,
        notFoundAsNull = true,
    )?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return parseEmbyLyricsPayload(payload, EMBY_LYRICS_SOURCE_ID)
}

internal fun parseEmbyLyricsPayload(
    payload: String,
    sourceId: String = EMBY_LYRICS_SOURCE_ID,
): LyricsDocument? {
    val parsed = runCatching { embyJson.parseToJsonElement(payload) }.getOrNull()
        ?: return parseCachedLyrics(sourceId, payload)
    return when (parsed) {
        is JsonObject -> parseEmbyLyricsObject(parsed, sourceId, payload)
        is JsonArray -> parseEmbyLyricsArray(parsed, sourceId, payload)
        is JsonPrimitive -> parsed.contentOrNull?.let { parseCachedLyrics(sourceId, it) }
    } ?: parseCachedLyrics(sourceId, payload)
}

private suspend fun requestEmbyItemsPage(
    baseUrl: String,
    credential: EmbyCredential,
    deviceId: String,
    startIndex: Int,
    pageSize: Int,
    httpClient: LyricsHttpClient,
    logger: DiagnosticLogger,
    timeoutMillis: Long? = null,
): EmbyItemsPage {
    val limit = pageSize.coerceAtLeast(1)
    val url = URLBuilder(baseUrl).apply {
        appendPathSegments("Users", credential.userId, "Items")
        parameters.append("Recursive", "true")
        parameters.append("MediaTypes", "Audio")
        parameters.append("IncludeItemTypes", "Audio")
        parameters.append("Fields", "MediaSources,Path")
        parameters.append("EnableImages", "false")
        parameters.append("StartIndex", startIndex.toString())
        parameters.append("Limit", limit.toString())
    }.buildString()
    val response = requestEmbyJson(
        httpClient = httpClient,
        credential = credential,
        deviceId = deviceId,
        url = url,
        method = RequestMethod.GET,
        body = null,
        operation = "Items",
        logger = logger,
        timeoutMillis = timeoutMillis,
    )
    val items = response["Items"].asObjectList().map { it.toEmbyAudioItem() }
    return EmbyItemsPage(
        items = items,
        totalRecordCount = response.int("TotalRecordCount") ?: items.size,
        pageSize = limit,
    )
}

private suspend fun requestEmbyItemsPageWithRetry(
    baseUrl: String,
    credential: EmbyCredential,
    deviceId: String,
    startIndex: Int,
    pageSize: Int,
    httpClient: LyricsHttpClient,
    logger: DiagnosticLogger,
    timeoutMillis: Long? = null,
): EmbyItemsPage {
    val retryPageSizes = EMBY_IMPORT_RETRY_PAGE_SIZES.filter { it <= pageSize.coerceAtLeast(1) }
        .ifEmpty { listOf(pageSize.coerceAtLeast(1)) }
    var lastTimeout: Throwable? = null
    retryPageSizes.forEach { candidatePageSize ->
        try {
            return requestEmbyItemsPage(
                baseUrl = baseUrl,
                credential = credential,
                deviceId = deviceId,
                startIndex = startIndex,
                pageSize = candidatePageSize,
                httpClient = httpClient,
                logger = logger,
                timeoutMillis = timeoutMillis,
            )
        } catch (throwable: Throwable) {
            if (!throwable.isEmbyTimeoutFailure()) throw throwable
            lastTimeout = throwable
            logger.log(
                level = DiagnosticLogLevel.WARN,
                tag = "Emby",
                message = "items-page-timeout startIndex=$startIndex limit=$candidatePageSize",
            )
        }
    }
    throw UiTextException(
        uiText(Res.string.emby_items_page_timeout, startIndex, retryPageSizes.last()),
        lastTimeout,
    )
}

private suspend fun requestEmbyPagedItems(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    operation: String,
    parameters: Map<String, String>,
    logger: DiagnosticLogger,
    pageSize: Int = EMBY_PAGE_SIZE,
    maxItems: Int = Int.MAX_VALUE,
): List<JsonObject> {
    val items = mutableListOf<JsonObject>()
    var startIndex = 0
    while (items.size < maxItems) {
        val payload = requestEmbyJson(
            httpClient = httpClient,
            source = source,
            method = RequestMethod.GET,
            pathSegments = arrayOf("Users", source.credential.userId, "Items"),
            parameters = parameters + mapOf(
                "StartIndex" to startIndex.toString(),
                "Limit" to pageSize.toString(),
            ),
            operation = operation,
            logger = logger,
        )
        val pageItems = payload["Items"].asObjectList()
        if (pageItems.isEmpty()) break
        items += pageItems.take(maxItems - items.size)
        val totalRecordCount = payload.int("TotalRecordCount") ?: items.size
        startIndex += pageItems.size
        if (startIndex >= totalRecordCount) break
    }
    return items
}

private suspend fun requestEmbyPagedPlaylistItems(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    playlistId: String,
    logger: DiagnosticLogger,
): List<JsonObject> {
    val items = mutableListOf<JsonObject>()
    var startIndex = 0
    while (true) {
        val payload = requestEmbyJson(
            httpClient = httpClient,
            source = source,
            method = RequestMethod.GET,
            pathSegments = arrayOf("Playlists", playlistId, "Items"),
            parameters = mapOf(
                "UserId" to source.credential.userId,
                "Fields" to "UserData",
                "EnableUserData" to "true",
                "StartIndex" to startIndex.toString(),
                "Limit" to EMBY_PAGE_SIZE.toString(),
            ),
            operation = "PlaylistItems",
            logger = logger,
        )
        val pageItems = payload["Items"].asObjectList()
        if (pageItems.isEmpty()) break
        items += pageItems
        val totalRecordCount = payload.int("TotalRecordCount") ?: items.size
        startIndex += pageItems.size
        if (startIndex >= totalRecordCount) break
    }
    return items
}

private suspend fun requestEmbyJson(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    method: RequestMethod,
    pathSegments: Array<String>,
    parameters: Map<String, String> = emptyMap(),
    body: String? = null,
    operation: String,
    logger: DiagnosticLogger,
): JsonObject {
    val payload = requestEmbyTextWithSourceAddressFallback(
        httpClient = httpClient,
        source = source,
        method = method,
        pathSegments = pathSegments,
        parameters = parameters,
        body = body,
        operation = operation,
        logger = logger,
        notFoundAsNull = false,
    ) ?: throw UiTextException(uiText(Res.string.server_response_empty, "Emby", operation))
    return parseEmbyObject(payload, operation)
}

private suspend fun requestEmbyUnit(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    method: RequestMethod,
    pathSegments: Array<String>,
    parameters: Map<String, String> = emptyMap(),
    body: String? = null,
    operation: String,
    logger: DiagnosticLogger,
) {
    requestEmbyTextWithSourceAddressFallback(
        httpClient = httpClient,
        source = source,
        method = method,
        pathSegments = pathSegments,
        parameters = parameters,
        body = body,
        operation = operation,
        logger = logger,
        notFoundAsNull = false,
    )
}

private suspend fun requestEmbyTextWithSourceAddressFallback(
    httpClient: LyricsHttpClient,
    source: EmbyResolvedSource,
    method: RequestMethod,
    pathSegments: Array<String>,
    parameters: Map<String, String>,
    body: String?,
    operation: String,
    logger: DiagnosticLogger,
    notFoundAsNull: Boolean,
): String? {
    val selector = source.addressSelector
    if (selector != null) {
        return selector.withAddressFallback(
            sourceId = source.sourceId,
            sourceType = ImportSourceType.EMBY,
            lanBaseUrl = source.baseUrl,
            wanBaseUrl = source.wanBaseUrl,
            normalizeBaseUrl = ::normalizeEmbyBaseUrl,
        ) { candidate ->
            requestEmbyTextOrNull(
                httpClient = httpClient,
                credential = source.credential,
                deviceId = source.deviceId,
                url = buildEmbyApiUrl(candidate.value, *pathSegments, parameters = parameters),
                method = method,
                body = body,
                operation = operation,
                logger = logger,
                notFoundAsNull = notFoundAsNull,
            )
        }
    }
    return requestEmbyTextOrNull(
        httpClient = httpClient,
        credential = source.credential,
        deviceId = source.deviceId,
        url = buildEmbyApiUrl(source.baseUrl, *pathSegments, parameters = parameters),
        method = method,
        body = body,
        operation = operation,
        logger = logger,
        notFoundAsNull = notFoundAsNull,
    )
}

private suspend fun requestEmbyJson(
    httpClient: LyricsHttpClient,
    credential: EmbyCredential,
    deviceId: String,
    url: String,
    method: RequestMethod,
    body: String?,
    operation: String,
    logger: DiagnosticLogger,
    timeoutMillis: Long? = null,
): JsonObject {
    return parseEmbyObject(
        requestEmbyText(
            httpClient = httpClient,
            credential = credential,
            deviceId = deviceId,
            url = url,
            method = method,
            body = body,
            operation = operation,
            logger = logger,
            timeoutMillis = timeoutMillis,
        ),
        operation,
    )
}

private suspend fun requestEmbyText(
    httpClient: LyricsHttpClient,
    credential: EmbyCredential,
    deviceId: String,
    url: String,
    method: RequestMethod,
    body: String?,
    operation: String,
    logger: DiagnosticLogger,
    timeoutMillis: Long? = null,
): String {
    return requestEmbyTextOrNull(
        httpClient = httpClient,
        credential = credential,
        deviceId = deviceId,
        url = url,
        method = method,
        body = body,
        operation = operation,
        logger = logger,
        notFoundAsNull = false,
        timeoutMillis = timeoutMillis,
    ) ?: throw UiTextException(uiText(Res.string.server_response_empty, "Emby", operation))
}

private suspend fun requestEmbyTextOrNull(
    httpClient: LyricsHttpClient,
    credential: EmbyCredential,
    deviceId: String,
    url: String,
    method: RequestMethod,
    body: String?,
    operation: String,
    logger: DiagnosticLogger,
    notFoundAsNull: Boolean,
    timeoutMillis: Long? = null,
): String? {
    val request = LyricsRequest(
        method = method,
        url = url,
        headers = buildMap {
            put("X-Emby-Token", credential.accessToken)
            put("X-Emby-Authorization", embyAuthorizationHeader(deviceId = deviceId, credential = credential))
            put("Accept", "application/json")
            if (body != null) put("Content-Type", "application/json")
        },
        body = body,
        timeoutMillis = timeoutMillis,
    )
    logEmbyRequest(logger, operation, request.url)
    val response = httpClient.request(request).getOrElse { throwable ->
        throw RemoteSourceRequestException(uiText(Res.string.server_request_failed, "Emby", operation, throwable.uiErrorDetail()), throwable)
    }
    if (response.statusCode == 404 && notFoundAsNull) return null
    if (response.statusCode !in 200..299) {
        throw RemoteSourceHttpArgumentException(response.statusCode, uiText(Res.string.server_http_failed, "Emby", operation, response.statusCode))
    }
    return response.body
}

private fun buildEmbyApiUrl(
    baseUrl: String,
    vararg pathSegments: String,
    parameters: Map<String, String> = emptyMap(),
): String {
    return URLBuilder(baseUrl).apply {
        appendPathSegments(*pathSegments)
        parameters.forEach { (key, value) ->
            if (value.isNotBlank()) {
                this.parameters.append(key, value)
            }
        }
    }.buildString()
}

private fun EmbyResolvedSource.preferredBaseUrl(): String {
    val selector = addressSelector
    if (selector != null) {
        return selector.orderedBaseUrls(
            sourceId = sourceId,
            sourceType = ImportSourceType.EMBY,
            lanBaseUrl = baseUrl,
            wanBaseUrl = wanBaseUrl,
            normalizeBaseUrl = ::normalizeEmbyBaseUrl,
        ).first().value
    }
    return normalizeEmbyBaseUrl(baseUrl)
}

private fun EmbyResolvedSource.addressCandidates(): List<RemoteSourceBaseUrl> {
    val selector = addressSelector
    if (selector != null) {
        return selector.orderedBaseUrls(
            sourceId = sourceId,
            sourceType = ImportSourceType.EMBY,
            lanBaseUrl = baseUrl,
            wanBaseUrl = wanBaseUrl,
            normalizeBaseUrl = ::normalizeEmbyBaseUrl,
        )
    }
    return listOf(RemoteSourceBaseUrl(RemoteSourceAddressKind.LAN, normalizeEmbyBaseUrl(baseUrl)))
}

private fun embyAuthorizationHeader(deviceId: String, credential: EmbyCredential? = null): String {
    return buildString {
        append("Emby Client=\"$EMBY_CLIENT_NAME\", Device=\"$EMBY_DEVICE_NAME\", DeviceId=\"$deviceId\", Version=\"$EMBY_VERSION\"")
        credential?.let {
            append(", UserId=\"")
            append(it.userId)
            append("\", Token=\"")
            append(it.accessToken)
            append('"')
        }
    }
}

private fun generateEmbyDeviceId(): String {
    val bytes = ByteArray(16)
    Random.nextBytes(bytes)
    return "lynmusic-" + bytes.joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

private fun logEmbyRequest(logger: DiagnosticLogger, operation: String, url: String) {
    if (logger === NoopDiagnosticLogger) return
    logger.log(
        level = DiagnosticLogLevel.INFO,
        tag = "Emby",
        message = "request operation=$operation\nurl: ${redactEmbyUrlForLog(url)}",
    )
}

private fun redactEmbyUrlForLog(url: String): String {
    return url.replace(Regex("([?&]api_key=)[^&]*"), "$1<redacted>")
}

private data class EmbyItemsPage(
    val items: List<EmbyAudioItem>,
    val totalRecordCount: Int,
    val pageSize: Int,
)

private data class EmbyAudioItem(
    val itemId: String,
    val title: String,
    val artistName: String?,
    val albumTitle: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val sizeBytes: Long,
    val suffix: String?,
    val fileName: String?,
    val path: String?,
    val coverItemId: String?,
    val bitRate: Int?,
    val channelCount: Int?,
    val samplingRate: Int?,
    val bitDepth: Int?,
) {
    fun suffix(): String? = suffix?.trim()?.takeIf { it.isNotBlank() }
        ?: fileName?.substringAfterLast('.', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
        ?: path?.substringAfterLast('.', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
}

private fun JsonObject.toEmbyAudioItem(): EmbyAudioItem {
    val itemId = string("Id").orEmpty()
    val title = string("Name").orEmpty().ifBlank { "未知曲目" }
    val mediaSource = this["MediaSources"].asObjectList().firstOrNull()
    val artistName = stringArray("Artists").firstOrNull()
        ?: string("AlbumArtist")
        ?: this["ArtistItems"].asObjectList().firstNotNullOfOrNull { it.string("Name") }
    val albumTitle = string("Album")
    val suffix = firstNonBlank(
        string("Container"),
        mediaSource?.string("Container"),
        string("FileName")?.substringAfterLast('.', missingDelimiterValue = ""),
        string("Path")?.substringAfterLast('.', missingDelimiterValue = ""),
    )
    return EmbyAudioItem(
        itemId = itemId,
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        durationMs = ((long("RunTimeTicks") ?: mediaSource?.long("RunTimeTicks") ?: 0L) / 10_000L).coerceAtLeast(0L),
        trackNumber = int("IndexNumber"),
        discNumber = int("ParentIndexNumber"),
        sizeBytes = long("Size") ?: mediaSource?.long("Size") ?: 0L,
        suffix = suffix,
        fileName = string("FileName"),
        path = string("Path"),
        coverItemId = resolveEmbyCoverItemId(this, itemId),
        bitRate = int("Bitrate") ?: mediaSource?.int("Bitrate"),
        channelCount = firstAudioMediaStream(mediaSource)?.int("Channels"),
        samplingRate = firstAudioMediaStream(mediaSource)?.int("SampleRate"),
        bitDepth = firstAudioMediaStream(mediaSource)?.int("BitDepth"),
    )
}

private fun firstAudioMediaStream(mediaSource: JsonObject?): JsonObject? {
    return mediaSource?.get("MediaStreams")
        .asObjectList()
        .firstOrNull { it.string("Type").orEmpty().equals("Audio", ignoreCase = true) }
}

private fun resolveEmbyCoverItemId(item: JsonObject, itemId: String): String? {
    item.string("PrimaryImageItemId")?.takeIf { it.isNotBlank() }?.let { return it }
    if (item.string("PrimaryImageTag").isNullOrBlank().not() || item["ImageTags"].asObjectOrNull()?.string("Primary").isNullOrBlank().not()) {
        return itemId
    }
    val albumId = item.string("AlbumId")
    if (!albumId.isNullOrBlank()) {
        return albumId
    }
    return null
}

private fun EmbyAudioItem.relativePath(): String {
    val fileName = buildString {
        append(normalizeEmbyPathSegment(title.ifBlank { "未知曲目" }))
        suffix()?.let {
            append('.')
            append(it.lowercase())
        }
    }
    return listOf(
        normalizeEmbyPathSegment(artistName.orEmpty().ifBlank { "未知艺人" }),
        normalizeEmbyPathSegment(albumTitle.orEmpty().ifBlank { "未知专辑" }),
        fileName,
    ).joinToString("/")
}

private fun EmbyAudioItem.toImportedTrackCandidate(sourceId: String): ImportedTrackCandidate {
    return ImportedTrackCandidate(
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        mediaLocator = buildEmbySongLocator(sourceId, itemId),
        relativePath = relativePath(),
        artworkLocator = coverItemId?.let { buildEmbyCoverLocator(sourceId, it) },
        sizeBytes = sizeBytes,
        bitRate = bitRate,
        channelCount = channelCount,
        samplingRate = samplingRate,
        bitDepth = bitDepth,
    )
}

private fun normalizeEmbyPathSegment(value: String): String {
    return value.trim()
        .replace('/', '／')
        .replace('\\', '／')
        .ifBlank { "未知" }
}

private fun parseEmbyObject(payload: String, context: String): JsonObject {
    return embyJson.parseToJsonElement(payload) as? JsonObject
        ?: throw UiTextException(uiText(Res.string.server_response_not_object, "Emby", context))
}

private fun Throwable.isEmbyTimeoutFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        val message = current.message.orEmpty()
        if (message.contains("timeout", ignoreCase = true) || message.contains("timed out", ignoreCase = true)) {
            return true
        }
        current = current.cause
    }
    return false
}

private fun parseEmbyLyricsObject(
    payload: JsonObject,
    sourceId: String,
    rawPayload: String,
): LyricsDocument? {
    val structured = payload["Lyrics"] ?: payload["lyrics"] ?: payload["Lines"] ?: payload["lines"]
    when (structured) {
        is JsonArray -> parseEmbyLyricsArray(structured, sourceId, rawPayload)?.let { return it }
        is JsonPrimitive -> structured.contentOrNull?.let { return parseCachedLyrics(sourceId, it) }
        is JsonObject -> {
            structured.string("Value")
                ?.takeIf { it.isNotBlank() }
                ?.let { return parseCachedLyrics(sourceId, it) }
        }
        null -> Unit
    }
    firstNonBlank(
        payload.string("Value"),
        payload.string("Text"),
        payload.string("Lyric"),
        payload.string("Lyrics"),
        payload.string("lyrics"),
    )?.let { return parseCachedLyrics(sourceId, it) }
    return null
}

private fun parseEmbyLyricsArray(
    payload: JsonArray,
    sourceId: String,
    rawPayload: String,
): LyricsDocument? {
    val lines = payload.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let { LyricsLine(timestampMs = null, text = it) }

            is JsonObject -> {
                val text = firstNonBlank(
                    element.string("Text"),
                    element.string("Line"),
                    element.string("Value"),
                    element.string("text"),
                ) ?: return@mapNotNull null
                val timestampMs = firstNonNull(
                    element.long("Start"),
                    element.long("StartTicks"),
                    element.long("StartPositionTicks"),
                    element.long("start"),
                )?.let(::embyTicksToMillis)
                    ?: firstNonNull(
                        element.long("StartMs"),
                        element.long("TimeMs"),
                        element.long("TimestampMs"),
                    )
                LyricsLine(timestampMs = timestampMs, text = text)
            }

            else -> null
        }
    }
    if (lines.isEmpty()) return null
    return LyricsDocument(
        lines = lines.sortedBy { it.timestampMs ?: Long.MAX_VALUE },
        sourceId = sourceId,
        rawPayload = rawPayload,
    )
}

private fun embyTicksToMillis(value: Long): Long = value / 10_000L

private fun firstNonBlank(vararg values: String?): String? {
    return values.firstOrNull { !it.isNullOrBlank() }?.trim()
}

private fun <T : Any> firstNonNull(vararg values: T?): T? {
    return values.firstOrNull { it != null }
}

private fun parseEmbyTimestampMillis(value: String): Long? {
    return runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrNull()
}

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asObjectList(): List<JsonObject> {
    return when (val element = this) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(element)
        else -> emptyList()
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

private fun JsonObject.stringArray(key: String): List<String> {
    return when (val element = this[key]) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        is JsonPrimitive -> listOfNotNull(element.contentOrNull?.takeIf(String::isNotBlank))
        else -> emptyList()
    }
}
