package com.example.ui.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.engine.BeatPresets
import com.example.engine.MusicalScale
import com.example.engine.SynthWaveform
import com.example.engine.TrackType
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHigh
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioViolet

@Composable
fun MusicStudioScreen(
    modifier: Modifier = Modifier,
    viewModel: MusicStudioViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var aiPrompt by rememberSaveable { mutableStateOf("") }

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
                TransportControlCard(
                    state = state,
                    onTitleChange = viewModel::updateTitle,
                    onTogglePlay = viewModel::togglePlayback,
                    onBpmChange = viewModel::setBpm,
                    onExportBarsChange = viewModel::setExportBars,
                    onExportWav = viewModel::exportWav
                )
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
                PresetAndSynthConfigRow(
                    state = state,
                    onSelectPreset = viewModel::applyPreset,
                    onSelectWaveform = viewModel::setWaveform,
                    onSelectScale = viewModel::setScale,
                    onSwingChange = viewModel::setSwing
                )
            }

            item {
                SequencerGridCard(
                    grid = state.grid,
                    mutedTracks = state.mutedTracks,
                    currentStep = state.currentStep,
                    onToggleStep = viewModel::toggleStep,
                    onToggleMute = viewModel::toggleMute
                )
            }

            item {
                LiveSynthKeyboardCard(
                    waveform = state.waveform,
                    onTriggerNote = viewModel::triggerLiveNote
                )
            }

            item {
                AiSongwriterCard(
                    prompt = aiPrompt,
                    onPromptChange = { aiPrompt = it },
                    aiState = state.aiSongState,
                    onGenerate = { viewModel.generateWithAi(aiPrompt) }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TransportControlCard(
    state: MusicStudioUiState,
    onTitleChange: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onExportBarsChange: (Int) -> Unit,
    onExportWav: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text("Nom du morceau") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("music_title_input")
                )

                Button(
                    onClick = onTogglePlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isPlaying) StudioCoral else StudioCyan,
                        contentColor = StudioObsidian
                    ),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("music_play_button")
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Arrêter" else "Lire"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.isPlaying) "Stop" else "Lire")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onBpmChange(state.bpm - 2) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Baisser BPM", tint = StudioCyan)
                    }
                    Text(
                        text = "${state.bpm} BPM",
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioCyan
                    )
                    IconButton(
                        onClick = { onBpmChange(state.bpm + 2) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Augmenter BPM", tint = StudioCyan)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val barsOptions = listOf(4, 8, 16, 32, 64)
                    val nextBars = barsOptions[(barsOptions.indexOf(state.exportBars) + 1) % barsOptions.size]
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioSurfaceHigh,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onExportBarsChange(nextBars) }
                            .testTag("music_bars_selector")
                    ) {
                        Text(
                            text = "${state.exportBars} mes.",
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
                        )
                    }

                    Button(
                        onClick = onExportWav,
                        enabled = !state.isExporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioAmber,
                            contentColor = StudioObsidian
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("music_export_wav_button")
                    ) {
                        if (state.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = StudioObsidian
                            )
                        } else {
                            Icon(Icons.Filled.Download, contentDescription = "Exporter WAV")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WAV")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetAndSynthConfigRow(
    state: MusicStudioUiState,
    onSelectPreset: (com.example.engine.BeatPreset) -> Unit,
    onSelectWaveform: (SynthWaveform) -> Unit,
    onSelectScale: (MusicalScale) -> Unit,
    onSwingChange: (Float) -> Unit
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
            Text(
                text = "Styles & Synthétiseur",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BeatPresets.all) { preset ->
                    FilterChip(
                        selected = state.selectedPreset == preset.name,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioCyan.copy(alpha = 0.22f),
                            selectedLabelColor = StudioCyan
                        )
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SynthWaveform.entries) { wf ->
                    FilterChip(
                        selected = state.waveform == wf,
                        onClick = { onSelectWaveform(wf) },
                        label = { Text(wf.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioViolet.copy(alpha = 0.24f),
                            selectedLabelColor = StudioViolet
                        )
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MusicalScale.entries) { scale ->
                    FilterChip(
                        selected = state.scale == scale,
                        onClick = { onSelectScale(scale) },
                        label = { Text(scale.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioAmber.copy(alpha = 0.24f),
                            selectedLabelColor = StudioAmber
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Swing ${(state.swing * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioTextSecondary
                )
                Slider(
                    value = state.swing,
                    onValueChange = onSwingChange,
                    valueRange = 0f..0.45f,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioCyan,
                        activeTrackColor = StudioCyan
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SequencerGridCard(
    grid: Map<TrackType, List<Boolean>>,
    mutedTracks: Set<TrackType>,
    currentStep: Int,
    onToggleStep: (TrackType, Int) -> Unit,
    onToggleMute: (TrackType) -> Unit
) {
    val trackColors = mapOf(
        TrackType.KICK to StudioCyan,
        TrackType.SNARE to StudioAmber,
        TrackType.HIHAT to StudioEmerald,
        TrackType.CLAP to StudioCoral,
        TrackType.BASS to StudioViolet,
        TrackType.LEAD to Color(0xFF38BDF8)
    )

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Séquenceur 16 Pas",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (currentStep >= 0) "PAS ${currentStep + 1}/16" else "PRÊT",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioCyan
                )
            }

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackType.entries.forEach { track ->
                    val steps = grid[track] ?: List(16) { false }
                    val color = trackColors[track] ?: StudioCyan
                    val isMuted = track in mutedTracks

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .width(86.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioSurfaceHigh)
                                .clickable { onToggleMute(track) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = track.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isMuted) StudioTextMuted else color
                            )
                            Icon(
                                imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                contentDescription = "Muter ${track.displayName}",
                                tint = if (isMuted) StudioTextMuted else color,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        steps.forEachIndexed { idx, active ->
                            val isCurrent = idx == currentStep
                            val groupDividerPad = if (idx % 4 == 0 && idx > 0) 6.dp else 0.dp
                            Spacer(modifier = Modifier.width(groupDividerPad))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            active && !isMuted -> color.copy(alpha = if (isCurrent) 1f else 0.78f)
                                            active && isMuted -> color.copy(alpha = 0.25f)
                                            isCurrent -> StudioSurfaceHigh
                                            (idx / 4) % 2 == 0 -> StudioSurface
                                            else -> Color(0xFF161926)
                                        }
                                    )
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) Color.White else StudioBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .testTag("step_${track.name}_$idx")
                                    .clickable { onToggleStep(track, idx) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSynthKeyboardCard(
    waveform: SynthWaveform,
    onTriggerNote: (Int) -> Unit
) {
    val notes = listOf(
        60 to "Do",
        62 to "Ré",
        64 to "Mi",
        65 to "Fa",
        67 to "Sol",
        69 to "La",
        71 to "Si",
        72 to "Do5"
    )

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Clavier Synthé Live",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = waveform.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioViolet
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                notes.forEach { (midi, name) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(74.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioSurfaceHigh)
                            .border(1.dp, StudioViolet.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .testTag("synth_key_$midi")
                            .clickable { onTriggerNote(midi) },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioTextSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSongwriterCard(
    prompt: String,
    onPromptChange: (String) -> Unit,
    aiState: AiSongUiState,
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
                    text = "Compositeur & Parolier IA",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                label = { Text("Style, thème ou ambiance (ex: Afro-rap mélancolique nocturne)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music_ai_prompt_input"),
                maxLines = 3
            )

            Button(
                onClick = onGenerate,
                enabled = aiState !is AiSongUiState.Loading && prompt.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioViolet,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("music_ai_generate_button")
            ) {
                if (aiState is AiSongUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Composition du rythme et des paroles...")
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Composer Beat + Paroles IA")
                }
            }

            when (aiState) {
                is AiSongUiState.Error -> {
                    Text(
                        text = "Erreur : ${aiState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioCoral
                    )
                }
                is AiSongUiState.Success -> {
                    Surface(
                        color = StudioSurface,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${aiState.plan.title} • ${aiState.plan.bpm} BPM • ${aiState.plan.musicalKey}",
                                style = MaterialTheme.typography.titleMedium,
                                color = StudioCyan
                            )
                            Text(
                                text = "Accords : ${aiState.plan.chords}",
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioAmber
                            )
                            Text(
                                text = aiState.plan.lyrics,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
