@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.checkUi
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiTextArgumentException
import top.iwesley.lyn.music.core.model.UiTextUnsupportedException
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVKeyValueStatusLoaded
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.availableMetadataFormats
import platform.AVFoundation.commonKey
import platform.AVFoundation.commonMetadata
import platform.AVFoundation.dataValue
import platform.AVFoundation.key
import platform.AVFoundation.metadataForFormat
import platform.AVFoundation.numberValue
import platform.AVFoundation.stringValue
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.kCFStringEncodingGB_18030_2000
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDirectoryEnumerationSkipsHiddenFiles
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorReadingWithoutChanges
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF16StringEncoding
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkResolutionWithoutImplicitStartAccessing
import platform.Foundation.NSURLContentModificationDateKey
import platform.Foundation.NSURLFileResourceIdentifierKey
import platform.Foundation.NSURLFileSizeKey
import platform.Foundation.NSURLIsDirectoryKey
import platform.Foundation.NSURLIsSymbolicLinkKey
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.NonNavidromeAudioScanResult
import top.iwesley.lyn.music.core.model.SAME_NAME_LRC_MAX_BYTES
import top.iwesley.lyn.music.core.model.SameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildIosLocalFolderReference
import top.iwesley.lyn.music.core.model.buildIosLocalMediaLocator
import top.iwesley.lyn.music.core.model.classifyNonNavidromeAudioFile
import top.iwesley.lyn.music.core.model.parseIosLocalFolderReference
import top.iwesley.lyn.music.core.model.parseIosLocalMediaLocator
import top.iwesley.lyn.music.core.model.sameNameLyricsRelativePath
import top.iwesley.lyn.music.core.model.unsupportedAudioImportFailure
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import kotlin.coroutines.resume
import kotlin.math.roundToLong

internal val IOS_SUPPORTED_IMPORT_AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac")

internal class IosLocalFolderPicker : NSObject(), UIDocumentPickerDelegateProtocol {
    private var continuation: kotlinx.coroutines.CancellableContinuation<LocalFolderSelection?>? = null
    private var picker: UIDocumentPickerViewController? = null

    suspend fun pick(): LocalFolderSelection? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { pending ->
            checkUi(continuation == null) { uiText(Res.string.ios_folder_picker_active) }
            val controller = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeFolder),
                asCopy = false,
            )
            controller.delegate = this@IosLocalFolderPicker
            controller.allowsMultipleSelection = false
            continuation = pending
            picker = controller
            pending.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    if (continuation === pending && isCurrentPickerController(controller)) {
                        controller.dismissViewControllerAnimated(true, completion = null)
                        clearPendingPicker()
                    }
                }
            }
            val presenter = topIosViewController()
            if (presenter == null) {
                clearPendingPicker()
                pending.resumeWith(Result.failure(UiTextException(uiText(Res.string.folder_picker_open_failed))))
            } else {
                presenter.presentViewController(controller, animated = true, completion = null)
            }
        }
    }

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        if (!isCurrentPickerController(controller)) return
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        val result = runCatching { url?.let(::createIosLocalFolderSelection) }
        val pending = continuation
        clearPendingPicker()
        pending?.resumeWith(result)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        if (!isCurrentPickerController(controller)) return
        val pending = continuation
        clearPendingPicker()
        pending?.resume(null)
    }

    private fun isCurrentPickerController(controller: UIDocumentPickerViewController): Boolean {
        return continuation != null && picker?.isEqual(controller) == true
    }

    private fun clearPendingPicker() {
        continuation = null
        picker?.delegate = null
        picker = null
    }
}

private fun topIosViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val window = application.windows
        .filterIsInstance<UIWindow>()
        .firstOrNull { it.isKeyWindow() }
        ?: application.keyWindow
    var controller: UIViewController? = window?.rootViewController ?: return null
    while (true) {
        val presented = controller?.presentedViewController ?: break
        controller = presented
    }
    return controller
}

private fun createIosLocalFolderSelection(url: NSURL): LocalFolderSelection {
    val started = url.startAccessingSecurityScopedResource()
    checkUi(started) { uiText(Res.string.ios_folder_access_failed) }
    return try {
        val normalizedUrl = url.URLByStandardizingPath ?: url
        val identity = normalizedUrl.absoluteString
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: throw UiTextException(uiText(Res.string.folder_selection_unrecognized))
        val bookmark = createIosBookmark(url)
        LocalFolderSelection(
            label = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: "本地音乐",
            persistentReference = buildIosLocalFolderReference(identity, bookmark.toByteArray()),
        )
    } finally {
        url.stopAccessingSecurityScopedResource()
    }
}

private fun createIosBookmark(url: NSURL): NSData = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    url.bookmarkDataWithOptions(
        options = 0u,
        includingResourceValuesForKeys = null,
        relativeToURL = null,
        error = error.ptr,
    ) ?: throw (error.value?.localizedDescription?.let { IllegalStateException(it) }
        ?: UiTextException(uiText(Res.string.ios_folder_bookmark_failed)))
}

internal class IosScopedFolderAccess private constructor(
    val rootUrl: NSURL,
    val refreshedPersistentReference: String?,
) {
    private var released = false

    fun close() {
        if (released) return
        released = true
        rootUrl.stopAccessingSecurityScopedResource()
    }

    companion object {
        fun open(persistentReference: String): IosScopedFolderAccess {
            val reference = parseIosLocalFolderReference(persistentReference)
                ?: throw UiTextException(uiText(Res.string.folder_access_expired))
            return memScoped {
                val stale = alloc<BooleanVar>()
                stale.value = false
                val error = alloc<ObjCObjectVar<NSError?>>()
                val url = NSURL.URLByResolvingBookmarkData(
                    bookmarkData = reference.bookmarkData.toNSData(),
                    options = NSURLBookmarkResolutionWithoutImplicitStartAccessing,
                    relativeToURL = null,
                    bookmarkDataIsStale = stale.ptr,
                    error = error.ptr,
                ) ?: throw (error.value?.localizedDescription?.let { IllegalStateException(it) }
                    ?: UiTextException(uiText(Res.string.ios_folder_permission_expired)))
                checkUi(url.startAccessingSecurityScopedResource()) {
                    uiText(Res.string.ios_folder_access_unavailable)
                }
                val refreshed = if (stale.value) {
                    runCatching {
                        buildIosLocalFolderReference(reference.identity, createIosBookmark(url).toByteArray())
                    }.getOrNull()
                } else {
                    null
                }
                IosScopedFolderAccess(url, refreshed)
            }
        }
    }
}

internal data class IosLocalTrackAccess(
    val url: NSURL,
    private val folderAccess: IosScopedFolderAccess,
) {
    fun close() = folderAccess.close()
}

internal class IosLocalFileAccessService(
    private val database: LynMusicDatabase,
) {
    suspend fun open(locator: String): IosLocalTrackAccess =
        requireNotNull(open(locator, optional = false))

    suspend fun openOptional(locator: String): IosLocalTrackAccess? = open(locator, optional = true)

    private suspend fun open(locator: String, optional: Boolean): IosLocalTrackAccess? {
        val (sourceId, relativePath) = parseIosLocalMediaLocator(locator)
            ?: throw UiTextException(uiText(Res.string.local_track_location_invalid))
        val source = database.importSourceDao().getById(sourceId)
            ?.takeIf { it.type == ImportSourceType.LOCAL_FOLDER.name }
            ?: throw UiTextException(uiText(Res.string.local_track_source_missing))
        val access = IosScopedFolderAccess.open(source.rootReference)
        return try {
            val url = resolveIosChildUrl(access.rootUrl, relativePath)
            val originalPath = url.path
            if (originalPath == null || !NSFileManager.defaultManager.fileExistsAtPath(originalPath)) {
                access.close()
                if (optional) return null
                throw UiTextException(uiText(Res.string.local_track_file_missing))
            }
            val coordinatedUrl = coordinatedReadableUrl(url)
            val path = coordinatedUrl.path
            checkUi(path != null && NSFileManager.defaultManager.fileExistsAtPath(path)) {
                uiText(Res.string.local_track_file_missing)
            }
            IosLocalTrackAccess(coordinatedUrl, access)
        } catch (throwable: Throwable) {
            access.close()
            throw throwable
        }
    }
}

internal class IosLocalFolderScanner {
    suspend fun scan(
        selection: LocalFolderSelection,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport = withContext(Dispatchers.Default) {
        val access = IosScopedFolderAccess.open(selection.persistentReference)
        try {
            val files = enumerateIosFiles(access.rootUrl).sortedBy { it.relativePath }
            val tracks = mutableListOf<ImportedTrackCandidate>()
            val failures = mutableListOf<ImportScanFailure>()
            var discovered = 0
            var supportedDiscovered = 0
            files.forEach { file ->
                when (classifyNonNavidromeAudioFile(file.name, IOS_SUPPORTED_IMPORT_AUDIO_EXTENSIONS)) {
                    NonNavidromeAudioScanResult.NOT_AUDIO -> Unit
                    NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED -> {
                        discovered += 1
                        failures += unsupportedAudioImportFailure(file.relativePath)
                    }

                    NonNavidromeAudioScanResult.IMPORT_SUPPORTED -> {
                        discovered += 1
                        supportedDiscovered += 1
                        runCatching {
                            readIosImportedTrack(file, sourceId)
                        }.onSuccess { candidate ->
                            tracks += candidate
                            progressSink.onProgress(
                                ImportScanProgress(
                                    sourceId = sourceId,
                                    phase = ImportScanPhase.Scanning,
                                    importedTrackCount = tracks.size,
                                    totalTrackCount = null,
                                ),
                            )
                        }.onFailure { throwable ->
                            failures += iosAudioImportFailure(file.relativePath, throwable)
                        }
                    }
                }
            }
            if (supportedDiscovered > 0 && tracks.isEmpty()) {
                throw UiTextException(uiText(Res.string.folder_scan_unreadable_index_preserved))
            }
            ImportScanReport(
                tracks = tracks,
                discoveredAudioFileCount = discovered,
                failures = failures,
                refreshedPersistentReference = access.refreshedPersistentReference,
            )
        } finally {
            access.close()
        }
    }
}

private data class IosEnumeratedFile(
    val url: NSURL,
    val name: String,
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

private data class IosPendingDirectory(
    val url: NSURL,
    val relativePath: String,
)

internal data class IosUrlResourceValues(
    val isDirectory: Boolean?,
    val isSymbolicLink: Boolean,
    val fileResourceIdentifier: Any?,
    val sizeBytes: Long?,
    val modifiedAt: Long?,
)

private val IOS_ENUMERATION_RESOURCE_KEYS = listOfNotNull(
    NSURLIsDirectoryKey,
    NSURLIsSymbolicLinkKey,
    NSURLFileResourceIdentifierKey,
    NSURLFileSizeKey,
    NSURLContentModificationDateKey,
)

internal class IosVisitedDirectories {
    private val fileResourceIdentifiers = mutableSetOf<Any>()
    private val canonicalUrls = mutableSetOf<String>()

    fun markVisited(url: NSURL, fileResourceIdentifier: Any?): Boolean {
        val canonicalUrl = iosCanonicalDirectoryUrl(url)
        if (fileResourceIdentifier != null && fileResourceIdentifier in fileResourceIdentifiers) return false
        if (canonicalUrl in canonicalUrls) return false
        fileResourceIdentifier?.let(fileResourceIdentifiers::add)
        canonicalUrls += canonicalUrl
        return true
    }
}

private fun enumerateIosFiles(rootUrl: NSURL): List<IosEnumeratedFile> {
    coordinatedReadableUrl(rootUrl)
    val output = mutableListOf<IosEnumeratedFile>()
    val pendingDirectories = ArrayDeque<IosPendingDirectory>()
    val visitedDirectories = IosVisitedDirectories()
    pendingDirectories += IosPendingDirectory(rootUrl, "")
    while (pendingDirectories.isNotEmpty()) {
        val directory = pendingDirectories.removeLast()
        val directoryValues = readIosUrlResourceValues(directory.url)
        if (!visitedDirectories.markVisited(directory.url, directoryValues.fileResourceIdentifier)) continue
        val children = coordinatedDirectoryContents(directory.url)
            .filterIsInstance<NSURL>()
            .sortedBy { it.lastPathComponent.orEmpty() }
        children.forEach { child ->
            val name = child.lastPathComponent.orEmpty()
            if (name.isBlank()) return@forEach
            val values = readIosUrlResourceValues(child)
            if (values.isSymbolicLink) return@forEach
            val relativePath = if (directory.relativePath.isBlank()) name else "${directory.relativePath}/$name"
            val path = child.path ?: return@forEach
            val isDirectory = values.isDirectory ?: isIosDirectory(path)
            if (isDirectory) {
                pendingDirectories += IosPendingDirectory(child, relativePath)
            } else {
                val attributes = if (values.sizeBytes == null || values.modifiedAt == null) {
                    NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
                } else {
                    null
                }
                val size = values.sizeBytes
                    ?: (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue
                    ?: 0L
                val modifiedAt = values.modifiedAt
                    ?: iosModifiedAtMillis(attributes?.get(NSFileModificationDate) as? NSDate)
                    ?: 0L
                output += IosEnumeratedFile(child, name, relativePath, size, modifiedAt)
            }
        }
    }
    return output
}

internal fun readIosUrlResourceValues(url: NSURL): IosUrlResourceValues = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val values = url.resourceValuesForKeys(IOS_ENUMERATION_RESOURCE_KEYS, error = error.ptr).orEmpty()
    IosUrlResourceValues(
        isDirectory = NSURLIsDirectoryKey
            ?.let { values[it] as? NSNumber }
            ?.boolValue,
        isSymbolicLink = NSURLIsSymbolicLinkKey
            ?.let { values[it] as? NSNumber }
            ?.boolValue == true,
        fileResourceIdentifier = NSURLFileResourceIdentifierKey?.let(values::get),
        sizeBytes = NSURLFileSizeKey
            ?.let { values[it] as? NSNumber }
            ?.longLongValue,
        modifiedAt = NSURLContentModificationDateKey
            ?.let { values[it] as? NSDate }
            ?.let(::iosModifiedAtMillis),
    )
}

private fun iosCanonicalDirectoryUrl(url: NSURL): String {
    val resolved = url.URLByResolvingSymlinksInPath ?: url
    val standardized = resolved.URLByStandardizingPath ?: resolved
    return standardized.absoluteString ?: standardized.path ?: url.toString()
}

private fun isIosDirectory(path: String): Boolean = memScoped {
    val result = alloc<BooleanVar>()
    NSFileManager.defaultManager.fileExistsAtPath(path, isDirectory = result.ptr) && result.value
}

private fun iosModifiedAtMillis(date: NSDate?): Long? = date
    ?.timeIntervalSinceReferenceDate
    ?.plus(978_307_200.0)
    ?.times(1_000.0)
    ?.roundToLong()

private fun coordinatedDirectoryContents(url: NSURL): List<*> {
    var contents: List<*>? = null
    var accessorFailure: Throwable? = null
    coordinateIosRead(url) { coordinatedUrl ->
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            contents = NSFileManager.defaultManager.contentsOfDirectoryAtURL(
                url = coordinatedUrl,
                includingPropertiesForKeys = IOS_ENUMERATION_RESOURCE_KEYS,
                options = NSDirectoryEnumerationSkipsHiddenFiles,
                error = error.ptr,
            )
            if (contents == null) {
                accessorFailure = error.value?.localizedDescription?.let { IllegalStateException(it) }
                    ?: UiTextException(uiText(Res.string.folder_contents_read_failed))
            }
        }
    }
    accessorFailure?.let { throw it }
    return contents ?: throw UiTextException(uiText(Res.string.folder_contents_read_failed))
}

private fun coordinatedReadableUrl(url: NSURL): NSURL {
    var coordinated: NSURL? = null
    coordinateIosRead(url) { coordinated = it }
    return coordinated ?: throw UiTextException(uiText(Res.string.file_provider_no_readable_files))
}

private fun <T> coordinateIosRead(url: NSURL, accessor: (NSURL) -> T): T {
    var accessorResult: Result<T>? = null
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(
            url = url,
            options = NSFileCoordinatorReadingWithoutChanges,
            error = error.ptr,
        ) { coordinatedUrl ->
            if (coordinatedUrl != null) {
                accessorResult = runCatching { accessor(coordinatedUrl) }
            }
        }
        error.value?.let { errorValue ->
            throw IllegalStateException(errorValue.localizedDescription)
        }
    }
    return accessorResult?.getOrThrow() ?: throw UiTextException(uiText(Res.string.file_provider_no_readable_files))
}

private fun resolveIosChildUrl(rootUrl: NSURL, relativePath: String): NSURL {
    val segments = relativePath.replace('\\', '/').split('/').filter { it.isNotBlank() }
    checkUi(segments.isNotEmpty() && segments.none { it == "." || it == ".." }) {
        uiText(Res.string.ios_local_path_invalid)
    }
    return segments.fold(rootUrl) { current, segment ->
        current.URLByAppendingPathComponent(segment, isDirectory = false)
            ?: throw UiTextException(uiText(Res.string.local_track_path_unresolved))
    }
}

private data class IosAudioMetadata(
    val title: String?,
    val artistName: String?,
    val albumTitle: String?,
    val albumArtist: String?,
    val year: Int?,
    val genre: String?,
    val comment: String?,
    val composer: String?,
    val isCompilation: Boolean,
    val trackNumber: Int?,
    val discNumber: Int?,
    val embeddedLyrics: String?,
    val artworkBytes: ByteArray?,
    val durationMs: Long,
)

private suspend fun readIosImportedTrack(file: IosEnumeratedFile, sourceId: String): ImportedTrackCandidate {
    val metadata = readIosAudioMetadataCoordinated(file.url)
    val locator = buildIosLocalMediaLocator(sourceId, file.relativePath)
    val artworkLocator = metadata?.artworkBytes?.let { bytes ->
        storeIosImportedArtwork("ios-local:$sourceId:${file.relativePath}:${file.modifiedAt}", bytes)
    }
    return ImportedTrackCandidate(
        title = metadata?.title ?: file.name.substringBeforeLast('.', file.name),
        artistName = metadata?.artistName,
        albumTitle = metadata?.albumTitle,
        durationMs = metadata?.durationMs ?: 0L,
        trackNumber = metadata?.trackNumber,
        discNumber = metadata?.discNumber,
        mediaLocator = locator,
        relativePath = file.relativePath,
        artworkLocator = artworkLocator,
        embeddedLyrics = metadata?.embeddedLyrics,
        sizeBytes = file.sizeBytes,
        modifiedAt = file.modifiedAt,
    )
}

private suspend fun readIosAudioMetadataCoordinated(url: NSURL): IosAudioMetadata? =
    withContext(Dispatchers.Default) {
        coordinateIosRead(url) { coordinatedUrl ->
            val path = coordinatedUrl.path
            checkIosAudioFileAvailable(path != null && NSFileManager.defaultManager.fileExistsAtPath(path))
            runCatching {
                runBlocking { readIosAudioMetadata(coordinatedUrl) }
            }.getOrNull()
        }
    }

private suspend fun readIosAudioMetadata(url: NSURL): IosAudioMetadata {
    val asset = AVURLAsset(uRL = url, options = null)
    awaitIosAssetMetadata(asset)
    val items = buildList<AVMetadataItem> {
        addAll(asset.commonMetadata.filterIsInstance<AVMetadataItem>())
        asset.availableMetadataFormats.filterIsInstance<String>().forEach { format ->
            addAll(asset.metadataForFormat(format).filterIsInstance<AVMetadataItem>())
        }
    }.distinctBy { item -> item.identifier.orEmpty() + ":" + item.key.toString() }
    fun descriptor(item: AVMetadataItem): String = buildString {
        append(item.identifier.orEmpty())
        append('/')
        append(item.commonKey.orEmpty())
        append('/')
        append(item.key?.toString().orEmpty())
    }.lowercase()
    fun text(vararg terms: String): String? = items.firstNotNullOfOrNull { item ->
        val key = descriptor(item)
        item.stringValue?.trim()?.takeIf { value -> value.isNotBlank() && terms.any(key::contains) }
    }
    fun number(vararg terms: String): Int? = items.firstNotNullOfOrNull { item ->
        val key = descriptor(item)
        if (terms.none(key::contains)) return@firstNotNullOfOrNull null
        item.numberValue?.intValue?.takeIf { it > 0 }
            ?: item.stringValue?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            ?: item.dataValue?.toByteArray()?.let(::decodeIosPackedTagNumber)
    }
    fun flag(vararg terms: String): Boolean = items.any { item ->
        val key = descriptor(item)
        if (terms.none(key::contains)) return@any false
        item.numberValue?.intValue?.let { it != 0 }
            ?: item.stringValue?.trim()?.let { it == "1" || it.equals("true", ignoreCase = true) }
            ?: item.dataValue?.toByteArray()?.lastOrNull()?.let { it.toInt() != 0 }
            ?: false
    }
    val albumArtist = text("albumartist", "album_artist", "tpe2", "aart")
    val albumTitle = text("albumname", "albumtitle", "talb", "©alb")
    val artist = text("common/artist", "tpe1", "©art")
    val title = text("common/title", "tit2", "©nam")
    val yearText = text("year", "creationdate", "releasedate", "tdrc", "©day")
    val compilation = flag("compilation", "tcmp", "cpil")
    val artwork = items.firstNotNullOfOrNull { item ->
        item.dataValue?.toByteArray()?.takeIf { descriptor(item).contains("artwork") || descriptor(item).contains("covr") }
    }
    val seconds = CMTimeGetSeconds(asset.duration)
    return IosAudioMetadata(
        title = title,
        artistName = artist,
        albumTitle = albumTitle,
        albumArtist = albumArtist,
        year = yearText?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() },
        genre = text("genre", "tcon", "©gen"),
        comment = text("comment", "description", "comm", "©cmt"),
        composer = text("composer", "tcom", "©wrt"),
        isCompilation = compilation,
        trackNumber = number("tracknumber", "trck", "trkn"),
        discNumber = number("discnumber", "tpos", "disk"),
        embeddedLyrics = text("lyrics", "unsynchronizedlyric", "uslt", "©lyr"),
        artworkBytes = artwork,
        durationMs = if (seconds.isFinite() && seconds > 0.0) (seconds * 1_000.0).roundToLong() else 0L,
    )
}

private fun decodeIosPackedTagNumber(bytes: ByteArray): Int? {
    if (bytes.size < 4) return null
    return (((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF))
        .takeIf { it > 0 }
}

private suspend fun awaitIosAssetMetadata(asset: AVURLAsset) = suspendCancellableCoroutine<Unit> { continuation ->
    val keys = listOf("commonMetadata", "availableMetadataFormats", "duration")
    asset.loadValuesAsynchronouslyForKeys(keys) {
        val result = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (asset.statusOfValueForKey("commonMetadata", error = error.ptr) == AVKeyValueStatusLoaded) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(error.value?.localizedDescription ?: "无法读取音频元数据。"))
            }
        }
        if (continuation.isActive) continuation.resumeWith(result)
    }
}

internal class IosAudioTagGateway(
    private val accessService: IosLocalFileAccessService,
) : AudioTagGateway {
    override suspend fun canEdit(track: Track): Boolean =
        parseIosLocalMediaLocator(track.mediaLocator)?.first == track.sourceId

    override suspend fun canWrite(track: Track): Boolean = false

    override suspend fun read(track: Track): Result<AudioTagSnapshot> = runCatching {
        val access = accessService.open(track.mediaLocator)
        try {
            val metadata = readIosAudioMetadataCoordinated(access.url)
            val artworkLocator = metadata?.artworkBytes?.let { bytes ->
                storeIosImportedArtwork("ios-local:${track.sourceId}:${track.relativePath}:${track.modifiedAt}", bytes)
            }
            AudioTagSnapshot(
                title = metadata?.title ?: track.title,
                artistName = metadata?.artistName ?: track.artistName,
                albumTitle = metadata?.albumTitle ?: track.albumTitle,
                albumArtist = metadata?.albumArtist,
                year = metadata?.year,
                genre = metadata?.genre,
                comment = metadata?.comment,
                composer = metadata?.composer,
                isCompilation = metadata?.isCompilation ?: false,
                tagLabel = "AVFoundation",
                tagLabelText = uiText(Res.string.tag_format_read_only, "AVFoundation"),
                trackNumber = metadata?.trackNumber ?: track.trackNumber,
                discNumber = metadata?.discNumber ?: track.discNumber,
                embeddedLyrics = metadata?.embeddedLyrics,
                artworkLocator = artworkLocator ?: track.artworkLocator,
            )
        } finally {
            access.close()
        }
    }

    override suspend fun write(track: Track, patch: AudioTagPatch): Result<AudioTagSnapshot> =
        Result.failure(UiTextUnsupportedException(uiText(Res.string.ios_files_tag_writing_unsupported)))
}

internal class IosSameNameLyricsFileGateway(
    private val accessService: IosLocalFileAccessService,
) : SameNameLyricsFileGateway {
    override suspend fun readSameNameLyrics(track: Track): Result<String?> = runCatching {
        val lyricsRelativePath = sameNameLyricsRelativePath(track.relativePath) ?: return@runCatching null
        val lyricsLocator = buildIosLocalMediaLocator(track.sourceId, lyricsRelativePath)
        val access = accessService.openOptional(lyricsLocator) ?: return@runCatching null
        try {
            val bytes = readIosCoordinatedBytesUpTo(access.url, SAME_NAME_LRC_MAX_BYTES)
                ?.takeIf { it.isNotEmpty() }
                ?: return@runCatching null
            decodeIosLyricsBytes(bytes)
        } finally {
            access.close()
        }
    }
}

private fun readIosCoordinatedBytesUpTo(url: NSURL, maxBytes: Long): ByteArray? {
    var bytes: ByteArray? = null
    coordinateIosRead(url) { coordinatedUrl ->
        bytes = readIosFileBytesUpTo(coordinatedUrl, maxBytes)
    }
    return bytes
}

private fun decodeIosLyricsBytes(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val data = bytes.toNSData()
    val encodings = listOf(
        NSUTF8StringEncoding,
        NSUTF16StringEncoding,
        CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingGB_18030_2000.toUInt()),
    )
    return encodings.firstNotNullOfOrNull { encoding ->
        NSString.create(data = data, encoding = encoding)
            ?.toString()
            ?.removePrefix("\uFEFF")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

internal class IosAppleLocalMediaAccessResolver(
    private val accessService: IosLocalFileAccessService,
) : AppleLocalMediaAccessResolver {
    override suspend fun resolve(locator: String): AppleLocalMediaAccess? {
        if (parseIosLocalMediaLocator(locator) == null) return null
        val access = accessService.open(locator)
        val fileUrl = access.url.absoluteString
        if (fileUrl.isNullOrBlank()) {
            access.close()
            throw UiTextException(uiText(Res.string.local_track_file_url_invalid))
        }
        return AppleLocalMediaAccess(
            locator = AppleResolvedMediaLocator.FileUrl(fileUrl),
            release = access::close,
        )
    }
}
