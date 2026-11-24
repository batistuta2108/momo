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

        fun resample24kTo44k(pcm24kLe: ByteArray): ShortArray {
            if (pcm24kLe.size < 2) return ShortArray(0)
            val srcCount = pcm24kLe.size / 2
            val src = ShortArray(srcCount)
            ByteBuffer.wrap(pcm24kLe).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(src)

            val ratio = SAMPLE_RATE.toDouble() / 24000.0
            val dstCount = (srcCount * ratio).toInt().coerceAtLeast(1)
            val dst = ShortArray(dstCount)
            for (i in 0 until dstCount) {
                val srcPos = i / ratio
                val idx = srcPos.toInt().coerceIn(0, srcCount - 1)
                val nextIdx = (idx + 1).coerceIn(0, srcCount - 1)
                val frac = srcPos - idx
                val sample = src[idx] * (1.0 - frac) + src[nextIdx] * frac
                dst[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            return dst
        }
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

            val track = createStreamTrack(minBuf * 2)

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

    fun startFullSong(
        scope: CoroutineScope,
        song: SunoSongTrack,
        masterVolume: Float,
        startFraction: Float = 0f,
        onProgress: (posMs: Long, totalMs: Long, sectionIndex: Int, stepInBar: Int) -> Unit,
        onFinished: () -> Unit
    ) {
        stopLoop()
        playbackJob = scope.launch(Dispatchers.Default) {
            val sections = song.sections.ifEmpty {
                listOf(SongSection("Arrangement", 8, 0.9f, song.fullLyrics))
            }
            val totalBars = sections.sumOf { it.bars.coerceAtLeast(1) }.coerceAtLeast(1)
            val totalSteps = totalBars * 16
            val bpm = song.bpm.coerceIn(60, 200)
            val baseStepSamples = (SAMPLE_RATE * 60.0 / bpm / 4.0).toInt()
            val totalSamplesEstimate = totalSteps.toLong() * baseStepSamples
            val totalMs = (totalSamplesEstimate * 1000L) / SAMPLE_RATE

            val timelineSteps = buildSectionTimeline(sections)
            val startStep = (startFraction.coerceIn(0f, 0.98f) * timelineSteps.size).toInt()
                .coerceIn(0, (timelineSteps.size - 1).coerceAtLeast(0))

            // Find sample offset where vocal track starts (first Couplet or Refrain, or bar 2)
            val vocalStartStep = timelineSteps.indexOfFirst { (secIdx, _) ->
                val secName = sections[secIdx].name.lowercase()
                "couplet" in secName || "refrain" in secName || "chorus" in secName || "thème" in secName
            }.let { if (it >= 0) it else 32.coerceAtMost(timelineSteps.size / 4) }

            val vocalPcm = if (!song.isInstrumental) song.vocalPcm44k else null
            var globalSampleCursor = startStep.toLong() * baseStepSamples
            val vocalStartSample = vocalStartStep.toLong() * baseStepSamples

            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = createStreamTrack(minBuf * 2)
            try {
                track.play()
                for (stepIdx in startStep until timelineSteps.size) {
                    if (!isActive) break
                    val (secIndex, barInTrack) = timelineSteps[stepIdx]
                    val section = sections[secIndex]
                    val stepInBar = stepIdx % 16

                    val stepSamples = if (stepInBar % 2 == 1) {
                        (baseStepSamples * (1f + song.swing * 0.45f)).toInt()
                    } else {
                        (baseStepSamples * (1f - song.swing * 0.45f)).toInt()
                    }.coerceAtLeast(1000)

                    val posMs = (globalSampleCursor * 1000L) / SAMPLE_RATE
                    withContext(Dispatchers.Main) {
                        onProgress(posMs.coerceAtMost(totalMs), totalMs, secIndex, stepInBar)
                    }

                    val chordName = if (song.chords.isNotEmpty()) {
                        song.chords[barInTrack % song.chords.size]
                    } else {
                        "Am"
                    }

                    val pcm = synthesizeArrangedStep(
                        stepInBar = stepInBar,
                        sampleCount = stepSamples,
                        grid = song.grid,
                        waveform = song.waveform,
                        scale = song.scale,
                        chordName = chordName,
                        section = section,
                        masterVolume = masterVolume,
                        vocalPcm = vocalPcm,
                        vocalSampleOffset = globalSampleCursor - vocalStartSample
                    )
                    track.write(pcm, 0, pcm.size)
                    globalSampleCursor += stepSamples
                }
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onFinished()
                    }
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
            writeShortsToStream(shorts, pcmStream)
        }

        saveWavBytesToMediaStore(context, title, pcmStream.toByteArray())
    }

    suspend fun exportFullSongWavToMediaStore(
        context: Context,
        song: SunoSongTrack,
        masterVolume: Float
    ): Uri = withContext(Dispatchers.IO) {
        val sections = song.sections.ifEmpty {
            listOf(SongSection("Arrangement", 8, 0.9f, song.fullLyrics))
        }
        val timelineSteps = buildSectionTimeline(sections)
        val bpm = song.bpm.coerceIn(60, 200)
        val baseStepSamples = (SAMPLE_RATE * 60.0 / bpm / 4.0).toInt()

        val vocalStartStep = timelineSteps.indexOfFirst { (secIdx, _) ->
            val secName = sections[secIdx].name.lowercase()
            "couplet" in secName || "refrain" in secName || "chorus" in secName || "thème" in secName
        }.let { if (it >= 0) it else 32.coerceAtMost(timelineSteps.size / 4) }

        val vocalPcm = if (!song.isInstrumental) song.vocalPcm44k else null
        var globalSampleCursor = 0L
        val vocalStartSample = vocalStartStep.toLong() * baseStepSamples

        val pcmStream = ByteArrayOutputStream()
        for (stepIdx in timelineSteps.indices) {
            val (secIndex, barInTrack) = timelineSteps[stepIdx]
            val section = sections[secIndex]
            val stepInBar = stepIdx % 16
            val stepSamples = if (stepInBar % 2 == 1) {
                (baseStepSamples * (1f + song.swing * 0.45f)).toInt()
            } else {
                (baseStepSamples * (1f - song.swing * 0.45f)).toInt()
            }.coerceAtLeast(1000)

            val chordName = if (song.chords.isNotEmpty()) {
                song.chords[barInTrack % song.chords.size]
            } else {
                "Am"
            }

            val shorts = synthesizeArrangedStep(
                stepInBar = stepInBar,
                sampleCount = stepSamples,
                grid = song.grid,
                waveform = song.waveform,
                scale = song.scale,
                chordName = chordName,
                section = section,
                masterVolume = masterVolume,
                vocalPcm = vocalPcm,
                vocalSampleOffset = globalSampleCursor - vocalStartSample
            )
            writeShortsToStream(shorts, pcmStream)
            globalSampleCursor += stepSamples
        }

        saveWavBytesToMediaStore(context, song.title, pcmStream.toByteArray())
    }

    private fun saveWavBytesToMediaStore(
        context: Context,
        title: String,
        pcmData: ByteArray
    ): Uri {
        val wavHeader = buildWavHeader(pcmData.size, SAMPLE_RATE, 1, 16)
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "HydraBeat" }
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.wav"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    private fun writeShortsToStream(shorts: ShortArray, stream: ByteArrayOutputStream) {
        val byteBuf = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in shorts) {
            byteBuf.putShort(sample)
        }
        stream.write(byteBuf.array())
    }

    private fun createStreamTrack(bufSize: Int): AudioTrack {
        return AudioTrack.Builder()
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
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun buildSectionTimeline(sections: List<SongSection>): List<Pair<Int, Int>> {
        val steps = mutableListOf<Pair<Int, Int>>()
        var barCounter = 0
        sections.forEachIndexed { secIdx, sec ->
            val bars = sec.bars.coerceAtLeast(1)
            for (b in 0 until bars) {
                for (s in 0 until 16) {
                    steps.add(secIdx to barCounter)
                }
                barCounter++
            }
        }
        return steps
    }

    private fun synthesizeArrangedStep(
        stepInBar: Int,
        sampleCount: Int,
        grid: Map<TrackType, List<Boolean>>,
        waveform: SynthWaveform,
        scale: MusicalScale,
        chordName: String,
        section: SongSection,
        masterVolume: Float,
        vocalPcm: ShortArray?,
        vocalSampleOffset: Long
    ): ShortArray {
        val output = ShortArray(sampleCount)
        val e = section.energy.coerceIn(0.3f, 1.0f)

        val hasKick = section.drumsEnabled && (grid[TrackType.KICK]?.getOrNull(stepInBar) == true)
        val hasSnare = section.drumsEnabled && e >= 0.6f && (grid[TrackType.SNARE]?.getOrNull(stepInBar) == true)
        val hasHihat = (section.drumsEnabled || e >= 0.44f) && (grid[TrackType.HIHAT]?.getOrNull(stepInBar) == true)
        val hasClap = section.drumsEnabled && e >= 0.72f && (grid[TrackType.CLAP]?.getOrNull(stepInBar) == true)
        val hasBass = section.bassEnabled && (grid[TrackType.BASS]?.getOrNull(stepInBar) == true)
        val hasLead = section.leadEnabled && (grid[TrackType.LEAD]?.getOrNull(stepInBar) == true)
        val hasChordTrigger = section.chordsEnabled && (stepInBar == 0 || (e > 0.8f && stepInBar == 8))
        val hasArp = section.arpEnabled && (stepInBar % 2 == 0)

        val chordNotes = parseChordMidiNotes(chordName)
        val bassFreq = midiToFreq(chordNotes.first() - 24)
        val chordFreqs = chordNotes.map { midiToFreq(it) }
        val arpFreq = midiToFreq(chordNotes[(stepInBar / 2) % chordNotes.size] + 12)
        val leadDegrees = intArrayOf(0, 2, 4, 5, 7, 4, 3, 2, 0, 4, 6, 4, 2, 5, 3, 1)
        val leadMidi = 60 + scale.offsets[leadDegrees[stepInBar % leadDegrees.size] % scale.offsets.size]
        val leadFreq = midiToFreq(leadMidi)

        var seed = (stepInBar + 1) * 7919 + 131
        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            var mix = 0.0

            seed = seed * 1664525 + 1013904223
            val noise = ((seed shr 16) and 0xFFFF) / 32768.0 - 1.0

            if (hasKick) {
                val freqSweep = 42.0 + 118.0 * exp(-t * 36.0)
                val kickEnv = exp(-t * 10.5)
                mix += sin(2.0 * PI * freqSweep * t) * kickEnv * (0.85 * e + 0.15)
            }

            if (hasSnare) {
                val body = sin(2.0 * PI * 185.0 * t) * exp(-t * 28.0) * 0.42
                val snap = noise * exp(-t * 20.0) * 0.50
                mix += (body + snap) * e
            }

            if (hasHihat) {
                val metallic = (noise - 0.5 * sin(2.0 * PI * 7200.0 * t)) * exp(-t * 68.0) * 0.28
                mix += metallic * e
            }

            if (hasClap) {
                val burst = when {
                    t < 0.012 -> 1.0
                    t < 0.024 -> 0.8
                    t < 0.036 -> 0.65
                    else -> exp(-(t - 0.036) * 26.0)
                }
                mix += noise * burst * 0.45 * e
            }

            if (hasBass) {
                val bassEnv = (1.0 - exp(-t * 140.0)) * exp(-t * 4.8)
                val bassOsc = 0.72 * sin(2.0 * PI * bassFreq * t) + 0.28 * sin(4.0 * PI * bassFreq * t)
                mix += bassOsc * bassEnv * 0.62 * e
            }

            if (section.chordsEnabled) {
                val padEnv = if (hasChordTrigger) {
                    (1.0 - exp(-t * 25.0)) * exp(-t * 1.4)
                } else {
                    0.35 * exp(-t * 1.8)
                }
                var chordSum = 0.0
                for (cf in chordFreqs) {
                    chordSum += sin(2.0 * PI * cf * t) * 0.6 +
                        sampleOscillator(SynthWaveform.TRIANGLE, cf * 1.003, t) * 0.4
                }
                mix += (chordSum / chordFreqs.size) * padEnv * 0.34
            }

            if (hasArp) {
                val arpEnv = (1.0 - exp(-t * 180.0)) * exp(-t * 11.0)
                mix += sampleOscillator(SynthWaveform.TRIANGLE, arpFreq, t) * arpEnv * 0.24 * e
            }

            if (hasLead) {
                val leadEnv = (1.0 - exp(-t * 120.0)) * exp(-t * 8.0)
                mix += sampleOscillator(waveform, leadFreq, t) * leadEnv * 0.36 * e
            }

            if (vocalPcm != null && vocalPcm.isNotEmpty()) {
                val vIdx = vocalSampleOffset + i
                if (vIdx in vocalPcm.indices) {
                    val mainVocal = vocalPcm[vIdx.toInt()] / 32768.0
                    val echoIdx = (vIdx - (SAMPLE_RATE * 0.14).toLong()).toInt()
                    val echoVocal = if (echoIdx in vocalPcm.indices) (vocalPcm[echoIdx] / 32768.0) * 0.22 else 0.0
                    mix = mix * 0.76 + (mainVocal + echoVocal) * 0.92
                }
            }

            val limited = tanh(mix * masterVolume) * Short.MAX_VALUE
            output[i] = limited.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return output
    }

    private fun parseChordMidiNotes(chord: String): List<Int> {
        val c = chord.trim()
        if (c.isEmpty()) return listOf(57, 60, 64)
        val rootChar = c[0].uppercaseChar()
        var rootOffset = when (rootChar) {
            'C' -> 0
            'D' -> 2
            'E' -> 4
            'F' -> 5
            'G' -> 7
            'A' -> 9
            'B' -> 11
            else -> 9
        }
        if (c.length > 1) {
            when (c[1]) {
                '#' -> rootOffset += 1
                'b' -> rootOffset -= 1
            }
        }
        val rootMidi = 48 + ((rootOffset % 12) + 12) % 12
        val isMinor = c.lowercase().contains("m") && !c.lowercase().contains("maj")
        val third = if (isMinor) 3 else 4
        return listOf(rootMidi, rootMidi + third, rootMidi + 7)
    }

    private fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

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
        val bassFreq = midiToFreq(bassNote)

        val leadDegrees = intArrayOf(0, 2, 4, 5, 7, 4, 3, 2, 0, 4, 6, 4, 2, 5, 3, 1)
        val leadNote = 60 + scale.offsets[leadDegrees[stepIndex % leadDegrees.size] % scale.offsets.size]
        val leadFreq = midiToFreq(leadNote)

        var seed = (stepIndex + 1) * 7919 + 131
        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            var mix = 0.0

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
