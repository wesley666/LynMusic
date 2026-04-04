package top.iwesley.lyn.music.platform

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URL
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import kotlin.coroutines.resume

class AndroidLyricsSharePlatformService(
    activity: ComponentActivity,
) : LyricsSharePlatformService {
    private val context = activity.applicationContext
    private val artworkCacheStore = createAndroidArtworkCacheStore(context)
    private var pendingPermissionContinuation: CancellableContinuation<Boolean>? = null
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingPermissionContinuation?.resume(granted)
        pendingPermissionContinuation = null
    }

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val artwork = loadArtworkBitmap(model.artworkLocator)
            renderLyricsShareBitmap(model, artwork)
        }
    }

    override suspend fun saveImage(
        pngBytes: ByteArray,
        suggestedName: String,
    ): Result<LyricsShareSaveResult> {
        return runCatching {
            if (!ensureWritePermissionIfNeeded()) {
                error("没有相册写入权限。")
            }
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveImageToMediaStore(pngBytes, suggestedName)
                } else {
                    saveImageLegacy(pngBytes, suggestedName)
                }
            }
            LyricsShareSaveResult(message = "图片已保存到相册")
        }
    }

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(File(context.cacheDir, "lyrics-share").apply { mkdirs() }, "lyrics-share-copy.png")
            file.writeBytes(pngBytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.lyricsshare.fileprovider",
                file,
            )
            val clipboard = ContextCompat.getSystemService(context, android.content.ClipboardManager::class.java)
                ?: error("系统剪贴板不可用。")
            clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "歌词分享图片", uri))
        }
    }

    private suspend fun ensureWritePermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                pendingPermissionContinuation = continuation
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun saveImageToMediaStore(pngBytes: ByteArray, suggestedName: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, ensurePngFileName(suggestedName))
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LynMusic")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建图片文件。")
        try {
            resolver.openOutputStream(uri)?.use { output -> output.write(pngBytes) }
                ?: error("无法写入图片文件。")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (throwable: Throwable) {
            resolver.delete(uri, null, null)
            throw throwable
        }
    }

    @Suppress("DEPRECATION")
    private fun saveImageLegacy(pngBytes: ByteArray, suggestedName: String) {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "LynMusic",
        ).apply { mkdirs() }
        val output = File(directory, ensurePngFileName(suggestedName))
        output.writeBytes(pngBytes)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(output.absolutePath),
            arrayOf("image/png"),
            null,
        )
    }

    private suspend fun loadArtworkBitmap(locator: String?): Bitmap? {
        val normalized = locator?.trim().orEmpty()
        if (normalized.isBlank()) return null
        val target = artworkCacheStore.cache(normalized, normalized).orEmpty()
        if (target.isBlank()) return null
        return runCatching {
            when {
                target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                    URL(target).openStream().use(BitmapFactory::decodeStream)

                target.startsWith("file://", ignoreCase = true) ->
                    BitmapFactory.decodeFile(runCatching { File(URI(target)).absolutePath }.getOrElse { target.removePrefix("file://") })

                else -> BitmapFactory.decodeFile(target)
            }
        }.getOrNull()
    }

    private fun renderLyricsShareBitmap(
        model: LyricsShareCardModel,
        artworkBitmap: Bitmap?,
    ): ByteArray {
        val width = LyricsShareCardSpec.IMAGE_WIDTH_PX
        val canvasPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.CANVAS_BACKGROUND_ARGB
        }
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.PAPER_BACKGROUND_ARGB
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.PAPER_SHADOW_ARGB
        }
        val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.TAPE_ARGB
        }
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.PLACEHOLDER_ARGB
        }
        val lyricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.TEXT_PRIMARY_ARGB
            textSize = LyricsShareCardSpec.LYRICS_FONT_SIZE_PX
            isFakeBoldText = true
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.TEXT_PRIMARY_ARGB
            textSize = LyricsShareCardSpec.TITLE_FONT_SIZE_PX
            isFakeBoldText = true
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.TEXT_SECONDARY_ARGB
            textSize = LyricsShareCardSpec.META_FONT_SIZE_PX
        }
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.TEXT_SECONDARY_ARGB
            textSize = LyricsShareCardSpec.BRAND_FONT_SIZE_PX
            textAlign = Paint.Align.LEFT
        }

        val availableWidth = width - LyricsShareCardSpec.OUTER_PADDING_PX * 2 - LyricsShareCardSpec.PAPER_PADDING_HORIZONTAL_PX * 2
        val lyricsLayout = createTextLayout(
            text = model.lyricsLines.joinToString("\n"),
            paint = lyricsPaint,
            widthPx = availableWidth,
            alignment = Layout.Alignment.ALIGN_NORMAL,
        )
        val titleLayout = createTextLayout(
            text = model.title.ifBlank { "当前歌曲" },
            paint = titlePaint,
            widthPx = availableWidth,
            alignment = Layout.Alignment.ALIGN_NORMAL,
        )
        val artistText = model.artistName?.ifBlank { "未知艺人" } ?: "未知艺人"
        val artistLayout = createTextLayout(
            text = artistText,
            paint = artistPaint,
            widthPx = availableWidth,
            alignment = Layout.Alignment.ALIGN_NORMAL,
        )

        val contentHeight =
            LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                lyricsLayout.height +
                LyricsShareCardSpec.FOOTER_TOP_GAP_PX +
                titleLayout.height +
                artistLayout.height +
                LyricsShareCardSpec.BRAND_TOP_GAP_PX +
                brandPaint.textSize.toInt() +
                LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX
        val height = (contentHeight + LyricsShareCardSpec.OUTER_PADDING_PX * 2 + LyricsShareCardSpec.SHADOW_OFFSET_PX)
            .coerceIn(LyricsShareCardSpec.IMAGE_MIN_HEIGHT_PX, LyricsShareCardSpec.IMAGE_MAX_HEIGHT_PX)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), canvasPaint)

        val paperLeft = LyricsShareCardSpec.OUTER_PADDING_PX.toFloat()
        val paperTop = LyricsShareCardSpec.OUTER_PADDING_PX.toFloat()
        val paperRight = (width - LyricsShareCardSpec.OUTER_PADDING_PX).toFloat()
        val paperBottom = (height - LyricsShareCardSpec.OUTER_PADDING_PX - LyricsShareCardSpec.SHADOW_OFFSET_PX).toFloat()
        val paperRect = RectF(paperLeft, paperTop, paperRight, paperBottom)
        val shadowRect = RectF(
            paperRect.left,
            paperRect.top + LyricsShareCardSpec.SHADOW_OFFSET_PX,
            paperRect.right,
            paperRect.bottom + LyricsShareCardSpec.SHADOW_OFFSET_PX,
        )

        canvas.drawRoundRect(
            shadowRect,
            LyricsShareCardSpec.PAPER_RADIUS_PX.toFloat(),
            LyricsShareCardSpec.PAPER_RADIUS_PX.toFloat(),
            shadowPaint,
        )
        canvas.drawRoundRect(
            paperRect,
            LyricsShareCardSpec.PAPER_RADIUS_PX.toFloat(),
            LyricsShareCardSpec.PAPER_RADIUS_PX.toFloat(),
            paperPaint,
        )

        val tapeLeft = paperRect.left + 40f
        val tapeTop = paperRect.top - 10f
        canvas.save()
        canvas.rotate(-8f, tapeLeft + LyricsShareCardSpec.TAPE_WIDTH_PX / 2f, tapeTop + LyricsShareCardSpec.TAPE_HEIGHT_PX / 2f)
        canvas.drawRoundRect(
            RectF(
                tapeLeft,
                tapeTop,
                tapeLeft + LyricsShareCardSpec.TAPE_WIDTH_PX,
                tapeTop + LyricsShareCardSpec.TAPE_HEIGHT_PX,
            ),
            12f,
            12f,
            tapePaint,
        )
        canvas.restore()

        val artworkLeft = paperRect.left + LyricsShareCardSpec.PAPER_PADDING_HORIZONTAL_PX
        val artworkTop = paperRect.top + LyricsShareCardSpec.PAPER_PADDING_TOP_PX
        val artworkRect = RectF(
            artworkLeft,
            artworkTop,
            artworkLeft + LyricsShareCardSpec.ARTWORK_SIZE_PX,
            artworkTop + LyricsShareCardSpec.ARTWORK_SIZE_PX,
        )
        val artworkPath = Path().apply {
            addRoundRect(artworkRect, 32f, 32f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(artworkPath)
        if (artworkBitmap != null) {
            canvas.drawBitmap(artworkBitmap, null, artworkRect, Paint(Paint.ANTI_ALIAS_FLAG))
        } else {
            canvas.drawRoundRect(artworkRect, 32f, 32f, placeholderPaint)
        }
        canvas.restore()

        var cursorY = artworkRect.bottom + LyricsShareCardSpec.LYRICS_TOP_GAP_PX
        canvas.save()
        canvas.translate(artworkLeft, cursorY)
        lyricsLayout.draw(canvas)
        canvas.restore()
        cursorY += lyricsLayout.height + LyricsShareCardSpec.FOOTER_TOP_GAP_PX

        canvas.save()
        canvas.translate(artworkLeft, cursorY)
        titleLayout.draw(canvas)
        canvas.restore()
        cursorY += titleLayout.height.toFloat()

        canvas.save()
        canvas.translate(artworkLeft, cursorY)
        artistLayout.draw(canvas)
        canvas.restore()

        val brandText = LyricsShareCardSpec.BRAND_TEXT
        val brandBaseline = paperRect.bottom - LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX - brandPaint.fontMetrics.descent
        val brandX = paperRect.centerX() - brandPaint.measureText(brandText) / 2f
        canvas.drawText(brandText, brandX, brandBaseline, brandPaint)

        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }
}

private fun createTextLayout(
    text: String,
    paint: TextPaint,
    widthPx: Int,
    alignment: Layout.Alignment,
): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
        .setAlignment(alignment)
        .setIncludePad(false)
        .setLineSpacing(10f, 1f)
        .build()
}

private fun ensurePngFileName(name: String): String {
    val normalized = name.trim().ifBlank { "lynmusic-lyrics-share.png" }
    return if (normalized.endsWith(".png", ignoreCase = true)) normalized else "$normalized.png"
}
