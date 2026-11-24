package com.example.ui.photo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
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

enum class StudioCanvasStyle(val label: String, val colors: IntArray) {
    CYBER_NEON("Cyber Néon", intArrayOf(Color.rgb(8, 14, 34), Color.rgb(0, 180, 216), Color.rgb(123, 44, 191))),
    SUNSET_GOLD("Coucher Doré", intArrayOf(Color.rgb(34, 12, 38), Color.rgb(214, 69, 80), Color.rgb(255, 183, 3))),
    EMERALD_DARK("Émeraude", intArrayOf(Color.rgb(6, 24, 24), Color.rgb(16, 185, 129), Color.rgb(6, 95, 70))),
    OBSIDIAN_MINIMAL("Noir Studio", intArrayOf(Color.rgb(12, 14, 20), Color.rgb(34, 40, 62), Color.rgb(12, 14, 20)))
}

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

    init {
        loadStarterCanvas()
    }

    private fun loadStarterCanvas() {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                BitmapFactory.decodeResource(
                    getApplication<Application>().resources,
                    R.drawable.img_studio_hero_1795396503160
                )
            }
            if (bmp != null) {
                _uiState.update {
                    it.copy(originalBitmap = bmp, previewBitmap = bmp)
                }
            }
        }
    }

    fun createBlankStudioCanvas(style: StudioCanvasStyle) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                val w = 1080
                val h = 1080
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, w.toFloat(), h.toFloat(),
                        style.colors,
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                out
            }
            _uiState.update {
                it.copy(
                    originalBitmap = bmp,
                    edits = PhotoEditState(overlayText = "HYDRA STUDIO"),
                    showOriginal = false,
                    statusMessage = "Toile « ${style.label} » prête à éditer."
                )
            }
            scheduleRender()
        }
    }

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

    fun applyAiColorGrading(prompt: String) {
        if (prompt.isBlank() || _uiState.value.isAiBusy) return
        if (_uiState.value.originalBitmap == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiBusy = true, aiError = null, statusMessage = null) }
            try {
                val advice = aiRepository.generatePhotoGradingAdvice(prompt)
                _uiState.update { state ->
                    state.copy(
                        isAiBusy = false,
                        edits = state.edits.copy(
                            filter = advice.filter,
                            brightness = advice.brightness,
                            contrast = advice.contrast,
                            saturation = advice.saturation,
                            warmth = advice.warmth,
                            vignette = advice.vignette,
                            overlayText = advice.overlayText.ifBlank { state.edits.overlayText }
                        ),
                        statusMessage = "Étalonnage & titre IA « ${advice.filter.label} » appliqués !"
                    )
                }
                scheduleRender()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAiBusy = false,
                        aiError = e.localizedMessage ?: "Échec de l'étalonnage IA."
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
}
