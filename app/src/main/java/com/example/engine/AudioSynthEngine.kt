package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

enum class SynthWaveform(val label: String) {
    SINE("Sinus"),
    TRIANGLE("Triangle"),
    SQUARE("Carré"),
    SAW("Dent de scie")
}

enum class MusicalScale(val label: String, val offsets: IntArray) {
    MINOR("Mineure", intArrayOf(0, 2, 3, 5, 7, 8, 10, 12)),
    MAJOR("Majeure", intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)),
    PHRYGIAN("Trap / Phrygienne", intArrayOf(0, 1, 4, 5, 7, 8, 10, 12)),
    PENTATONIC("Afro Penta", intArrayOf(0, 3, 5, 7, 10, 12, 15, 17))
}

enum class TrackType(val displayName: String) {
    KICK("Kick"),
    SNARE("Snare"),
    HIHAT("Hi-Hat"),
    CLAP("Clap"),
    BASS("Basse"),
    LEAD("Synthé")
}

data class BeatPreset(
    val name: String,
    val bpm: Int,
    val scale: MusicalScale,
    val waveform: SynthWaveform,
    val grid: Map<TrackType, List<Boolean>>
)

object BeatPresets {
    val all = listOf(
        BeatPreset(
            name = "Trap 808",
            bpm = 138,
            scale = MusicalScale.PHRYGIAN,
            waveform = SynthWaveform.SAW,
            grid = mapOf(
                TrackType.KICK to listOf(true, false, false, false, false, false, true, false, false, false, true, false, false, false, false, false),
                TrackType.SNARE to listOf(false, false, false, false, false, false, false, false, true, false, false, false, false, false, false, false),
                TrackType.HIHAT to listOf(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true),
                TrackType.CLAP to listOf(false, false, false, false, false, false, false, false, true, false, false, false, false, false, true, false),
                TrackType.BASS to listOf(true, false, false, false, false, false, true, false, false, false, true, false, false, true, false, false),
                TrackType.LEAD to listOf(true, false, false, true, false, false, true, false, false, true, false, false, true, false, true, false)
            )
        ),
        BeatPreset(
            name = "Lo-Fi Chill",
            bpm = 84,
            scale = MusicalScale.MINOR,
            waveform = SynthWaveform.TRIANGLE,
            grid = mapOf(
                TrackType.KICK to listOf(true, false, false, false, false, false, false, true, false, false, true, false, false, false, false, false),
                TrackType.SNARE to listOf(false, false, false, false, true, false, false, false, false, false, false, false, true, false, false, false),
                TrackType.HIHAT to listOf(true, false, true, false, true, false, true, false, true, false, true, false, true, false, true, true),
                TrackType.CLAP to listOf(false, false, false, false, false, false, false, false, false, false, false, false, true, false, false, false),
                TrackType.BASS to listOf(true, false, false, false, false, false, false, true, false, false, true, false, false, false, true, false),
                TrackType.LEAD to listOf(true, false, false, true, false, true, false, false, true, false, false, true, false, false, true, false)
            )
        ),
        BeatPreset(
            name = "Afro Groove",
            bpm = 112,
            scale = MusicalScale.PENTATONIC,
            waveform = SynthWaveform.SINE,
            grid = mapOf(
                TrackType.KICK to listOf(true, false, false, true, false, false, true, false, true, false, false, true, false, false, true, false),
                TrackType.SNARE to listOf(false, false, false, false, true, false, false, true, false, false, false, false, true, false, false, false),
                TrackType.HIHAT to listOf(true, false, true, true, false, true, true, false, true, false, true, true, false, true, true, false),
                TrackType.CLAP to listOf(false, false, false, false, true, false, false, false, false, false, false, false, true, false, true, false),
                TrackType.BASS to listOf(true, false, false, true, false, false, true, false, false, false, true, false, false, true, false, false),
                TrackType.LEAD to listOf(true, false, true, false, false, true, false, true, true, false, false, true, false, true, false, false)
            )
        ),
        BeatPreset(
            name = "Synthwave",
            bpm = 124,
            scale = MusicalScale.MINOR,
            waveform = SynthWaveform.SQUARE,
            grid = mapOf(
                TrackType.KICK to listOf(true, false, false, false, true, false, false, false, true, false, false, false, true, false, false, false),
                TrackType.SNARE to listOf(false, false, false, false, true, false, false, false, false, false, false, false, true, false, false, false),
                TrackType.HIHAT to listOf(false, false, true, false, false, false, true, false, false, false, true, false, false, false, true, true),
                TrackType.CLAP to listOf(false, false, false, false, true, false, false, false, false, false, false, false, true, false, false, false),
                TrackType.BASS to listOf(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true),
                TrackType.LEAD to listOf(true, false, true, false, true, false, true, false, false, true, false, true, false, true, true, false)
            )
        ),
        BeatPreset(
            name = "Vierge",
            bpm = 120,
            scale = MusicalScale.MINOR,
            waveform = SynthWaveform.TRIANGLE,
            grid = TrackType.entries.associateWith { List(16) { false } }
        )
    )
}

class AudioSynthEngine {

    companion object {
        const val SAMPLE_RATE = 44100
    }

    private var playbackJob: Job? = null
    private var liveNoteJob: Job? = null

    @Volatile
    private var currentGrid: Map<TrackType, List<Boolean>> = BeatPresets.all.first().grid

    @Volatile
    private var currentMuted: Set<TrackType> = emptySet()

    @Volatile
    private var currentBpm: Int = 138

    @Volatile
    private var currentSwing: Float = 0.08f

    @Volatile
    private var currentWaveform: SynthWaveform = SynthWaveform.SAW

    @Volatile
    private var currentScale: MusicalScale = MusicalScale.PHRYGIAN

    @Volatile
    private var currentVolume: Float = 0.85f

    fun updateParams(
        grid: Map<TrackType, List<Boolean>>,
        muted: Set<TrackType>,
        bpm: Int,
        swing: Float,
        waveform: SynthWaveform,
        scale: MusicalScale,
        volume: Float
    ) {
        currentGrid = grid
        currentMuted = muted
        currentBpm = bpm
        currentSwing = swing
        currentWaveform = waveform
        currentScale = scale
        currentVolume = volume
    }

    fun startLoop(
        scope: CoroutineScope,
        onStepChanged: (Int) -> Unit
    ) {
        stopLoop()
        playbackJob = scope.launch(Dispatchers.Default) {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            try {
                track.play()
                var step = 0
                while (isActive) {
                    val bpm = currentBpm.coerceIn(60, 200)
                    val baseStepSamples = (SAMPLE_RATE * 60.0 / bpm / 4.0).toInt()
                    val swingShift = if (step % 2 == 1) {
                        (baseStepSamples * (1f + currentSwing * 0.45f)).toInt()
                    } else {
                        (baseStepSamples * (1f - currentSwing * 0.45f)).toInt()
                    }.coerceAtLeast(1000)

                    withContext(Dispatchers.Main) {
                        onStepChanged(step)
                    }

                    val pcm = synthesizeStep(
                        stepIndex = step,
                        sampleCount = swingShift,
                        grid = currentGrid,
                        muted = currentMuted,
                        waveform = currentWaveform,
                        scale = currentScale,
                        masterVolume = currentVolume
                    )
                    track.write(pcm, 0, pcm.size)
                    step = (step + 1) % 16
                }
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    fun stopLoop() {
        playbackJob?.cancel()
        playbackJob = null
    }

    fun playLiveKey(scope: CoroutineScope, midiNote: Int, waveform: SynthWaveform, volume: Float) {
        liveNoteJob?.cancel()
        liveNoteJob = scope.launch(Dispatchers.Default) {
            val durationSamples = (SAMPLE_RATE * 0.45).toInt()
            val freq = 440.0 * 2.0.pow((midiNote - 69) / 12.0)
            val buffer = ShortArray(durationSamples)
            for (i in 0 until durationSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = (1.0 - exp(-t * 160.0)) * exp(-t * 6.5)
                val osc = sampleOscillator(waveform, freq, t)
                val sample = tanh(osc * env * volume * 0.85) * Short.MAX_VALUE
                buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(buffer.size * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            try {
                track.write(buffer, 0, buffer.size)
                track.play()
                kotlinx.coroutines.delay(460)
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    suspend fun exportWavToMediaStore(
        context: Context,
        title: String,
        grid: Map<TrackType, List<Boolean>>,
        muted: Set<TrackType>,
        bpm: Int,
        swing: Float,
        waveform: SynthWaveform,
        scale: MusicalScale,
        volume: Float,
        barsCount: Int
    ): Uri = withContext(Dispatchers.IO) {
        val totalSteps = (barsCount.coerceAtLeast(1)) * 16
        val pcmStream = ByteArrayOutputStream()
        val baseStepSamples = (SAMPLE_RATE * 60.0 / bpm.coerceIn(60, 200) / 4.0).toInt()

        for (s in 0 until totalSteps) {
            val stepIndex = s % 16
            val stepSamples = if (stepIndex % 2 == 1) {
                (baseStepSamples * (1f + swing * 0.45f)).toInt()
            } else {
                (baseStepSamples * (1f - swing * 0.45f)).toInt()
            }.coerceAtLeast(1000)

            val shorts = synthesizeStep(
                stepIndex = stepIndex,
                sampleCount = stepSamples,
                grid = grid,
                muted = muted,
                waveform = waveform,
                scale = scale,
                masterVolume = volume
            )
            val byteBuf = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in shorts) {
                byteBuf.putShort(sample)
            }
            pcmStream.write(byteBuf.array())
        }

        val pcmData = pcmStream.toByteArray()
        val wavHeader = buildWavHeader(pcmData.size, SAMPLE_RATE, 1, 16)
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "HydraBeat" }
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.wav"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Hydra Studio Bergama")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Impossible de créer le fichier audio dans MediaStore.")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(wavHeader)
                out.write(pcmData)
            } ?: throw IllegalStateException("Flux d'écriture audio indisponible.")
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Hydra Studio Bergama")
            dir.mkdirs()
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { out ->
                out.write(wavHeader)
                out.write(pcmData)
            }
            MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf("audio/wav"), null)
            Uri.fromFile(outFile)
        }
    }

    private fun synthesizeStep(
        stepIndex: Int,
        sampleCount: Int,
        grid: Map<TrackType, List<Boolean>>,
        muted: Set<TrackType>,
        waveform: SynthWaveform,
        scale: MusicalScale,
        masterVolume: Float
    ): ShortArray {
        val output = ShortArray(sampleCount)
        val hasKick = !muted.contains(TrackType.KICK) && (grid[TrackType.KICK]?.getOrNull(stepIndex) == true)
        val hasSnare = !muted.contains(TrackType.SNARE) && (grid[TrackType.SNARE]?.getOrNull(stepIndex) == true)
        val hasHihat = !muted.contains(TrackType.HIHAT) && (grid[TrackType.HIHAT]?.getOrNull(stepIndex) == true)
        val hasClap = !muted.contains(TrackType.CLAP) && (grid[TrackType.CLAP]?.getOrNull(stepIndex) == true)
        val hasBass = !muted.contains(TrackType.BASS) && (grid[TrackType.BASS]?.getOrNull(stepIndex) == true)
        val hasLead = !muted.contains(TrackType.LEAD) && (grid[TrackType.LEAD]?.getOrNull(stepIndex) == true)

        val bassDegrees = intArrayOf(0, 0, 3, 4, 0, 2, 5, 4)
        val bassNote = 36 + scale.offsets[bassDegrees[stepIndex / 2 % bassDegrees.size] % scale.offsets.size]
        val bassFreq = 440.0 * 2.0.pow((bassNote - 69) / 12.0)

        val leadDegrees = intArrayOf(0, 2, 4, 5, 7, 4, 3, 2, 0, 4, 6, 4, 2, 5, 3, 1)
        val leadNote = 60 + scale.offsets[leadDegrees[stepIndex % leadDegrees.size] % scale.offsets.size]
        val leadFreq = 440.0 * 2.0.pow((leadNote - 69) / 12.0)

        var seed = (stepIndex + 1) * 7919 + 131
        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            var mix = 0.0

            // Fast deterministic LCG noise
            seed = seed * 1664525 + 1013904223
            val noise = ((seed shr 16) and 0xFFFF) / 32768.0 - 1.0

            if (hasKick) {
                val freqSweep = 44.0 + 115.0 * exp(-t * 36.0)
                val kickEnv = exp(-t * 11.0)
                mix += sin(2.0 * PI * freqSweep * t) * kickEnv * 0.95
            }

            if (hasSnare) {
                val body = sin(2.0 * PI * 185.0 * t) * exp(-t * 28.0) * 0.45
                val snap = noise * exp(-t * 20.0) * 0.55
                mix += body + snap
            }

            if (hasHihat) {
                val metallic = (noise - 0.5 * sin(2.0 * PI * 7200.0 * t)) * exp(-t * 68.0) * 0.35
                mix += metallic
            }

            if (hasClap) {
                val burst = when {
                    t < 0.012 -> 1.0
                    t < 0.024 -> 0.8
                    t < 0.036 -> 0.65
                    else -> exp(-(t - 0.036) * 26.0)
                }
                mix += noise * burst * 0.50
            }

            if (hasBass) {
                val bassEnv = (1.0 - exp(-t * 140.0)) * exp(-t * 5.2)
                val bassOsc = 0.7 * sin(2.0 * PI * bassFreq * t) + 0.3 * sin(4.0 * PI * bassFreq * t)
                mix += bassOsc * bassEnv * 0.65
            }

            if (hasLead) {
                val leadEnv = (1.0 - exp(-t * 120.0)) * exp(-t * 8.5)
                val leadOsc = sampleOscillator(waveform, leadFreq, t)
                mix += leadOsc * leadEnv * 0.42
            }

            val limited = tanh(mix * masterVolume) * Short.MAX_VALUE
            output[i] = limited.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return output
    }

    private fun sampleOscillator(waveform: SynthWaveform, freq: Double, t: Double): Double {
        val phase = (freq * t) % 1.0
        return when (waveform) {
            SynthWaveform.SINE -> sin(2.0 * PI * phase)
            SynthWaveform.SQUARE -> if (phase < 0.5) 0.75 else -0.75
            SynthWaveform.SAW -> (2.0 * phase - 1.0) * 0.75
            SynthWaveform.TRIANGLE -> if (phase < 0.5) (4.0 * phase - 1.0) else (3.0 - 4.0 * phase)
        }
    }

    private fun buildWavHeader(
        pcmDataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + pcmDataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmDataSize)
        return buffer.array()
    }
}
