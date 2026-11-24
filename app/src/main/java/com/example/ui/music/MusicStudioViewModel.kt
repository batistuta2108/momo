package com.example.ui.music

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiSongPlan
import com.example.data.ai.CreativeAiRepository
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioProjectRepository
import com.example.data.local.StudioProjectType
import com.example.engine.AudioSynthEngine
import com.example.engine.BeatPreset
import com.example.engine.BeatPresets
import com.example.engine.MusicalScale
import com.example.engine.SunoCreateMode
import com.example.engine.SunoSongLength
import com.example.engine.SunoSongTrack
import com.example.engine.SunoStarterLibrary
import com.example.engine.SunoVoicePersona
import com.example.engine.SynthWaveform
import com.example.engine.TrackType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class MusicStudioWorkspaceTab(val label: String) {
    SUNO_FULL("Suno Studio"),
    DAW_PRO("Séquenceur DAW")
}

sealed interface AiSongUiState {
    data object Idle : AiSongUiState
    data object Loading : AiSongUiState
    data class Success(val plan: AiSongPlan) : AiSongUiState
    data class Error(val message: String) : AiSongUiState
}

data class MusicStudioUiState(
    val workspaceTab: MusicStudioWorkspaceTab = MusicStudioWorkspaceTab.SUNO_FULL,
    // Suno Full State
    val sunoCreateMode: SunoCreateMode = SunoCreateMode.SIMPLE,
    val simplePrompt: String = "",
    val customTitle: String = "",
    val customLyrics: String = "",
    val customStyleTags: String = "Afro-Trap, 808 Profonde, Synthé Spatial",
    val isInstrumental: Boolean = false,
    val voicePersona: SunoVoicePersona = SunoVoicePersona.MALE_RAP,
    val songLength: SunoSongLength = SunoSongLength.FULL,
    val sunoLibrary: List<SunoSongTrack> = SunoStarterLibrary.starterTracks(),
    val activeSongId: String = SunoStarterLibrary.starterTracks().first().id,
    val isPlayingSong: Boolean = false,
    val songPosMs: Long = 0L,
    val songTotalMs: Long = SunoStarterLibrary.starterTracks().first().durationSec * 1000L,
    val activeSectionIndex: Int = 0,
    val liveTtsEnabled: Boolean = true,
    val isSunoAiBusy: Boolean = false,
    val isLyricsAiBusy: Boolean = false,
    val sunoAiError: String? = null,
    // Pro DAW Sequencer State
    val title: String = "Session Trap 808",
    val selectedPreset: String = BeatPresets.all.first().name,
    val bpm: Int = BeatPresets.all.first().bpm,
    val swing: Float = 0.08f,
    val waveform: SynthWaveform = BeatPresets.all.first().waveform,
    val scale: MusicalScale = BeatPresets.all.first().scale,
    val masterVolume: Float = 0.85f,
    val grid: Map<TrackType, List<Boolean>> = BeatPresets.all.first().grid,
    val mutedTracks: Set<TrackType> = emptySet(),
    val isPlaying: Boolean = false,
    val currentStep: Int = -1,
    val exportBars: Int = 8,
    val isExporting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val aiSongState: AiSongUiState = AiSongUiState.Idle
) {
    val activeSong: SunoSongTrack?
        get() = sunoLibrary.firstOrNull { it.id == activeSongId } ?: sunoLibrary.firstOrNull()
}

class MusicStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = AudioSynthEngine()
    private val aiRepository = CreativeAiRepository()
    private val projectRepository = StudioProjectRepository(
        StudioDatabase.getInstance(application).projectDao()
    )

    private val _uiState = MutableStateFlow(MusicStudioUiState())
    val uiState: StateFlow<MusicStudioUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var lastSpokenSectionIndex: Int = -1

    init {
        syncEngine()
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        runCatching {
            tts = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val res = tts?.setLanguage(Locale.FRENCH)
                    ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                    if (!ttsReady) {
                        tts?.language = Locale.getDefault()
                        ttsReady = true
                    }
                }
            }
        }
    }

    private fun stopAllPlayback() {
        engine.stopLoop()
        runCatching { tts?.stop() }
        lastSpokenSectionIndex = -1
        _uiState.update {
            it.copy(
                isPlaying = false,
                isPlayingSong = false,
                currentStep = -1
            )
        }
    }

    fun selectWorkspaceTab(tab: MusicStudioWorkspaceTab) {
        stopAllPlayback()
        _uiState.update { it.copy(workspaceTab = tab) }
    }

    // --- SUNO FULL CONTROLS ---

    fun setSunoCreateMode(mode: SunoCreateMode) {
        _uiState.update { it.copy(sunoCreateMode = mode) }
    }

    fun updateSimplePrompt(value: String) {
        _uiState.update { it.copy(simplePrompt = value, sunoAiError = null) }
    }

    fun updateCustomTitle(value: String) {
        _uiState.update { it.copy(customTitle = value) }
    }

    fun updateCustomLyrics(value: String) {
        _uiState.update { it.copy(customLyrics = value) }
    }

    fun appendLyricsSectionTag(tag: String) {
        _uiState.update { state ->
            val prefix = if (state.customLyrics.isBlank()) "" else "\n\n"
            state.copy(customLyrics = "${state.customLyrics}$prefix[$tag]\n")
        }
    }

    fun updateCustomStyleTags(value: String) {
        _uiState.update { it.copy(customStyleTags = value) }
    }

    fun toggleStyleChip(tag: String) {
        _uiState.update { state ->
            val current = state.customStyleTags
            val updated = if (current.contains(tag, ignoreCase = true)) {
                current
            } else if (current.isBlank()) {
                tag
            } else {
                "$current, $tag"
            }
            state.copy(customStyleTags = updated)
        }
    }

    fun setInstrumental(instrumental: Boolean) {
        _uiState.update { it.copy(isInstrumental = instrumental) }
    }

    fun setVoicePersona(persona: SunoVoicePersona) {
        _uiState.update { it.copy(voicePersona = persona) }
    }

    fun setSongLength(length: SunoSongLength) {
        _uiState.update { it.copy(songLength = length) }
    }

    fun toggleLiveTts() {
        val next = !_uiState.value.liveTtsEnabled
        if (!next) {
            runCatching { tts?.stop() }
        }
        _uiState.update { it.copy(liveTtsEnabled = next) }
    }

    fun selectSunoSong(songId: String) {
        stopAllPlayback()
        val song = _uiState.value.sunoLibrary.firstOrNull { it.id == songId } ?: return
        _uiState.update {
            it.copy(
                activeSongId = song.id,
                songPosMs = 0L,
                songTotalMs = song.durationSec * 1000L,
                activeSectionIndex = 0
            )
        }
    }

    fun remixSongIntoCustom(song: SunoSongTrack) {
        _uiState.update {
            it.copy(
                sunoCreateMode = SunoCreateMode.CUSTOM,
                customTitle = "${song.title} (Remix)",
                customStyleTags = song.styleTags,
                customLyrics = song.fullLyrics,
                isInstrumental = song.isInstrumental,
                voicePersona = song.voicePersona,
                statusMessage = "Paroles et style chargés dans le mode Personnalisé."
            )
        }
    }

    fun loadSongIntoDaw(song: SunoSongTrack) {
        stopAllPlayback()
        _uiState.update {
            it.copy(
                workspaceTab = MusicStudioWorkspaceTab.DAW_PRO,
                title = song.title,
                selectedPreset = "Suno • ${song.title}",
                bpm = song.bpm,
                swing = song.swing,
                waveform = song.waveform,
                scale = song.scale,
                grid = song.grid,
                mutedTracks = emptySet(),
                statusMessage = "Morceau « ${song.title} » ouvert dans le Séquenceur DAW."
            )
        }
        syncEngine()
    }

    fun togglePlayActiveSong() {
        val state = _uiState.value
        val song = state.activeSong ?: return
        if (state.isPlayingSong) {
            stopAllPlayback()
            return
        }
        val fraction = if (state.songTotalMs > 0 && state.songPosMs < state.songTotalMs - 500L) {
            state.songPosMs.toFloat() / state.songTotalMs.toFloat()
        } else {
            0f
        }
        startSunoSongPlayback(song, fraction)
    }

    fun seekActiveSong(posMs: Long) {
        val state = _uiState.value
        val song = state.activeSong ?: return
        val total = state.songTotalMs.coerceAtLeast(1000L)
        val clamped = posMs.coerceIn(0L, total)
        _uiState.update { it.copy(songPosMs = clamped) }
        if (state.isPlayingSong) {
            startSunoSongPlayback(song, clamped.toFloat() / total.toFloat())
        }
    }

    private fun startSunoSongPlayback(song: SunoSongTrack, startFraction: Float) {
        stopAllPlayback()
        _uiState.update {
            it.copy(
                isPlayingSong = true,
                songTotalMs = song.durationSec * 1000L
            )
        }
        engine.startFullSong(
            scope = viewModelScope,
            song = song,
            masterVolume = _uiState.value.masterVolume,
            startFraction = startFraction,
            onProgress = { posMs, totalMs, secIdx, stepInBar ->
                _uiState.update {
                    it.copy(
                        songPosMs = posMs,
                        songTotalMs = totalMs,
                        activeSectionIndex = secIdx,
                        currentStep = stepInBar
                    )
                }
                maybeSpeakSection(song, secIdx)
            },
            onFinished = {
                stopAllPlayback()
                _uiState.update { it.copy(songPosMs = 0L, activeSectionIndex = 0) }
            }
        )
    }

    private fun maybeSpeakSection(song: SunoSongTrack, sectionIndex: Int) {
        val state = _uiState.value
        if (!state.liveTtsEnabled || song.isInstrumental || song.vocalPcm44k != null) return
        if (!ttsReady || sectionIndex == lastSpokenSectionIndex) return
        lastSpokenSectionIndex = sectionIndex

        val section = song.sections.getOrNull(sectionIndex) ?: return
        val cleanText = section.lyrics.trim()
        if (cleanText.isBlank() || cleanText.startsWith("(")) return

        runCatching {
            tts?.setPitch(song.voicePersona.pitch)
            tts?.setSpeechRate(song.voicePersona.speechRate)
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "sec_$sectionIndex")
        }
    }

    fun generateLyricsWithAi() {
        val s = _uiState.value
        val topic = s.customTitle.ifBlank { s.simplePrompt }.ifBlank { s.customStyleTags }
        if (topic.isBlank() || s.isLyricsAiBusy) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLyricsAiBusy = true, sunoAiError = null, statusMessage = null) }
            try {
                val (genTitle, genLyrics) = aiRepository.generateSunoLyricsOnly(topic, s.customStyleTags)
                _uiState.update {
                    it.copy(
                        isLyricsAiBusy = false,
                        customTitle = it.customTitle.ifBlank { genTitle },
                        customLyrics = genLyrics,
                        statusMessage = "Paroles structurées générées par Gemini IA !"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLyricsAiBusy = false,
                        sunoAiError = e.localizedMessage ?: "Échec de génération des paroles IA."
                    )
                }
            }
        }
    }

    fun createSunoTrackWithAi() {
        val s = _uiState.value
        if (s.isSunoAiBusy) return
        val prompt = if (s.sunoCreateMode == SunoCreateMode.SIMPLE) {
            s.simplePrompt.ifBlank { "Afro-trap moderne mélodique et énergique" }
        } else {
            s.customStyleTags.ifBlank { s.customTitle.ifBlank { "Studio moderne" } }
        }

        viewModelScope.launch {
            stopAllPlayback()
            _uiState.update { it.copy(isSunoAiBusy = true, sunoAiError = null, statusMessage = null) }
            try {
                val (song, hasAiVocals) = aiRepository.generateSunoFullSong(
                    prompt = prompt,
                    customLyrics = if (s.sunoCreateMode == SunoCreateMode.CUSTOM) s.customLyrics else null,
                    styleTags = if (s.sunoCreateMode == SunoCreateMode.CUSTOM) s.customStyleTags else null,
                    titleHint = if (s.sunoCreateMode == SunoCreateMode.CUSTOM) s.customTitle else null,
                    isInstrumental = s.isInstrumental,
                    voicePersona = s.voicePersona,
                    length = s.songLength
                )
                _uiState.update { state ->
                    state.copy(
                        isSunoAiBusy = false,
                        sunoLibrary = listOf(song) + state.sunoLibrary,
                        activeSongId = song.id,
                        songPosMs = 0L,
                        songTotalMs = song.durationSec * 1000L,
                        activeSectionIndex = 0,
                        statusMessage = if (hasAiVocals) {
                            "Morceau Suno IA créé avec voix IA intégrée (${song.title}) !"
                        } else {
                            "Morceau Suno IA créé (${song.title} • ${song.bpm} BPM) !"
                        }
                    )
                }
                startSunoSongPlayback(song, 0f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSunoAiBusy = false,
                        sunoAiError = e.localizedMessage ?: "Échec de la création Suno IA."
                    )
                }
            }
        }
    }

    fun createSunoTrackOffline() {
        val s = _uiState.value
        stopAllPlayback()
        val style = if (s.sunoCreateMode == SunoCreateMode.SIMPLE) {
            s.simplePrompt.ifBlank { s.customStyleTags }
        } else {
            s.customStyleTags
        }
        val lyrics = if (s.sunoCreateMode == SunoCreateMode.CUSTOM) s.customLyrics else ""
        val title = if (s.sunoCreateMode == SunoCreateMode.CUSTOM) s.customTitle else ""

        val song = SunoStarterLibrary.buildLocalSunoTrack(
            titleInput = title,
            promptOrStyle = style,
            lyricsInput = lyrics,
            isInstrumental = s.isInstrumental,
            voicePersona = s.voicePersona,
            length = s.songLength
        )

        _uiState.update { state ->
            state.copy(
                sunoLibrary = listOf(song) + state.sunoLibrary,
                activeSongId = song.id,
                songPosMs = 0L,
                songTotalMs = song.durationSec * 1000L,
                activeSectionIndex = 0,
                sunoAiError = null,
                statusMessage = "Morceau « ${song.title} » produit en Studio (${song.durationSec}s) !"
            )
        }
        startSunoSongPlayback(song, 0f)
    }

    fun exportActiveSongWav() {
        val s = _uiState.value
        val song = s.activeSong ?: return
        if (s.isExporting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, statusMessage = null, errorMessage = null) }
            try {
                val uri = engine.exportFullSongWavToMediaStore(
                    context = getApplication(),
                    song = song,
                    masterVolume = s.masterVolume
                )
                projectRepository.saveProject(
                    title = song.title,
                    type = StudioProjectType.MUSIC,
                    summary = "${song.bpm} BPM • ${song.durationSec}s • ${song.styleTags.take(24)} • WAV",
                    exportedUri = uri.toString()
                )
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        statusMessage = "Chanson complète « ${song.title} » exportée en WAV dans Musique/Hydra Studio Bergama !"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = e.localizedMessage ?: "Erreur lors de l'export WAV."
                    )
                }
            }
        }
    }

    // --- PRO DAW SEQUENCER CONTROLS ---

    private fun syncEngine() {
        val s = _uiState.value
        engine.updateParams(
            grid = s.grid,
            muted = s.mutedTracks,
            bpm = s.bpm,
            swing = s.swing,
            waveform = s.waveform,
            scale = s.scale,
            volume = s.masterVolume
        )
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun toggleStep(track: TrackType, stepIndex: Int) {
        _uiState.update { state ->
            val currentList = state.grid[track] ?: List(16) { false }
            val updated = currentList.toMutableList().apply {
                this[stepIndex] = !this[stepIndex]
            }
            state.copy(
                selectedPreset = "Personnalisé",
                grid = state.grid + (track to updated)
            )
        }
        syncEngine()
    }

    fun toggleMute(track: TrackType) {
        _uiState.update { state ->
            val next = if (track in state.mutedTracks) {
                state.mutedTracks - track
            } else {
                state.mutedTracks + track
            }
            state.copy(mutedTracks = next)
        }
        syncEngine()
    }

    fun applyPreset(preset: BeatPreset) {
        _uiState.update {
            it.copy(
                title = "Session ${preset.name}",
                selectedPreset = preset.name,
                bpm = preset.bpm,
                scale = preset.scale,
                waveform = preset.waveform,
                grid = preset.grid,
                mutedTracks = emptySet()
            )
        }
        syncEngine()
    }

    fun setBpm(bpm: Int) {
        _uiState.update { it.copy(bpm = bpm.coerceIn(60, 200)) }
        syncEngine()
    }

    fun setSwing(swing: Float) {
        _uiState.update { it.copy(swing = swing.coerceIn(0f, 0.5f)) }
        syncEngine()
    }

    fun setWaveform(waveform: SynthWaveform) {
        _uiState.update { it.copy(waveform = waveform) }
        syncEngine()
    }

    fun setScale(scale: MusicalScale) {
        _uiState.update { it.copy(scale = scale) }
        syncEngine()
    }

    fun setMasterVolume(volume: Float) {
        _uiState.update { it.copy(masterVolume = volume.coerceIn(0.1f, 1.0f)) }
        syncEngine()
    }

    fun generateProceduralBeat() {
        val rng = java.util.Random()
        val kicks = List(16) { i -> i == 0 || i == 8 || (i % 2 == 0 && rng.nextFloat() < 0.3f) || (i == 6 && rng.nextBoolean()) }
        val snares = List(16) { i -> i == 4 || i == 12 || (i == 15 && rng.nextFloat() < 0.35f) }
        val hihats = List(16) { i -> i % 2 == 0 || rng.nextFloat() < 0.55f }
        val claps = List(16) { i -> i == 12 || (i == 4 && rng.nextBoolean()) }
        val bass = List(16) { i -> kicks[i] || (i % 4 == 2 && rng.nextFloat() < 0.5f) }
        val lead = List(16) { i -> i % 3 == 0 || rng.nextFloat() < 0.45f }

        val newBpm = 88 + rng.nextInt(56)
        val wf = SynthWaveform.entries[rng.nextInt(SynthWaveform.entries.size)]
        val sc = MusicalScale.entries[rng.nextInt(MusicalScale.entries.size)]

        _uiState.update {
            it.copy(
                selectedPreset = "Générateur Pro",
                bpm = newBpm,
                waveform = wf,
                scale = sc,
                grid = mapOf(
                    TrackType.KICK to kicks,
                    TrackType.SNARE to snares,
                    TrackType.HIHAT to hihats,
                    TrackType.CLAP to claps,
                    TrackType.BASS to bass,
                    TrackType.LEAD to lead
                ),
                statusMessage = "Nouveau beat généré ($newBpm BPM • ${sc.label}) !"
            )
        }
        syncEngine()
    }

    fun setExportBars(bars: Int) {
        _uiState.update { it.copy(exportBars = bars.coerceIn(1, 256)) }
    }

    fun togglePlayback() {
        val currentlyPlaying = _uiState.value.isPlaying
        if (currentlyPlaying) {
            stopAllPlayback()
        } else {
            stopAllPlayback()
            syncEngine()
            _uiState.update { it.copy(isPlaying = true) }
            engine.startLoop(viewModelScope) { step ->
                _uiState.update { it.copy(currentStep = step) }
            }
        }
    }

    fun triggerLiveNote(midiNote: Int) {
        val s = _uiState.value
        engine.playLiveKey(viewModelScope, midiNote, s.waveform, s.masterVolume)
    }

    fun generateWithAi(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(aiSongState = AiSongUiState.Loading) }
            try {
                val plan = aiRepository.generateSongPlan(prompt)
                _uiState.update { state ->
                    val newGrid = state.grid.toMutableMap().apply {
                        put(TrackType.KICK, plan.kickSteps)
                        put(TrackType.SNARE, plan.snareSteps)
                        put(TrackType.HIHAT, plan.hihatSteps)
                        put(TrackType.BASS, plan.bassSteps)
                        put(TrackType.LEAD, plan.leadSteps)
                    }
                    state.copy(
                        title = plan.title,
                        bpm = plan.bpm,
                        selectedPreset = "IA • ${plan.genre}",
                        grid = newGrid,
                        aiSongState = AiSongUiState.Success(plan)
                    )
                }
                syncEngine()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(aiSongState = AiSongUiState.Error(e.localizedMessage ?: "Erreur IA."))
                }
            }
        }
    }

    fun exportWav() {
        val s = _uiState.value
        if (s.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, statusMessage = null, errorMessage = null) }
            try {
                val uri = engine.exportWavToMediaStore(
                    context = getApplication(),
                    title = s.title,
                    grid = s.grid,
                    muted = s.mutedTracks,
                    bpm = s.bpm,
                    swing = s.swing,
                    waveform = s.waveform,
                    scale = s.scale,
                    volume = s.masterVolume,
                    barsCount = s.exportBars
                )
                projectRepository.saveProject(
                    title = s.title,
                    type = StudioProjectType.MUSIC,
                    summary = "${s.bpm} BPM • ${s.exportBars} mesures • WAV",
                    exportedUri = uri.toString()
                )
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        statusMessage = "Audio WAV exporté sans limite dans Musique/Hydra Studio Bergama !"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = e.localizedMessage ?: "Erreur lors de l'export WAV."
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAllPlayback()
        runCatching { tts?.shutdown() }
    }
}
