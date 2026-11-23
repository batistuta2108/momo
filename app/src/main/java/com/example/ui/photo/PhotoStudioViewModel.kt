package com.example.ui.photo

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiImageRatio
import com.example.data.ai.CreativeAiRepository
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioProjectRepository
import com.example.data.local.StudioProjectType
import com.example.engine.PhotoAspectRatio
import com.example.engine.PhotoEditState
import com.example.engine.PhotoFilterPreset
import com.example.engine.PhotoProcessor
import com.example.engine.TextPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoStudioUiState(
    val title: String = "Création Photo Pro",
    val originalBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val edits: PhotoEditState = PhotoEditState(),
    val showOriginal: Boolean = false,
    val isAiBusy: Boolean = false,
    val aiError: String? = null,
    val isExporting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class PhotoStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val aiRepository = CreativeAiRepository()
    private val projectRepository = StudioProjectRepository(
        StudioDatabase.getInstance(application).projectDao()
    )

    private val _uiState = MutableStateFlow(PhotoStudioUiState())
    val uiState: StateFlow<PhotoStudioUiState> = _uiState.asStateFlow()

    private var renderJob: Job? = null

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val bmp = PhotoProcessor.loadBitmapFromUri(getApplication(), uri)
                _uiState.update {
                    it.copy(
                        originalBitmap = bmp,
                        edits = PhotoEditState(),
                        aiError = null,
                        errorMessage = null,
                        statusMessage = "Photo importée en haute résolution."
                    )
                }
                scheduleRender()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "Erreur lors de l'import photo.")
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun setFilter(filter: PhotoFilterPreset) {
        _uiState.update { it.copy(edits = it.edits.copy(filter = filter)) }
        scheduleRender()
    }

    fun setBrightness(v: Float) {
        _uiState.update { it.copy(edits = it.edits.copy(brightness = v)) }
        scheduleRender()
    }

    fun setContrast(v: Float) {
        _uiState.update { it.copy(edits = it.edits.copy(contrast = v)) }
        scheduleRender()
    }

    fun setSaturation(v: Float) {
        _uiState.update { it.copy(edits = it.edits.copy(saturation = v)) }
        scheduleRender()
    }

    fun setWarmth(v: Float) {
        _uiState.update { it.copy(edits = it.edits.copy(warmth = v)) }
        scheduleRender()
    }

    fun setVignette(v: Float) {
        _uiState.update { it.copy(edits = it.edits.copy(vignette = v)) }
        scheduleRender()
    }

    fun setAspectRatio(ratio: PhotoAspectRatio) {
        _uiState.update { it.copy(edits = it.edits.copy(aspectRatio = ratio)) }
        scheduleRender()
    }

    fun setOverlayText(text: String) {
        _uiState.update { it.copy(edits = it.edits.copy(overlayText = text)) }
        scheduleRender()
    }

    fun setOverlayPosition(position: TextPosition) {
        _uiState.update { it.copy(edits = it.edits.copy(overlayPosition = position)) }
        scheduleRender()
    }

    fun setOverlayColor(colorHex: Long) {
        _uiState.update { it.copy(edits = it.edits.copy(overlayColorHex = colorHex)) }
        scheduleRender()
    }

    fun setOverlaySize(scale: Float) {
        _uiState.update { it.copy(edits = it.edits.copy(overlaySizeScale = scale)) }
        scheduleRender()
    }

    fun toggleBeforeAfter() {
        _uiState.update { it.copy(showOriginal = !it.showOriginal) }
    }

    fun resetEdits() {
        _uiState.update { it.copy(edits = PhotoEditState(), showOriginal = false) }
        scheduleRender()
    }

    private fun scheduleRender() {
        val src = _uiState.value.originalBitmap ?: return
        val currentEdits = _uiState.value.edits
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                PhotoProcessor.applyEdits(src, currentEdits)
            }
            _uiState.update { it.copy(previewBitmap = rendered) }
        }
    }

    fun generateAiPhoto(prompt: String, ratio: AiImageRatio) {
        if (prompt.isBlank() || _uiState.value.isAiBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiBusy = true, aiError = null, statusMessage = null) }
            try {
                val bmp = aiRepository.generateImage(prompt, ratio)
                _uiState.update {
                    it.copy(
                        originalBitmap = bmp,
                        previewBitmap = bmp,
                        edits = PhotoEditState(),
                        isAiBusy = false,
                        statusMessage = "Image IA générée en HD !"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAiBusy = false,
                        aiError = e.localizedMessage ?: "Échec de génération d'image IA."
                    )
                }
            }
        }
    }

    fun magicEditWithAi(instruction: String) {
        val src = _uiState.value.previewBitmap ?: _uiState.value.originalBitmap ?: return
        if (instruction.isBlank() || _uiState.value.isAiBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiBusy = true, aiError = null, statusMessage = null) }
            try {
                val edited = aiRepository.editImage(src, instruction)
                _uiState.update {
                    it.copy(
                        originalBitmap = edited,
                        previewBitmap = edited,
                        edits = PhotoEditState(),
                        isAiBusy = false,
                        statusMessage = "Retouche IA appliquée !"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAiBusy = false,
                        aiError = e.localizedMessage ?: "Échec de la retouche IA."
                    )
                }
            }
        }
    }

    fun exportPhoto() {
        val bmp = _uiState.value.previewBitmap ?: _uiState.value.originalBitmap ?: return
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, statusMessage = null, errorMessage = null) }
            try {
                val uri = PhotoProcessor.exportBitmapToMediaStore(
                    context = getApplication(),
                    bitmap = bmp,
                    title = _uiState.value.title
                )
                val s = _uiState.value
                projectRepository.saveProject(
                    title = s.title,
                    type = StudioProjectType.PHOTO,
                    summary = "${s.edits.filter.label} • ${s.edits.aspectRatio.label} • PNG HD",
                    exportedUri = uri.toString()
                )
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        statusMessage = "Photo HD exportée sans filigrane dans Images/Hydra Studio Bergama !"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = e.localizedMessage ?: "Erreur lors de l'export photo."
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null, aiError = null) }
    }
}
