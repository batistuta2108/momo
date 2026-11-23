package com.example.ui.photo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ai.AiImageRatio
import com.example.engine.PhotoAspectRatio
import com.example.engine.PhotoFilterPreset
import com.example.engine.TextPosition
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHigh
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioViolet

@Composable
fun PhotoStudioScreen(
    modifier: Modifier = Modifier,
    viewModel: PhotoStudioViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var aiPrompt by rememberSaveable { mutableStateOf("") }
    var aiRatio by rememberSaveable { mutableStateOf(AiImageRatio.SQUARE) }
    var selectedToolTab by rememberSaveable { mutableIntStateOf(0) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.loadFromUri(it) } }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        border = BorderStroke(1.dp, StudioCyan),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("photo_pick_button")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Importer une photo", tint = StudioCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importer", color = StudioCyan)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (state.originalBitmap != null) {
                        IconButton(
                            onClick = viewModel::toggleBeforeAfter,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("photo_compare_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Compare,
                                contentDescription = "Comparer Avant / Après",
                                tint = if (state.showOriginal) StudioAmber else StudioTextSecondary
                            )
                        }

                        IconButton(
                            onClick = viewModel::resetEdits,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("photo_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Réinitialiser les réglages",
                                tint = StudioTextSecondary
                            )
                        }

                        Button(
                            onClick = viewModel::exportPhoto,
                            enabled = !state.isExporting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioAmber,
                                contentColor = StudioObsidian
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("photo_export_button")
                        ) {
                            if (state.isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = StudioObsidian
                                )
                            } else {
                                Icon(Icons.Filled.Download, contentDescription = "Exporter photo HD")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Exporter HD")
                            }
                        }
                    }
                }
            }

            if (state.statusMessage != null || state.errorMessage != null || state.aiError != null) {
                item {
                    val errText = state.aiError ?: state.errorMessage
                    val isErr = errText != null
                    Surface(
                        color = if (isErr) StudioCoral.copy(alpha = 0.16f) else StudioEmerald.copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, if (isErr) StudioCoral else StudioEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errText ?: state.statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isErr) StudioCoral else StudioEmerald,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                PhotoCanvasPreview(
                    bitmap = if (state.showOriginal) state.originalBitmap else (state.previewBitmap ?: state.originalBitmap),
                    isAiBusy = state.isAiBusy,
                    showOriginalBadge = state.showOriginal,
                    onPickClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Créer une toile / pochette rapide",
                            style = MaterialTheme.typography.labelLarge,
                            color = StudioTextSecondary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(StudioCanvasStyle.entries) { style ->
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.createBlankStudioCanvas(style) },
                                    label = { Text(style.label) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                AiPhotoStudioCard(
                    prompt = aiPrompt,
                    onPromptChange = { aiPrompt = it },
                    selectedRatio = aiRatio,
                    onRatioSelect = { aiRatio = it },
                    hasActiveImage = state.originalBitmap != null,
                    isBusy = state.isAiBusy,
                    onGenerate = { viewModel.generateAiPhoto(aiPrompt, aiRatio) },
                    onMagicEdit = { viewModel.magicEditWithAi(aiPrompt) }
                )
            }

            if (state.originalBitmap != null) {
                item {
                    PhotoEditorDock(
                        state = state,
                        selectedTab = selectedToolTab,
                        onSelectTab = { selectedToolTab = it },
                        viewModel = viewModel
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PhotoCanvasPreview(
    bitmap: android.graphics.Bitmap?,
    isAiBusy: Boolean,
    showOriginalBadge: Boolean,
    onPickClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isAiBusy) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = StudioViolet)
                    Text(
                        text = "Création IA en cours (30-60s)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioViolet
                    )
                }
            } else if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Aperçu photo éditée",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
                if (showOriginalBadge) {
                    Surface(
                        color = StudioAmber,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "ORIGINAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioObsidian,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .clickable(onClick = onPickClick)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = StudioViolet,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = "Importez une photo ou créez une image avec l'IA ci-dessous",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AiPhotoStudioCard(
    prompt: String,
    onPromptChange: (String) -> Unit,
    selectedRatio: AiImageRatio,
    onRatioSelect: (AiImageRatio) -> Unit,
    hasActiveImage: Boolean,
    isBusy: Boolean,
    onGenerate: () -> Unit,
    onMagicEdit: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioViolet.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = StudioViolet)
                    Text(
                        text = "Studio Photo IA",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiImageRatio.entries.forEach { ratio ->
                        FilterChip(
                            selected = selectedRatio == ratio,
                            onClick = { onRatioSelect(ratio) },
                            label = { Text(ratio.label) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                label = { Text("Décrivez une image à créer ou une retouche magique") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_ai_prompt_input"),
                maxLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onGenerate,
                    enabled = !isBusy && prompt.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioViolet,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("photo_ai_generate_button")
                ) {
                    Text("Générer IA")
                }

                if (hasActiveImage) {
                    OutlinedButton(
                        onClick = onMagicEdit,
                        enabled = !isBusy && prompt.isNotBlank(),
                        border = BorderStroke(1.dp, StudioCyan),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("photo_ai_edit_button")
                    ) {
                        Text("Retoucher IA", color = StudioCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoEditorDock(
    state: PhotoStudioUiState,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    viewModel: PhotoStudioViewModel
) {
    val tabs = listOf("Filtres", "Réglages", "Format", "Texte")

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = StudioSurfaceHigh,
                contentColor = StudioCyan
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { onSelectTab(idx) },
                        text = { Text(label) }
                    )
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PhotoFilterPreset.entries) { preset ->
                                FilterChip(
                                    selected = state.edits.filter == preset,
                                    onClick = { viewModel.setFilter(preset) },
                                    label = { Text(preset.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = StudioCyan.copy(alpha = 0.22f),
                                        selectedLabelColor = StudioCyan
                                    )
                                )
                            }
                        }
                    }
                    1 -> {
                        EditorSlider("Luminosité", state.edits.brightness, -80f..80f, viewModel::setBrightness)
                        EditorSlider("Contraste", state.edits.contrast, 0.5f..1.8f, viewModel::setContrast)
                        EditorSlider("Saturation", state.edits.saturation, 0f..2f, viewModel::setSaturation)
                        EditorSlider("Chaleur", state.edits.warmth, -1f..1f, viewModel::setWarmth)
                        EditorSlider("Vignette", state.edits.vignette, 0f..1f, viewModel::setVignette)
                    }
                    2 -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PhotoAspectRatio.entries) { ratio ->
                                FilterChip(
                                    selected = state.edits.aspectRatio == ratio,
                                    onClick = { viewModel.setAspectRatio(ratio) },
                                    label = { Text(ratio.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = StudioAmber.copy(alpha = 0.22f),
                                        selectedLabelColor = StudioAmber
                                    )
                                )
                            }
                        }
                    }
                    3 -> {
                        OutlinedTextField(
                            value = state.edits.overlayText,
                            onValueChange = viewModel::setOverlayText,
                            label = { Text("Texte / Titre sur la photo") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("photo_overlay_text_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextPosition.entries.forEach { pos ->
                                FilterChip(
                                    selected = state.edits.overlayPosition == pos,
                                    onClick = { viewModel.setOverlayPosition(pos) },
                                    label = { Text(pos.label) }
                                )
                            }
                        }

                        val colors = listOf(0xFFFFFFFF, 0xFF00E5FF, 0xFFFFB300, 0xFFFF5370, 0xFF8E63FF, 0xFF0A0B10)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            colors.forEach { hex ->
                                val selected = state.edits.overlayColorHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) StudioCyan else StudioBorder,
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setOverlayColor(hex) }
                                )
                            }
                        }

                        EditorSlider("Taille du texte", state.edits.overlaySizeScale, 0.5f..2.2f, viewModel::setOverlaySize)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = StudioTextSecondary)
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.labelMedium,
                color = StudioCyan
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = StudioCyan,
                activeTrackColor = StudioCyan
            )
        )
    }
}
