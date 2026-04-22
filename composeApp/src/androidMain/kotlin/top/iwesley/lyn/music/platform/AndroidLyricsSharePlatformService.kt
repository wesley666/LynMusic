package top.iwesley.lyn.music.platform

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
import kotlin.math.max
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareArtworkTintSpec
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsShareFontKind
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.argbWithAlpha
import top.iwesley.lyn.music.core.model.buildLyricsShareTitleArtistLine
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme
import kotlin.coroutines.resume

class AndroidLyricsSharePlatformService(
    activity: ComponentActivity,
    private val fontLibraryPlatformService: LyricsShareFontLibraryPlatformService =
        UnsupportedLyricsShareFontLibraryPlatformService,
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
            val importedTypeface = resolveAndroidLyricsShareImportedTypeface(fontLibraryPlatformService, model.fontKey)
            renderLyricsShareBitmap(model, artwork, importedTypeface)
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

    override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val importedFonts = fontLibraryPlatformService.listImportedFonts().getOrDefault(emptyList())
                    .filter { it.kind == LyricsShareFontKind.IMPORTED }
                importedFonts + listOf(
                    LyricsShareFontOption(
                        fontKey = "Serif",
                        displayName = "Serif",
                        previewText = DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT,
                    ),
                )
            }
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
        val artworkBitmap = runCatching {
            val normalized = locator?.trim().orEmpty()
            if (normalized.isBlank()) return@runCatching null
            val target = artworkCacheStore.cache(normalized, normalized).orEmpty()
            if (target.isBlank()) return@runCatching null
            when {
                target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                    URL(target).openStream().use(BitmapFactory::decodeStream)

                target.startsWith("file://", ignoreCase = true) ->
                    BitmapFactory.decodeFile(runCatching { File(URI(target)).absolutePath }.getOrElse { target.removePrefix("file://") })

                else -> BitmapFactory.decodeFile(target)
            }
        }.getOrNull()
        return artworkBitmap ?: loadBundledDefaultCoverBytes()?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    private fun renderLyricsShareBitmap(
        model: LyricsShareCardModel,
        artworkBitmap: Bitmap?,
        importedTypeface: Typeface?,
    ): ByteArray {
        return when (model.template) {
            LyricsShareTemplate.NOTE -> renderNoteLyricsShareBitmap(model, artworkBitmap, importedTypeface)
            LyricsShareTemplate.ARTWORK_TINT -> renderArtworkTintLyricsShareBitmap(model, artworkBitmap, importedTypeface)
        }
    }

    private fun renderNoteLyricsShareBitmap(
        model: LyricsShareCardModel,
        artworkBitmap: Bitmap?,
        importedTypeface: Typeface?,
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
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.TEXT_FOOTER_ARGB
            textSize = LyricsShareCardSpec.TITLE_FONT_SIZE_PX
            isFakeBoldText = false
            typeface = importedTypeface
        }
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareCardSpec.TEXT_SECONDARY_ARGB
            textSize = LyricsShareCardSpec.BRAND_FONT_SIZE_PX
            textAlign = Paint.Align.LEFT
            typeface = importedTypeface
        }

        val availableWidth = width - LyricsShareCardSpec.OUTER_PADDING_PX * 2 - LyricsShareCardSpec.PAPER_PADDING_HORIZONTAL_PX * 2
        val titleLayout = createTextLayout(
            text = buildLyricsShareTitleArtistLine(model.title, model.artistName),
            paint = titlePaint,
            widthPx = availableWidth,
            alignment = Layout.Alignment.ALIGN_NORMAL,
            lineSpacingExtraPx = 0f,
        )
        val fixedHeight =
            LyricsShareCardSpec.OUTER_PADDING_PX * 2 +
                LyricsShareCardSpec.SHADOW_OFFSET_PX +
                LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                LyricsShareCardSpec.FOOTER_TOP_GAP_PX +
                titleLayout.height +
                LyricsShareCardSpec.BRAND_TOP_GAP_PX +
                brandPaint.textSize.toInt() +
                LyricsShareCardSpec.PAPER_PADDING_BOTTOM_PX
        val fittedLyrics = fitAndroidLyricsLayout(
            text = model.lyricsLines.joinToString("\n"),
            widthPx = availableWidth,
            textColor = LyricsShareCardSpec.TEXT_PRIMARY_ARGB,
            baseFontSizePx = LyricsShareCardSpec.LYRICS_FONT_SIZE_PX,
            baseLineSpacingExtraPx = LyricsShareCardSpec.LYRICS_ANDROID_LINE_GAP_PX,
            maxTotalHeight = LyricsShareCardSpec.IMAGE_MAX_HEIGHT_PX,
            fixedHeight = fixedHeight,
            minFontScale = LyricsShareCardSpec.LYRICS_MIN_FONT_SCALE,
            shrinkStep = LyricsShareCardSpec.LYRICS_FONT_SHRINK_STEP,
            typeface = importedTypeface,
        )

        val contentHeight =
            LyricsShareCardSpec.PAPER_PADDING_TOP_PX +
                LyricsShareCardSpec.ARTWORK_SIZE_PX +
                LyricsShareCardSpec.LYRICS_TOP_GAP_PX +
                fittedLyrics.layout.height +
                LyricsShareCardSpec.FOOTER_TOP_GAP_PX +
                titleLayout.height +
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
        fittedLyrics.layout.draw(canvas)
        canvas.restore()
        cursorY += fittedLyrics.layout.height + LyricsShareCardSpec.FOOTER_TOP_GAP_PX

        canvas.save()
        canvas.translate(artworkLeft, cursorY)
        titleLayout.draw(canvas)
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

    private fun renderArtworkTintLyricsShareBitmap(
        model: LyricsShareCardModel,
        artworkBitmap: Bitmap?,
        importedTypeface: Typeface?,
    ): ByteArray {
        val width = LyricsShareArtworkTintSpec.IMAGE_WIDTH_PX
        val theme = model.artworkTintTheme ?: sampleArtworkTintTheme(artworkBitmap)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB
        }
        val topGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX.toFloat(),
                intArrayOf(
                    argbWithAlpha(
                        theme?.innerGlowColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
                        if (theme != null) 0.22f else 0f,
                    ),
                    argbWithAlpha(
                        theme?.glowColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
                        if (theme != null) 0.18f else 0f,
                    ),
                    0x00000000,
                ),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val accentGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX * 0.22f,
                width.toFloat(),
                LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX.toFloat(),
                intArrayOf(
                    0x00000000,
                    argbWithAlpha(
                        theme?.rimColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
                        if (theme != null) 0.12f else 0f,
                    ),
                    0x00000000,
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareArtworkTintSpec.PLACEHOLDER_ARGB
        }
        val artworkShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareArtworkTintSpec.ARTWORK_SHADOW_ARGB
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareArtworkTintSpec.TEXT_FOOTER_ARGB
            textSize = LyricsShareArtworkTintSpec.TITLE_FONT_SIZE_PX
            isFakeBoldText = false
            typeface = importedTypeface
        }
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricsShareArtworkTintSpec.TEXT_SECONDARY_ARGB
            textSize = LyricsShareArtworkTintSpec.BRAND_FONT_SIZE_PX
            textAlign = Paint.Align.LEFT
            typeface = importedTypeface
        }

        val availableWidth = width - LyricsShareArtworkTintSpec.OUTER_PADDING_PX * 2
        val titleLayout = createTextLayout(
            text = buildLyricsShareTitleArtistLine(model.title, model.artistName),
            paint = titlePaint,
            widthPx = availableWidth,
            alignment = Layout.Alignment.ALIGN_NORMAL,
            lineSpacingExtraPx = 0f,
        )
        val fixedHeight =
            LyricsShareArtworkTintSpec.OUTER_PADDING_PX +
                LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX +
                LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX +
                titleLayout.height +
                LyricsShareArtworkTintSpec.BRAND_TOP_GAP_PX +
                brandPaint.textSize.toInt() +
                LyricsShareArtworkTintSpec.OUTER_PADDING_PX
        val fittedLyrics = fitAndroidLyricsLayout(
            text = model.lyricsLines.joinToString("\n"),
            widthPx = availableWidth,
            textColor = LyricsShareArtworkTintSpec.TEXT_PRIMARY_ARGB,
            baseFontSizePx = LyricsShareArtworkTintSpec.LYRICS_FONT_SIZE_PX,
            baseLineSpacingExtraPx = LyricsShareArtworkTintSpec.LYRICS_ANDROID_LINE_GAP_PX,
            maxTotalHeight = LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX,
            fixedHeight = fixedHeight,
            minFontScale = LyricsShareArtworkTintSpec.LYRICS_MIN_FONT_SCALE,
            shrinkStep = LyricsShareArtworkTintSpec.LYRICS_FONT_SHRINK_STEP,
            typeface = importedTypeface,
        )
        val contentHeight =
            LyricsShareArtworkTintSpec.OUTER_PADDING_PX +
                LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX +
                LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX +
                LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX +
                fittedLyrics.layout.height +
                LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX +
                titleLayout.height +
                LyricsShareArtworkTintSpec.BRAND_TOP_GAP_PX +
                brandPaint.textSize.toInt() +
                LyricsShareArtworkTintSpec.OUTER_PADDING_PX
        val height = contentHeight
            .coerceIn(LyricsShareArtworkTintSpec.IMAGE_MIN_HEIGHT_PX, LyricsShareArtworkTintSpec.IMAGE_MAX_HEIGHT_PX)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), topGradientPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), accentGradientPaint)

        val artworkLeft = LyricsShareArtworkTintSpec.OUTER_PADDING_PX.toFloat()
        val artworkTop = (LyricsShareArtworkTintSpec.OUTER_PADDING_PX + LyricsShareArtworkTintSpec.ARTWORK_TOP_GAP_PX).toFloat()
        val shadowRect = RectF(
            artworkLeft,
            artworkTop + 10f,
            artworkLeft + LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX,
            artworkTop + 10f + LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX,
        )
        canvas.drawRoundRect(
            shadowRect,
            LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
            LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
            artworkShadowPaint,
        )
        val artworkRect = RectF(
            artworkLeft,
            artworkTop,
            artworkLeft + LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX,
            artworkTop + LyricsShareArtworkTintSpec.ARTWORK_SIZE_PX,
        )
        val artworkPath = Path().apply {
            addRoundRect(
                artworkRect,
                LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
                LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
                Path.Direction.CW,
            )
        }
        canvas.save()
        canvas.clipPath(artworkPath)
        if (artworkBitmap != null) {
            canvas.drawBitmap(artworkBitmap, null, artworkRect, Paint(Paint.ANTI_ALIAS_FLAG))
        } else {
            canvas.drawRoundRect(
                artworkRect,
                LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
                LyricsShareArtworkTintSpec.ARTWORK_RADIUS_PX.toFloat(),
                placeholderPaint,
            )
        }
        canvas.restore()

        var cursorY = artworkRect.bottom + LyricsShareArtworkTintSpec.LYRICS_TOP_GAP_PX
        canvas.save()
        canvas.translate(artworkLeft, cursorY)
        fittedLyrics.layout.draw(canvas)
        canvas.restore()
        cursorY += fittedLyrics.layout.height + LyricsShareArtworkTintSpec.FOOTER_TOP_GAP_PX

        canvas.save()
        canvas.translate(artworkLeft, cursorY)
        titleLayout.draw(canvas)
        canvas.restore()

        val brandText = LyricsShareCardSpec.BRAND_TEXT
        val brandBaseline = height - LyricsShareArtworkTintSpec.OUTER_PADDING_PX.toFloat()
        val brandX = width / 2f - brandPaint.measureText(brandText) / 2f
        canvas.drawText(brandText, brandX, brandBaseline, brandPaint)

        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }
}

private fun sampleArtworkTintTheme(artworkBitmap: Bitmap?): top.iwesley.lyn.music.core.model.ArtworkTintTheme? {
    if (artworkBitmap == null) return null
    val stepX = max(1, artworkBitmap.width / 24)
    val stepY = max(1, artworkBitmap.height / 24)
    return deriveArtworkTintTheme(
        buildList {
            for (y in 0 until artworkBitmap.height step stepY) {
                for (x in 0 until artworkBitmap.width step stepX) {
                    add(artworkBitmap.getPixel(x, y))
                }
            }
        },
    )
}

private data class AndroidFittedLyricsLayout(
    val layout: StaticLayout,
)

private fun fitAndroidLyricsLayout(
    text: String,
    widthPx: Int,
    textColor: Int,
    baseFontSizePx: Float,
    baseLineSpacingExtraPx: Float,
    maxTotalHeight: Int,
    fixedHeight: Int,
    minFontScale: Float,
    shrinkStep: Float,
    typeface: Typeface?,
): AndroidFittedLyricsLayout {
    var fontSizePx = baseFontSizePx
    var lineSpacingExtraPx = baseLineSpacingExtraPx
    val minFontSizePx = (baseFontSizePx * minFontScale).coerceAtLeast(1f)
    while (true) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = fontSizePx
            isFakeBoldText = false
            this.typeface = typeface
        }
        val layout = createTextLayout(
            text = text,
            paint = paint,
            widthPx = widthPx,
            alignment = Layout.Alignment.ALIGN_NORMAL,
            lineSpacingExtraPx = lineSpacingExtraPx,
        )
        if (fixedHeight + layout.height <= maxTotalHeight || fontSizePx <= minFontSizePx) {
            return AndroidFittedLyricsLayout(layout = layout)
        }
        val nextFontSizePx = (fontSizePx * shrinkStep).coerceAtLeast(minFontSizePx)
        if (nextFontSizePx == fontSizePx) {
            return AndroidFittedLyricsLayout(layout = layout)
        }
        fontSizePx = nextFontSizePx
        lineSpacingExtraPx = (lineSpacingExtraPx * shrinkStep).coerceAtLeast(0f)
    }
}

private suspend fun resolveAndroidLyricsShareImportedTypeface(
    fontLibraryPlatformService: LyricsShareFontLibraryPlatformService,
    fontKey: String?,
): Typeface? {
    val normalizedFontKey = fontKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val fontPath = fontLibraryPlatformService.resolveImportedFontPath(normalizedFontKey).getOrNull()
        ?: return null
    return runCatching { Typeface.createFromFile(fontPath) }.getOrNull()
}

private fun createTextLayout(
    text: String,
    paint: TextPaint,
    widthPx: Int,
    alignment: Layout.Alignment,
    lineSpacingExtraPx: Float,
): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
        .setAlignment(alignment)
        .setIncludePad(false)
        .setLineSpacing(lineSpacingExtraPx, 1f)
        .build()
}

private fun ensurePngFileName(name: String): String {
    val normalized = name.trim().ifBlank { "lynmusic-lyrics-share.png" }
    return if (normalized.endsWith(".png", ignoreCase = true)) normalized else "$normalized.png"
}
