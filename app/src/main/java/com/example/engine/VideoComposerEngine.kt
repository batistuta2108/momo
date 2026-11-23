package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

private const val TAG = "VideoComposer"

enum class VideoTransition(val label: String) {
    FADE("Fondu"),
    ZOOM("Zoom Cinéma"),
    SLIDE("Glissement"),
    CUT("Coupe Franche")
}

enum class VideoAspectRatio(val label: String, val width: Int, val height: Int) {
    PORTRAIT_9_16("9:16 Reels", 720, 1280),
    LANDSCAPE_16_9("16:9 Cinéma", 1280, 720),
    SQUARE_1_1("1:1 Carré", 720, 720)
}

data class VideoClipItem(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean,
    val durationSec: Int = 4,
    val transition: VideoTransition = VideoTransition.FADE,
    val filter: PhotoFilterPreset = PhotoFilterPreset.ORIGINAL,
    val caption: String = ""
)

object VideoComposerEngine {

    suspend fun loadClipFrame(
        context: Context,
        clip: VideoClipItem,
        maxSide: Int = 1280
    ): Bitmap = withContext(Dispatchers.IO) {
        if (clip.isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, clip.uri)
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                frame ?: throw IllegalStateException("Impossible d'extraire une image de la vidéo.")
            } finally {
                runCatching { retriever.release() }
            }
        } else {
            PhotoProcessor.loadBitmapFromUri(context, clip.uri, maxSide)
        }
    }

    fun renderTimelineFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        clips: List<VideoClipItem>,
        bitmaps: Map<String, Bitmap>,
        positionMs: Long
    ) {
        canvas.drawColor(Color.rgb(10, 11, 16))
        if (clips.isEmpty()) return

        var elapsed = 0L
        var activeIdx = clips.lastIndex
        var localProgress = 1f

        for (i in clips.indices) {
            val clipMs = clips[i].durationSec * 1000L
            if (positionMs < elapsed + clipMs || i == clips.lastIndex) {
                activeIdx = i
                localProgress = ((positionMs - elapsed).toFloat() / clipMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                break
            }
            elapsed += clipMs
        }

        val currentClip = clips[activeIdx]
        val currentBmp = bitmaps[currentClip.id] ?: return
        val prevBmp = if (activeIdx > 0) bitmaps[clips[activeIdx - 1].id] else null

        val transitionWindow = 0.22f
        val inTransition = activeIdx > 0 && localProgress < transitionWindow && currentClip.transition != VideoTransition.CUT
        val tNorm = if (inTransition) (localProgress / transitionWindow).coerceIn(0f, 1f) else 1f

        when {
            inTransition && currentClip.transition == VideoTransition.FADE && prevBmp != null -> {
                drawClipBitmap(canvas, width, height, prevBmp, clips[activeIdx - 1].filter, 1f, 0f, 255)
                drawClipBitmap(canvas, width, height, currentBmp, currentClip.filter, 1f, 0f, (tNorm * 255).toInt())
            }
            inTransition && currentClip.transition == VideoTransition.SLIDE && prevBmp != null -> {
                drawClipBitmap(canvas, width, height, prevBmp, clips[activeIdx - 1].filter, 1f, -width * tNorm, 255)
                drawClipBitmap(canvas, width, height, currentBmp, currentClip.filter, 1f, width * (1f - tNorm), 255)
            }
            currentClip.transition == VideoTransition.ZOOM -> {
                val scale = 1f + localProgress * 0.14f
                val alpha = if (inTransition) (tNorm * 255).toInt() else 255
                if (inTransition && prevBmp != null) {
                    drawClipBitmap(canvas, width, height, prevBmp, clips[activeIdx - 1].filter, 1.14f, 0f, 255)
                }
                drawClipBitmap(canvas, width, height, currentBmp, currentClip.filter, scale, 0f, alpha)
            }
            else -> {
                drawClipBitmap(canvas, width, height, currentBmp, currentClip.filter, 1f, 0f, 255)
            }
        }

        if (currentClip.caption.isNotBlank()) {
            drawVideoCaption(canvas, width, height, currentClip.caption)
        }
    }

    private fun drawClipBitmap(
        canvas: Canvas,
        cw: Int,
        ch: Int,
        bmp: Bitmap,
        filter: PhotoFilterPreset,
        zoomScale: Float,
        offsetX: Float,
        alpha: Int
    ) {
        val scale = max(cw.toFloat() / bmp.width, ch.toFloat() / bmp.height) * zoomScale
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val left = (cw - drawW) / 2f + offsetX
        val top = (ch - drawH) / 2f
        val dst = RectF(left, top, left + drawW, top + drawH)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha.coerceIn(0, 255)
            if (filter != PhotoFilterPreset.ORIGINAL) {
                colorFilter = ColorMatrixColorFilter(
                    PhotoProcessor.buildColorMatrix(PhotoEditState(filter = filter))
                )
            }
        }
        canvas.drawBitmap(bmp, null, dst, paint)
    }

    private fun drawVideoCaption(canvas: Canvas, width: Int, height: Int, caption: String) {
        val textSizePx = (min(width, height) * 0.052f).coerceAtLeast(24f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textWidth = textPaint.measureText(caption)
        val padH = textSizePx * 0.85f
        val padV = textSizePx * 0.45f
        val centerY = height * 0.84f
        val rect = RectF(
            (width - textWidth) / 2f - padH,
            centerY - textSizePx - padV * 0.4f,
            (width + textWidth) / 2f + padH,
            centerY + padV
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 10, 12, 18)
        }
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)
        canvas.drawText(caption, width / 2f, centerY, textPaint)
    }

    suspend fun exportMp4ToMediaStore(
        context: Context,
        title: String,
        clips: List<VideoClipItem>,
        bitmaps: Map<String, Bitmap>,
        aspectRatio: VideoAspectRatio,
        audioUri: Uri?,
        onProgress: (Float) -> Unit
    ): Uri = withContext(Dispatchers.IO) {
        require(clips.isNotEmpty()) { "Ajoutez au moins un média avant d'exporter." }

        val fps = 24
        val width = aspectRatio.width
        val height = aspectRatio.height
        val totalDurationMs = clips.sumOf { it.durationSec * 1000L }.coerceAtLeast(1000L)
        val totalFrames = ((totalDurationMs * fps) / 1000L).toInt().coerceAtLeast(1)

        val tempVideoFile = File(context.cacheDir, "hydra_export_${System.currentTimeMillis()}.mp4")
        val videoOnlyFile = File(context.cacheDir, "hydra_vonly_${System.currentTimeMillis()}.mp4")

        encodeVideoTrack(
            outputFile = videoOnlyFile,
            width = width,
            height = height,
            fps = fps,
            totalFrames = totalFrames,
            clips = clips,
            bitmaps = bitmaps,
            onProgress = { p -> onProgress(if (audioUri != null) p * 0.82f else p) }
        )

        val finalSourceFile = if (audioUri != null) {
            val muxed = tryMuxAudioWithVideo(context, videoOnlyFile, audioUri, tempVideoFile)
            onProgress(0.95f)
            if (muxed) tempVideoFile else videoOnlyFile
        } else {
            videoOnlyFile
        }

        val safeTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "HydraReel" }
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.mp4"

        val resultUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Hydra Studio Bergama")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Impossible de créer le fichier vidéo dans la galerie.")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(finalSourceFile).use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Flux d'écriture vidéo indisponible.")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Hydra Studio Bergama")
            dir.mkdirs()
            val outFile = File(dir, fileName)
            finalSourceFile.copyTo(outFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf("video/mp4"), null)
            Uri.fromFile(outFile)
        }

        runCatching { videoOnlyFile.delete() }
        runCatching { tempVideoFile.delete() }
        onProgress(1f)
        resultUri
    }

    private fun encodeVideoTrack(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        totalFrames: Int,
        clips: List<VideoClipItem>,
        bitmaps: Map<String, Bitmap>,
        onProgress: (Float) -> Unit
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 3_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBitmap)
        val argbPixels = IntArray(width * height)

        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        var submittedFrames = 0
        var inputDone = false
        var outputDone = false

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        if (submittedFrames >= totalFrames) {
                            encoder.queueInputBuffer(
                                inIdx, 0, 0,
                                (submittedFrames * 1_000_000L) / fps,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val posMs = (submittedFrames * 1000L) / fps
                            renderTimelineFrame(frameCanvas, width, height, clips, bitmaps, posMs)
                            frameBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)

                            val image = encoder.getInputImage(inIdx)
                            if (image != null) {
                                writeArgbToYuv420Image(argbPixels, width, height, image)
                                image.close()
                                val yuvSize = width * height * 3 / 2
                                encoder.queueInputBuffer(
                                    inIdx, 0, yuvSize,
                                    (submittedFrames * 1_000_000L) / fps,
                                    0
                                )
                            } else {
                                val inBuf = encoder.getInputBuffer(inIdx)!!
                                val bytesWritten = writeArgbToPackedI420(argbPixels, width, height, inBuf)
                                encoder.queueInputBuffer(
                                    inIdx, 0, bytesWritten,
                                    (submittedFrames * 1_000_000L) / fps,
                                    0
                                )
                            }
                            submittedFrames++
                            if (submittedFrames % 4 == 0 || submittedFrames == totalFrames) {
                                onProgress(submittedFrames.toFloat() / totalFrames.toFloat())
                            }
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        if (outBuf != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            frameBitmap.recycle()
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun tryMuxAudioWithVideo(
        context: Context,
        videoFile: File,
        audioUri: Uri,
        outFile: File
    ): Boolean {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            var vTrack = -1
            var vFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    vTrack = i
                    vFormat = fmt
                    break
                }
            }
            if (vTrack < 0 || vFormat == null) return false
            val videoDurationUs = if (vFormat.containsKey(MediaFormat.KEY_DURATION)) {
                vFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                Long.MAX_VALUE
            }

            audioExtractor.setDataSource(context, audioUri, null)
            var aTrack = -1
            var aFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime == MediaFormat.MIMETYPE_AUDIO_AAC || mime == "audio/mp4a-latm") {
                    aTrack = i
                    aFormat = fmt
                    break
                }
            }

            if (aTrack < 0 || aFormat == null) {
                // Audio is likely WAV or MP3; skip direct mux if not AAC to keep MP4 container valid
                return false
            }

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxVideoTrack = muxer.addTrack(vFormat)
            val muxAudioTrack = muxer.addTrack(aFormat)
            muxer.start()

            val buf = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            videoExtractor.selectTrack(vTrack)
            while (true) {
                val size = videoExtractor.readSampleData(buf, 0)
                if (size < 0) break
                info.set(0, size, videoExtractor.sampleTime, videoExtractor.sampleFlags)
                muxer.writeSampleData(muxVideoTrack, buf, info)
                videoExtractor.advance()
            }

            audioExtractor.selectTrack(aTrack)
            while (true) {
                val size = audioExtractor.readSampleData(buf, 0)
                if (size < 0) break
                val pts = audioExtractor.sampleTime
                if (pts > videoDurationUs) break
                info.set(0, size, pts, audioExtractor.sampleFlags)
                muxer.writeSampleData(muxAudioTrack, buf, info)
                audioExtractor.advance()
            }

            muxer.stop()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Audio mux skipped, using video-only MP4", e)
            false
        } finally {
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
            runCatching { muxer?.release() }
        }
    }

    private fun writeArgbToYuv420Image(argb: IntArray, width: Int, height: Int, image: Image) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (j in 0 until height) {
            val yRowOffset = j * yRowStride
            val uvRowOffsetU = (j shr 1) * uRowStride
            val uvRowOffsetV = (j shr 1) * vRowStride
            for (i in 0 until width) {
                val px = argb[j * width + i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(yRowOffset + i * yPixelStride, y.coerceIn(0, 255).toByte())

                if ((j and 1) == 0 && (i and 1) == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvCol = i shr 1
                    uBuf.put(uvRowOffsetU + uvCol * uPixelStride, u.coerceIn(0, 255).toByte())
                    vBuf.put(uvRowOffsetV + uvCol * vPixelStride, v.coerceIn(0, 255).toByte())
                }
            }
        }
    }

    private fun writeArgbToPackedI420(argb: IntArray, width: Int, height: Int, out: ByteBuffer): Int {
        out.clear()
        val ySize = width * height
        val uvSize = ySize / 4
        var yIdx = 0
        var uIdx = ySize
        var vIdx = ySize + uvSize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val px = argb[j * width + i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF

                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                out.put(yIdx++, y.toByte())

                if ((j and 1) == 0 && (i and 1) == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    out.put(uIdx++, u.toByte())
                    out.put(vIdx++, v.toByte())
                }
            }
        }
        return ySize + uvSize * 2
    }
}
