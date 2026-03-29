package top.iwesley.lyn.music.platform

import androidx.room.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.iwesley.lyn.music.buildLynMusicAppComponent
import top.iwesley.lyn.music.core.model.ConsoleDiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.domain.scanNavidromeLibrary
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

fun createIosAppComponent(): top.iwesley.lyn.music.LynMusicAppComponent {
    val database = buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(
            name = documentDirectory() + "/lynmusic.db",
        ),
    )
    val secureStore = IosKeychainCredentialStore()
    val playbackPreferencesStore = IosPlaybackPreferencesStore()
    val navidromeHttpClient = IosLyricsHttpClient()
    return buildLynMusicAppComponent(
        platform = PlatformDescriptor(
            name = "iPhone / iPad",
            capabilities = PlatformCapabilities(
                supportsLocalFolderImport = false,
                supportsSambaImport = false,
                supportsWebDavImport = false,
                supportsNavidromeImport = true,
                supportsSystemMediaControls = false,
            ),
        ),
        database = database,
        importSourceGateway = IosImportSourceGateway(navidromeHttpClient),
        playbackGateway = ApplePlaybackGateway(platformLabel = "iOS"),
        playbackPreferencesStore = playbackPreferencesStore,
        secureCredentialStore = secureStore,
        lyricsHttpClient = navidromeHttpClient,
        artworkCacheStore = createIosArtworkCacheStore(),
        logger = ConsoleDiagnosticLogger(enabled = true, label = "iOS"),
    )
}

private class IosLyricsHttpClient : LyricsHttpClient {
    private val client = HttpClient(Darwin)

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        return runCatching {
            val response = client.request {
                url(request.url)
                this.method = when (request.method) {
                    RequestMethod.GET -> HttpMethod.Get
                    RequestMethod.POST -> HttpMethod.Post
                }
                request.headers.forEach { (key, value) -> headers.append(key, value) }
                request.body?.let { setBody(it) }
            }
            LyricsHttpResponse(
                statusCode = response.status.value,
                body = response.bodyAsText(),
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosKeychainCredentialStore : SecureCredentialStore {
    override suspend fun put(key: String, value: String) {
        val baseQuery = keychainQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to IOS_KEYCHAIN_SERVICE.toCFValue(),
            kSecAttrAccount to key.toCFValue(),
        )
        val updateStatus = SecItemUpdate(
            baseQuery,
            keychainQuery(kSecValueData to value.toKeychainData()),
        )
        when (updateStatus) {
            errSecSuccess -> Unit
            errSecItemNotFound -> {
                val addStatus = SecItemAdd(
                    keychainQuery(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to IOS_KEYCHAIN_SERVICE.toCFValue(),
                        kSecAttrAccount to key.toCFValue(),
                        kSecValueData to value.toKeychainData(),
                        kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                    ),
                    null,
                )
                check(addStatus == errSecSuccess) { "Keychain write failed: $addStatus" }
            }

            else -> error("Keychain update failed: $updateStatus")
        }
    }

    override suspend fun get(key: String): String? {
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(
                keychainQuery(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to IOS_KEYCHAIN_SERVICE.toCFValue(),
                    kSecAttrAccount to key.toCFValue(),
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                ),
                result.ptr,
            )
            when (status) {
                errSecSuccess -> {
                    val released = result.value?.let { CFBridgingRelease(it) } as? NSData
                    released?.toUtf8String()
                }

                errSecItemNotFound -> null
                else -> error("Keychain read failed: $status")
            }
        }
    }

    override suspend fun remove(key: String) {
        val status = SecItemDelete(
            keychainQuery(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to IOS_KEYCHAIN_SERVICE.toCFValue(),
                kSecAttrAccount to key.toCFValue(),
            ),
        )
        if (status != errSecSuccess && status != errSecItemNotFound) {
            error("Keychain delete failed: $status")
        }
    }
}

private class IosPlaybackPreferencesStore : PlaybackPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val mutableUseSambaCache = MutableStateFlow(
        if (defaults.objectForKey(KEY_USE_SAMBA_CACHE) == null) true else defaults.boolForKey(KEY_USE_SAMBA_CACHE),
    )

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        defaults.setBool(enabled, KEY_USE_SAMBA_CACHE)
        mutableUseSambaCache.value = enabled
    }
}

private class IosImportSourceGateway(
    private val navidromeHttpClient: LyricsHttpClient,
) : ImportSourceGateway {
    override suspend fun pickLocalFolder(): LocalFolderSelection? = null

    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        return ImportScanReport(emptyList(), warnings = listOf("当前 iOS 构建未实现应用内目录扫描，请通过 Files 接入后扩展。"))
    }

    override suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport {
        return ImportScanReport(emptyList(), warnings = listOf("当前 iOS 构建建议通过 Files 连接 SMB。"))
    }

    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport {
        return ImportScanReport(emptyList(), warnings = listOf("当前 iOS 构建暂未实现应用内 WebDAV。"))
    }

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        return scanNavidromeLibrary(draft, sourceId, navidromeHttpClient)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val directoryUrl: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(directoryUrl?.path)
}

@OptIn(ExperimentalForeignApi::class)
private fun keychainQuery(vararg pairs: Pair<CFTypeRef?, CFTypeRef?>): CFMutableDictionaryRef? {
    val dictionary = CFDictionaryCreateMutable(null, pairs.size.toLong(), null, null)
    pairs.forEach { (key, value) ->
        CFDictionaryAddValue(dictionary, key, value)
    }
    return dictionary
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toCFValue(): CFTypeRef? = CFBridgingRetain(this)

@OptIn(ExperimentalForeignApi::class)
private fun String.toKeychainData(): CFTypeRef? {
    val bytes = encodeToByteArray()
    return bytes.usePinned { pinned ->
        CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toUtf8String(): String {
    val byteCount = length.toInt()
    if (byteCount == 0) return ""
    val byteArray = ByteArray(byteCount)
    byteArray.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return byteArray.decodeToString()
}

private const val IOS_KEYCHAIN_SERVICE = "top.iwesley.lyn.music.credentials"
private const val KEY_USE_SAMBA_CACHE = "use_samba_cache"
