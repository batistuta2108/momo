package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

enum class PhotoFilterPreset(val label: String) {
    ORIGINAL("Original"),
    CINEMA("Cinéma"),
    NOIR("Noir & Blanc"),
    VINTAGE("Vintage"),
    CYBERPUNK("Cyberpunk"),
    GOLDEN("Doré Chaud"),
    PASTEL("Pastel"),
    HDR("HDR Pro"),
    SEPIA("Sépia"),
    POLAR("Nuit Polaire")
}

enum class PhotoAspectRatio(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    LANDSCAPE("16:9", 16f / 9f),
    VERTICAL("9:16", 9f / 16f)
}

enum class TextPosition(val label: String) {
    TOP("Haut"),
    CENTER("Centre"),
    BOTTOM("Bas")
}

data class PhotoEditState(
    val filter: PhotoFilterPreset = PhotoFilterPreset.ORIGINAL,
    val brightness: Float = 0f,      // -80f .. +80f
    val contrast: Float = 1f,        // 0.5f .. 1.8f
    val saturation: Float = 1f,      // 0f .. 2f
    val warmth: Float = 0f,          // -1f .. +1f
    val vignette: Float = 0f,        // 0f .. 1f
    val aspectRatio: PhotoAspectRatio = PhotoAspectRatio.ORIGINAL,
    val overlayText: String = "",
    val overlayColorHex: Long = 0xFFFFFFFF,
    val overlayPosition: TextPosition = TextPosition.BOTTOM,
    val overlaySizeScale: Float = 1f
)

object PhotoProcessor {

    suspend fun loadBitmapFromUri(context: Context, uri: Uri, maxSide: Int = 1800): Bitmap =
        withContext(Dispatchers.IO) {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            var sampleSize = 1
            val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
            while (longest / sampleSize > maxSide) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: throw IllegalStateException("Impossible de charger la photo sélectionnée.")
        }

    fun applyEdits(source: Bitmap, edits: PhotoEditState): Bitmap {
        val cropped = cropToRatio(source, edits.aspectRatio.ratio)
        val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val matrix = buildColorMatrix(edits)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(cropped, 0f, 0f, paint)

        if (edits.vignette > 0.01f) {
            val cx = out.width / 2f
            val cy = out.height / 2f
            val radius = max(out.width, out.height) * 0.75f
            val alpha = (edits.vignette.coerceIn(0f, 1f) * 210).toInt()
            val grad = RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad }
            canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), vigPaint)
        }

        if (edits.overlayText.isNotBlank()) {
            drawTextOverlay(canvas, out.width, out.height, edits)
        }

        if (cropped !== source) {
            cropped.recycle()
        }
        return out
    }

    fun buildColorMatrix(edits: PhotoEditState): ColorMatrix {
        val result = ColorMatrix()

        // Preset LUT
        val preset = when (edits.filter) {
            PhotoFilterPreset.ORIGINAL -> ColorMatrix()
            PhotoFilterPreset.NOIR -> ColorMatrix().apply { setSaturation(0f) }
            PhotoFilterPreset.CINEMA -> ColorMatrix(
                floatArrayOf(
                    1.15f, -0.05f, 0f, 0f, 8f,
                    0f, 1.04f, 0.05f, 0f, 0f,
                    -0.06f, 0.06f, 1.18f, 0f, -6f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.VINTAGE -> ColorMatrix(
                floatArrayOf(
                    0.95f, 0.15f, 0.08f, 0f, 12f,
                    0.05f, 0.92f, 0.05f, 0f, 8f,
                    0.02f, 0.08f, 0.78f, 0f, 4f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.CYBERPUNK -> ColorMatrix(
                floatArrayOf(
                    1.2f, 0f, 0.25f, 0f, -8f,
                    0f, 0.95f, 0.12f, 0f, 4f,
                    0.22f, 0.1f, 1.35f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.GOLDEN -> ColorMatrix(
                floatArrayOf(
                    1.22f, 0.08f, 0f, 0f, 14f,
                    0.04f, 1.08f, 0f, 0f, 6f,
                    0f, 0f, 0.82f, 0f, -8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.PASTEL -> ColorMatrix(
                floatArrayOf(
                    0.95f, 0.08f, 0.08f, 0f, 22f,
                    0.06f, 0.95f, 0.08f, 0f, 20f,
                    0.08f, 0.06f, 0.98f, 0f, 24f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.HDR -> ColorMatrix(
                floatArrayOf(
                    1.32f, 0f, 0f, 0f, -24f,
                    0f, 1.32f, 0f, 0f, -24f,
                    0f, 0f, 1.32f, 0f, -24f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.SEPIA -> ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.POLAR -> ColorMatrix(
                floatArrayOf(
                    0.88f, 0f, 0.08f, 0f, -4f,
                    0f, 1.02f, 0.10f, 0f, 4f,
                    0.08f, 0.12f, 1.25f, 0f, 16f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        result.postConcat(preset)

        // Saturation
        if (edits.saturation != 1f) {
            val satMatrix = ColorMatrix().apply { setSaturation(edits.saturation.coerceIn(0f, 2f)) }
            result.postConcat(satMatrix)
        }

        // Contrast & Brightness
        val c = edits.contrast.coerceIn(0.4f, 2f)
        val t = (1f - c) * 128f + edits.brightness
        val bcMatrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        result.postConcat(bcMatrix)

        // Warmth
        if (edits.warmth != 0f) {
            val w = edits.warmth * 28f
            val warmMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, w,
                    0f, 1f, 0f, 0f, w * 0.25f,
                    0f, 0f, 1f, 0f, -w,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(warmMatrix)
        }

        return result
    }

    private fun cropToRatio(source: Bitmap, targetRatio: Float?): Bitmap {
        if (targetRatio == null) return source
        val w = source.width
        val h = source.height
        val currentRatio = w.toFloat() / h.toFloat()
        val cropW: Int
        val cropH: Int
        if (currentRatio > targetRatio) {
            cropH = h
            cropW = (h * targetRatio).toInt().coerceIn(1, w)
        } else {
            cropW = w
            cropH = (w / targetRatio).toInt().coerceIn(1, h)
        }
        val x = ((w - cropW) / 2).coerceAtLeast(0)
        val y = ((h - cropH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, x, y, cropW, cropH, Matrix(), true)
    }

    private fun drawTextOverlay(canvas: Canvas, w: Int, h: Int, edits: PhotoEditState) {
        val baseSize = (min(w, h) * 0.068f * edits.overlaySizeScale).coerceAtLeast(22f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = edits.overlayColorHex.toInt()
            textSize = baseSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(baseSize * 0.18f, 0f, baseSize * 0.08f, Color.argb(190, 0, 0, 0))
        }
        val y = when (edits.overlayPosition) {
            TextPosition.TOP -> h * 0.14f + baseSize * 0.4f
            TextPosition.CENTER -> h * 0.52f
            TextPosition.BOTTOM -> h * 0.88f
        }
        canvas.drawText(edits.overlayText, w / 2f, y, paint)
    }

    suspend fun exportBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        title: String
    ): Uri = withContext(Dispatchers.IO) {
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "HydraPhoto" }
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Hydra Studio Bergama")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Impossible de créer le fichier image dans la galerie.")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: throw IllegalStateException("Flux d'écriture image indisponible.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Hydra Studio Bergama")
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            Uri.fromFile(file)
        }
    }
}
