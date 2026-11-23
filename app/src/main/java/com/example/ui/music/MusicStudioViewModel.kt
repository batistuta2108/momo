package com.example.ui.music

import android.app.Application
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
import com.example.engine.SynthWaveform
import com.example.engine.TrackType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AiSongUiState {
    data object Idle : AiSongUiState
    data object Loading : AiSongUiState
    data class Success(val plan: AiSongPlan) : AiSongUiState
    data class Error(val message: String) : AiSongUiState
}

data class MusicStudioUiState(
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
)

class MusicStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = AudioSynthEngine()
    private val aiRepository = CreativeAiRepository()
    private val projectRepository = StudioProjectRepository(
        StudioDatabase.getInstance(application).projectDao()
    )

    private val _uiState = MutableStateFlow(MusicStudioUiState())
    val uiState: StateFlow<MusicStudioUiState> = _uiState.asStateFlow()

    init {
        syncEngine()
    }

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

    fun setExportBars(bars: Int) {
        _uiState.update { it.copy(exportBars = bars.coerceIn(1, 256)) }
    }

    fun togglePlayback() {
        val currentlyPlaying = _uiState.value.isPlaying
        if (currentlyPlaying) {
            engine.stopLoop()
            _uiState.update { it.copy(isPlaying = false, currentStep = -1) }
        } else {
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

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        engine.stopLoop()
    }
}
