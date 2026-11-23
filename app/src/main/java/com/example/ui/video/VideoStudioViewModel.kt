package com.example.ui.video

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ai.AiVideoStoryboard
import com.example.data.ai.CreativeAiRepository
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioProjectEntity
import com.example.data.local.StudioProjectRepository
import com.example.data.local.StudioProjectType
import com.example.engine.PhotoFilterPreset
import com.example.engine.VideoAspectRatio
import com.example.engine.VideoClipItem
import com.example.engine.VideoComposerEngine
import com.example.engine.VideoTransition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface AiStoryboardUiState {
    data object Idle : AiStoryboardUiState
    data object Loading : AiStoryboardUiState
    data class Success(val storyboard: AiVideoStoryboard) : AiStoryboardUiState
    data class Error(val message: String) : AiStoryboardUiState
}

data class VideoStudioUiState(
    val title: String = "Montage Hydra Studio",
    val clips: List<VideoClipItem> = emptyList(),
    val clipBitmaps: Map<String, Bitmap> = emptyMap(),
    val selectedClipIndex: Int = 0,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.PORTRAIT_9_16,
    val audioUri: Uri? = null,
    val audioLabel: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val exportProgress: Float? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val storyboardState: AiStoryboardUiState = AiStoryboardUiState.Idle
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.durationSec * 1000L }
}

class VideoStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val aiRepository = CreativeAiRepository()
    private val projectRepository = StudioProjectRepository(
        StudioDatabase.getInstance(application).projectDao()
    )

    val savedAudioExports: StateFlow<List<StudioProjectEntity>> = projectRepository.observeProjects()
        .map { list -> list.filter { it.type == StudioProjectType.MUSIC.name && !it.exportedUri.isNullOrBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _uiState = MutableStateFlow(VideoStudioUiState())
    val uiState: StateFlow<VideoStudioUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    init {
        loadStarterClips()
    }

    fun loadStarterClips() {
        viewModelScope.launch {
            val (clips, bitmaps) = withContext(Dispatchers.Default) {
                val ctx = getApplication<Application>()
                val hero = BitmapFactory.decodeResource(ctx.resources, R.drawable.img_studio_hero_1795396503160)
                    ?: createGradientFrame(Color.rgb(8, 22, 48), Color.rgb(0, 229, 255))
                val slide2 = createGradientFrame(Color.rgb(34, 12, 58), Color.rgb(255, 179, 0))
                val slide3 = createGradientFrame(Color.rgb(10, 32, 34), Color.rgb(16, 224, 160))

                val c1 = VideoClipItem(
                    id = "starter_1",
                    uri = Uri.EMPTY,
                    isVideo = false,
                    durationSec = 3,
                    transition = VideoTransition.ZOOM,
                    filter = PhotoFilterPreset.ORIGINAL,
                    caption = "Hydra Studio Bergama"
                )
                val c2 = VideoClipItem(
                    id = "starter_2",
                    uri = Uri.EMPTY,
                    isVideo = false,
                    durationSec = 3,
                    transition = VideoTransition.FADE,
                    filter = PhotoFilterPreset.CINEMA,
                    caption = "Montage Vidéo Sans Limite"
                )
                val c3 = VideoClipItem(
                    id = "starter_3",
                    uri = Uri.EMPTY,
                    isVideo = false,
                    durationSec = 3,
                    transition = VideoTransition.SLIDE,
                    filter = PhotoFilterPreset.GOLDEN,
                    caption = "Export HD Sans Filigrane"
                )
                listOf(c1, c2, c3) to mapOf(c1.id to hero, c2.id to slide2, c3.id to slide3)
            }
            _uiState.update {
                it.copy(
                    clips = clips,
                    clipBitmaps = bitmaps,
                    selectedClipIndex = 0,
                    positionMs = 0L
                )
            }
        }
    }

    private fun createGradientFrame(c1: Int, c2: Int): Bitmap {
        val bmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 720f, 1280f, c1, c2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 720f, 1280f, paint)
        return bmp
    }

    fun clearAllClips() {
        stopPlayback()
        _uiState.update {
            it.copy(
                clips = emptyList(),
                clipBitmaps = emptyMap(),
                selectedClipIndex = 0,
                positionMs = 0L,
                statusMessage = "Timeline vidée."
            )
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun addMediaUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val newClips = mutableListOf<VideoClipItem>()
            val newBitmaps = mutableMapOf<String, Bitmap>()
            for (uri in uris) {
                val mime = ctx.contentResolver.getType(uri).orEmpty()
                val isVideo = mime.startsWith("video/")
                val clip = VideoClipItem(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    isVideo = isVideo,
                    durationSec = 4
                )
                runCatching {
                    VideoComposerEngine.loadClipFrame(ctx, clip)
                }.onSuccess { bmp ->
                    newClips.add(clip)
                    newBitmaps[clip.id] = bmp
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.localizedMessage ?: "Média illisible.")
                    }
                }
            }
            if (newClips.isNotEmpty()) {
                _uiState.update { state ->
                    val existingFiltered = state.clips.filterNot { it.id.startsWith("starter_") }
                    val existingBmps = state.clipBitmaps.filterKeys { !it.startsWith("starter_") }
                    state.copy(
                        clips = existingFiltered + newClips,
                        clipBitmaps = existingBmps + newBitmaps,
                        selectedClipIndex = if (existingFiltered.isEmpty()) 0 else state.selectedClipIndex,
                        statusMessage = "${newClips.size} média(s) ajouté(s) à la timeline."
                    )
                }
            }
        }
    }

    fun attachAudio(uri: Uri, displayName: String?) {
        _uiState.update {
            it.copy(
                audioUri = uri,
                audioLabel = displayName ?: "Piste audio personnalisée",
                statusMessage = "Bande-son attachée au montage."
            )
        }
    }

    fun removeAudio() {
        stopPlayback()
        _uiState.update { it.copy(audioUri = null, audioLabel = null) }
    }

    fun selectClip(index: Int) {
        _uiState.update { state ->
            val clamped = index.coerceIn(0, (state.clips.size - 1).coerceAtLeast(0))
            state.copy(selectedClipIndex = clamped)
        }
    }

    fun updateSelectedClipDuration(seconds: Int) {
        _uiState.update { state ->
            val idx = state.selectedClipIndex
            if (idx !in state.clips.indices) return@update state
            val updated = state.clips.toMutableList()
            updated[idx] = updated[idx].copy(durationSec = seconds.coerceIn(1, 120))
            state.copy(clips = updated)
        }
    }

    fun updateSelectedClipTransition(transition: VideoTransition) {
        _uiState.update { state ->
            val idx = state.selectedClipIndex
            if (idx !in state.clips.indices) return@update state
            val updated = state.clips.toMutableList()
            updated[idx] = updated[idx].copy(transition = transition)
            state.copy(clips = updated)
        }
    }

    fun updateSelectedClipFilter(filter: PhotoFilterPreset) {
        _uiState.update { state ->
            val idx = state.selectedClipIndex
            if (idx !in state.clips.indices) return@update state
            val updated = state.clips.toMutableList()
            updated[idx] = updated[idx].copy(filter = filter)
            state.copy(clips = updated)
        }
    }

    fun updateSelectedClipCaption(caption: String) {
        _uiState.update { state ->
            val idx = state.selectedClipIndex
            if (idx !in state.clips.indices) return@update state
            val updated = state.clips.toMutableList()
            updated[idx] = updated[idx].copy(caption = caption)
            state.copy(clips = updated)
        }
    }

    fun moveSelectedClip(delta: Int) {
        _uiState.update { state ->
            val idx = state.selectedClipIndex
            val target = idx + delta
            if (idx !in state.clips.indices || target !in state.clips.indices) return@update state
            val updated = state.clips.toMutableList()
            val item = updated.removeAt(idx)
            updated.add(target, item)
            state.copy(clips = updated, selectedClipIndex = target)
        }
    }

    fun removeSelectedClip() {
        stopPlayback()
        _uiState.update { state ->
            val idx = state.selectedClipIndex
            if (idx !in state.clips.indices) return@update state
            val updated = state.clips.toMutableList()
            val removed = updated.removeAt(idx)
            state.copy(
                clips = updated,
                clipBitmaps = state.clipBitmaps - removed.id,
                selectedClipIndex = idx.coerceAtMost((updated.size - 1).coerceAtLeast(0)),
                positionMs = 0L
            )
        }
    }

    fun setAspectRatio(ratio: VideoAspectRatio) {
        _uiState.update { it.copy(aspectRatio = ratio) }
    }

    fun seekTo(posMs: Long) {
        val total = _uiState.value.totalDurationMs.coerceAtLeast(1L)
        val clamped = posMs.coerceIn(0L, total)
        _uiState.update { it.copy(positionMs = clamped) }
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.duration > 0) mp.seekTo((clamped % mp.duration).toInt())
            }
        }
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val state = _uiState.value
        if (state.clips.isEmpty()) return
        stopPlayback()
        _uiState.update { it.copy(isPlaying = true) }

        state.audioUri?.let { uri ->
            runCatching {
                mediaPlayer = MediaPlayer.create(getApplication(), uri)?.apply {
                    isLooping = true
                    seekTo((state.positionMs % duration.coerceAtLeast(1)).toInt())
                    start()
                }
            }
        }

        playJob = viewModelScope.launch {
            val frameStepMs = 42L
            while (isActive) {
                delay(frameStepMs)
                val total = _uiState.value.totalDurationMs.coerceAtLeast(1L)
                val next = _uiState.value.positionMs + frameStepMs
                if (next >= total) {
                    _uiState.update { it.copy(positionMs = 0L) }
                    runCatching { mediaPlayer?.seekTo(0) }
                } else {
                    _uiState.update { it.copy(positionMs = next) }
                }
            }
        }
    }

    fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun generateStoryboard(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(storyboardState = AiStoryboardUiState.Loading) }
            try {
                val storyboard = aiRepository.generateVideoStoryboard(prompt)
                _uiState.update { state ->
                    val updatedClips = state.clips.mapIndexed { idx, clip ->
                        val scene = storyboard.scenes.getOrNull(idx)
                        if (scene != null) {
                            clip.copy(
                                durationSec = scene.durationSec,
                                caption = scene.caption
                            )
                        } else {
                            clip
                        }
                    }
                    state.copy(
                        title = storyboard.title,
                        clips = updatedClips,
                        storyboardState = AiStoryboardUiState.Success(storyboard)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        storyboardState = AiStoryboardUiState.Error(
                            e.localizedMessage ?: "Échec du storyboard IA."
                        )
                    )
                }
            }
        }
    }

    fun exportMp4() {
        val state = _uiState.value
        if (state.clips.isEmpty() || state.exportProgress != null) return
        stopPlayback()
        viewModelScope.launch {
            _uiState.update { it.copy(exportProgress = 0.01f, statusMessage = null, errorMessage = null) }
            try {
                val uri = VideoComposerEngine.exportMp4ToMediaStore(
                    context = getApplication(),
                    title = state.title,
                    clips = state.clips,
                    bitmaps = state.clipBitmaps,
                    aspectRatio = state.aspectRatio,
                    audioUri = state.audioUri,
                    onProgress = { p ->
                        _uiState.update { it.copy(exportProgress = p.coerceIn(0.01f, 1f)) }
                    }
                )
                val sec = state.totalDurationMs / 1000L
                projectRepository.saveProject(
                    title = state.title,
                    type = StudioProjectType.VIDEO,
                    summary = "${state.aspectRatio.label} • ${state.clips.size} plans • ${sec}s MP4",
                    exportedUri = uri.toString()
                )
                _uiState.update {
                    it.copy(
                        exportProgress = null,
                        statusMessage = "Vidéo MP4 HD exportée sans filigrane dans Films/Hydra Studio Bergama !"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exportProgress = null,
                        errorMessage = e.localizedMessage ?: "Erreur lors de l'encodage vidéo MP4."
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
