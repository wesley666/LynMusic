package top.iwesley.lyn.music.platform

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocalCastProxyResource private constructor(
    private val context: Context,
    private val file: File?,
    private val uri: Uri?,
    override val mimeType: String,
) : AndroidCastProxyResource {
    override val length: Long? by lazy {
        when {
            file != null -> file.length().takeIf { it >= 0L }
            uri != null -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L }
            }
            else -> null
        }
    }

    override suspend fun open(start: Long, length: Long?): InputStream = withContext(Dispatchers.IO) {
        val stream = when {
            file != null -> FileInputStream(file)
            uri != null -> context.contentResolver.openInputStream(uri)
                ?: error("无法打开本地音频。")
            else -> error("本地音频不可用。")
        }
        if (start > 0L) {
            stream.skipFully(start)
        }
        stream
    }

    companion object {
        fun fromFile(
            context: Context,
            file: File,
            mimeType: String,
        ): LocalCastProxyResource? {
            return file.takeIf { it.isFile && it.canRead() }?.let {
                LocalCastProxyResource(
                    context = context.applicationContext,
                    file = it,
                    uri = null,
                    mimeType = mimeType,
                )
            }
        }

        fun fromUri(
            context: Context,
            uri: Uri,
            mimeType: String,
        ): LocalCastProxyResource {
            return LocalCastProxyResource(
                context = context.applicationContext,
                file = null,
                uri = uri,
                mimeType = mimeType,
            )
        }
    }
}
