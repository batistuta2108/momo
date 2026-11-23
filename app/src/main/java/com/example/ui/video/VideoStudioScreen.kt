package com.example.ui.video

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.engine.PhotoFilterPreset
import com.example.engine.VideoAspectRatio
import com.example.engine.VideoComposerEngine
import com.example.engine.VideoTransition
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
fun VideoStudioScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoStudioViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var aiPrompt by rememberSaveable { mutableStateOf("") }

    val multiMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50),
        onResult = { uris -> viewModel.addMediaUris(uris) }
    )

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.attachAudio(it, it.lastPathSegment) }
        }
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
                VideoTopHeader(
                    state = state,
                    onTitleChange = viewModel::updateTitle,
                    onAspectSelect = viewModel::setAspectRatio,
                    onExportMp4 = viewModel::exportMp4
                )
            }

            if (state.exportProgress != null) {
                item {
                    val pct = ((state.exportProgress ?: 0f) * 100).toInt()
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, StudioAmber),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Encodage MP4 H.264 HD : $pct%",
                                style = MaterialTheme.typography.labelLarge,
                                color = StudioAmber
                            )
                            LinearProgressIndicator(
                                progress = { state.exportProgress ?: 0f },
                                color = StudioAmber,
                                trackColor = StudioSurfaceHigh,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (state.statusMessage != null || state.errorMessage != null) {
                item {
                    val isErr = state.errorMessage != null
                    Surface(
                        color = if (isErr) StudioCoral.copy(alpha = 0.16f) else StudioEmerald.copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, if (isErr) StudioCoral else StudioEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.errorMessage ?: state.statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isErr) StudioCoral else StudioEmerald,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                VideoPreviewMonitor(
                    state = state,
                    onTogglePlay = viewModel::togglePlayback,
                    onSeek = viewModel::seekTo,
                    onAddMedia = {
                        multiMediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            multiMediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioCyan,
                            contentColor = StudioObsidian
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("video_add_clips_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ajouter Photos/Vidéos")
                    }

                    OutlinedButton(
                        onClick = { audioPicker.launch(arrayOf("audio/*")) },
                        border = BorderStroke(1.dp, StudioAmber),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("video_attach_audio_button")
                    ) {
                        Icon(Icons.Filled.AudioFile, contentDescription = "Ajouter musique", tint = StudioAmber)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Musique", color = StudioAmber)
                    }
                }
            }

            if (state.audioLabel != null) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.AudioFile, contentDescription = null, tint = StudioAmber)
                                Text(
                                    text = state.audioLabel.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = viewModel::removeAudio,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Retirer l'audio", tint = StudioTextSecondary)
                            }
                        }
                    }
                }
            }

            if (state.clips.isNotEmpty()) {
                item {
                    TimelineClipsStrip(
                        state = state,
                        onSelectClip = viewModel::selectClip
                    )
                }

                item {
                    val activeClip = state.clips.getOrNull(state.selectedClipIndex)
                    if (activeClip != null) {
                        ClipInspectorCard(
                            clip = activeClip,
                            index = state.selectedClipIndex,
                            totalCount = state.clips.size,
                            onDurationChange = viewModel::updateSelectedClipDuration,
                            onTransitionSelect = viewModel::updateSelectedClipTransition,
                            onFilterSelect = viewModel::updateSelectedClipFilter,
                            onCaptionChange = viewModel::updateSelectedClipCaption,
                            onMove = viewModel::moveSelectedClip,
                            onDelete = viewModel::removeSelectedClip
                        )
                    }
                }
            }

            item {
                AiStoryboardCard(
                    prompt = aiPrompt,
                    onPromptChange = { aiPrompt = it },
                    storyboardState = state.storyboardState,
                    onGenerate = { viewModel.generateStoryboard(aiPrompt) }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun VideoTopHeader(
    state: VideoStudioUiState,
    onTitleChange: (String) -> Unit,
    onAspectSelect: (VideoAspectRatio) -> Unit,
    onExportMp4: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text("Nom du projet vidéo") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("video_title_input")
                )

                Button(
                    onClick = onExportMp4,
                    enabled = state.clips.isNotEmpty() && state.exportProgress == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioAmber,
                        contentColor = StudioObsidian
                    ),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("video_export_mp4_button")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Exporter MP4")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("MP4")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VideoAspectRatio.entries) { ratio ->
                    FilterChip(
                        selected = state.aspectRatio == ratio,
                        onClick = { onAspectSelect(ratio) },
                        label = { Text(ratio.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioAmber.copy(alpha = 0.24f),
                            selectedLabelColor = StudioAmber
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewMonitor(
    state: VideoStudioUiState,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onAddMedia: () -> Unit
) {
    val ratioFloat = state.aspectRatio.width.toFloat() / state.aspectRatio.height.toFloat()
    val totalSec = (state.totalDurationMs / 1000L).toInt()
    val currentSec = (state.positionMs / 1000L).toInt()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.clips.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .clickable(onClick = onAddMedia)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MovieCreation,
                            contentDescription = null,
                            tint = StudioAmber,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = "Ajoutez des photos ou vidéos sans limite de durée",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary
                        )
                    }
                } else {
                    Canvas(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .aspectRatio(ratioFloat)
                    ) {
                        drawIntoCanvas { canvas ->
                            VideoComposerEngine.renderTimelineFrame(
                                canvas = canvas.nativeCanvas,
                                width = size.width.toInt().coerceAtLeast(1),
                                height = size.height.toInt().coerceAtLeast(1),
                                clips = state.clips,
                                bitmaps = state.clipBitmaps,
                                positionMs = state.positionMs
                            )
                        }
                    }
                }
            }

            if (state.clips.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("video_play_button")
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Lire",
                            tint = StudioCyan
                        )
                    }

                    Slider(
                        value = state.positionMs.toFloat(),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..state.totalDurationMs.coerceAtLeast(1000L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = StudioCyan,
                            activeTrackColor = StudioCyan
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${formatTime(currentSec)} / ${formatTime(totalSec)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineClipsStrip(
    state: VideoStudioUiState,
    onSelectClip: (Int) -> Unit
) {
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
                text = "Timeline (${state.clips.size} plans • durée illimitée)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.clips, key = { _, c -> c.id }) { idx, clip ->
                    val isSelected = idx == state.selectedClipIndex
                    val thumb = state.clipBitmaps[clip.id]
                    Box(
                        modifier = Modifier
                            .size(width = 86.dp, height = 72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) StudioCyan else StudioBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectClip(idx) }
                            .testTag("timeline_clip_$idx")
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = "Plan ${idx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Surface(
                            color = Color(0xBB0A0B10),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "#${idx + 1} • ${clip.durationSec}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipInspectorCard(
    clip: com.example.engine.VideoClipItem,
    index: Int,
    totalCount: Int,
    onDurationChange: (Int) -> Unit,
    onTransitionSelect: (VideoTransition) -> Unit,
    onFilterSelect: (PhotoFilterPreset) -> Unit,
    onCaptionChange: (String) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Plan #${index + 1} • ${clip.durationSec}s",
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioCyan
                )
                Row {
                    IconButton(
                        onClick = { onMove(-1) },
                        enabled = index > 0,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Déplacer à gauche")
                    }
                    IconButton(
                        onClick = { onMove(1) },
                        enabled = index < totalCount - 1,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Déplacer à droite")
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Supprimer ce plan", tint = StudioCoral)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Durée", style = MaterialTheme.typography.bodyMedium, color = StudioTextSecondary)
                Slider(
                    value = clip.durationSec.toFloat(),
                    onValueChange = { onDurationChange(it.toInt()) },
                    valueRange = 1f..60f,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioAmber,
                        activeTrackColor = StudioAmber
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text("${clip.durationSec}s", style = MaterialTheme.typography.labelMedium, color = StudioAmber)
            }

            Text("Transition d'entrée", style = MaterialTheme.typography.labelLarge, color = StudioTextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VideoTransition.entries) { tr ->
                    FilterChip(
                        selected = clip.transition == tr,
                        onClick = { onTransitionSelect(tr) },
                        label = { Text(tr.label) }
                    )
                }
            }

            Text("Filtre colorimétrique", style = MaterialTheme.typography.labelLarge, color = StudioTextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PhotoFilterPreset.entries) { flt ->
                    FilterChip(
                        selected = clip.filter == flt,
                        onClick = { onFilterSelect(flt) },
                        label = { Text(flt.label) }
                    )
                }
            }

            OutlinedTextField(
                value = clip.caption,
                onValueChange = onCaptionChange,
                label = { Text("Sous-titre / Texte sur ce plan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AiStoryboardCard(
    prompt: String,
    onPromptChange: (String) -> Unit,
    storyboardState: AiStoryboardUiState,
    onGenerate: () -> Unit
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = StudioViolet)
                Text(
                    text = "Réalisateur & Storyboard IA",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                label = { Text("Thème vidéo (ex: Clip voyage nocturne à Istanbul)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_ai_prompt_input"),
                maxLines = 2
            )

            Button(
                onClick = onGenerate,
                enabled = storyboardState !is AiStoryboardUiState.Loading && prompt.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioViolet,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("video_ai_generate_button")
            ) {
                if (storyboardState is AiStoryboardUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Création du découpage IA...")
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Générer Storyboard & Sous-titres IA")
                }
            }

            when (storyboardState) {
                is AiStoryboardUiState.Error -> {
                    Text(
                        text = "Erreur : ${storyboardState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioCoral
                    )
                }
                is AiStoryboardUiState.Success -> {
                    Surface(
                        color = StudioSurface,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = storyboardState.storyboard.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = StudioAmber
                            )
                            Text(
                                text = "Accroche : ${storyboardState.storyboard.hook}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioCyan
                            )
                            storyboardState.storyboard.scenes.forEachIndexed { idx, scene ->
                                Text(
                                    text = "${idx + 1}. ${scene.title} (${scene.durationSec}s) — ${scene.cameraMove}\n« ${scene.caption} »",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
