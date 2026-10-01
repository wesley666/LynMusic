package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.AudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.parseEmbyCoverLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleCoverLocator
import top.iwesley.lyn.music.domain.readRemotePlaybackUrlCandidateWithFallback

internal class AndroidAudioTagEditorPlatformService(
    context: Context,
    private val activityActions: AndroidActivityActions,
) : AudioTagEditorPlatformService {
    constructor(activity: ComponentActivity) : this(
        context = activity.applicationContext,
        activityActions = FixedAndroidActivityActions(activity, GlobalDiagnosticLogger),
    )

    private val context = context.applicationContext

    override suspend fun pickArtworkBytes(): Result<ByteArray?> = runCatching {
        val uri = activityActions.pickContent("image/*")
        if (uri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                readBytes(context, uri)
            }
        }
    }

    override suspend fun loadArtworkBytes(locator: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
            if (rawTarget.isBlank()) return@runCatching null
            val remoteCoverCandidates = if (
                parseSubsonicCompatibleCoverLocator(rawTarget) != null ||
                parseEmbyCoverLocator(rawTarget) != null
            ) {
                NavidromeLocatorRuntime.resolveCoverArtUrlCandidates(rawTarget).orEmpty()
            } else {
                emptyList()
            }
            val target = remoteCoverCandidates.firstOrNull()?.value ?: rawTarget
            when {
                target.isBlank() -> null
                target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                    loadRemoteArtworkBytes(
                        remoteCoverCandidates.takeIf { it.isNotEmpty() }
                            ?: listOf(RemotePlaybackUrlCandidate(value = target)),
                    )

                target.startsWith("content://", ignoreCase = true) ->
                    readBytes(context, Uri.parse(target))

                else -> {
                    val file = resolveAndroidLocalTrackFile(target)
                        ?: throw UiTextException(uiText(Res.string.artwork_file_unreadable))
                    if (!file.exists()) {
                        throw UiTextException(uiText(Res.string.artwork_file_missing))
                    }
                    file.readBytes()
                }
            }
        }
    }

    private suspend fun loadRemoteArtworkBytes(
        targets: List<RemotePlaybackUrlCandidate>,
    ): ByteArray? {
        val resolved = readRemotePlaybackUrlCandidateWithFallback(
            candidates = targets,
            read = { target -> URL(target.value).openStream().use { input -> input.readBytes() } },
            isValidPayload = ::isCompleteArtworkPayload,
        ) ?: return null
        NavidromeLocatorRuntime.markResolvedUrlSuccess(resolved.first)
        return resolved.second
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw UiTextException(uiText(Res.string.artwork_selection_unreadable))
    }
}
