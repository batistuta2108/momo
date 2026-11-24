package com.example.ui.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VoiceOverOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.SunoCreateMode
import com.example.engine.SunoSongLength
import com.example.engine.SunoSongTrack
import com.example.engine.SunoStarterLibrary
import com.example.engine.SunoVoicePersona
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
fun SunoCreatorCard(
    state: MusicStudioUiState,
    viewModel: MusicStudioViewModel
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioViolet.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = StudioCyan)
                    Text(
                        text = "Créer un morceau (Suno)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SunoCreateMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.sunoCreateMode == mode,
                            onClick = { viewModel.setSunoCreateMode(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioCyan.copy(alpha = 0.22f),
                                selectedLabelColor = StudioCyan
                            )
                        )
                    }
                }
            }

            if (state.sunoCreateMode == SunoCreateMode.SIMPLE) {
                OutlinedTextField(
                    value = state.simplePrompt,
                    onValueChange = viewModel::updateSimplePrompt,
                    label = { Text("Décrivez votre chanson (style, ambiance, sujet, voix...)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("suno_simple_prompt_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SunoStarterLibrary.quickPrompts) { suggestion ->
                        FilterChip(
                            selected = state.simplePrompt == suggestion,
                            onClick = { viewModel.updateSimplePrompt(suggestion) },
                            label = {
                                Text(
                                    text = suggestion.take(28) + if (suggestion.length > 28) "..." else "",
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.customTitle,
                    onValueChange = viewModel::updateCustomTitle,
                    label = { Text("Titre du morceau") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("suno_custom_title_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Paroles & Structure",
                            style = MaterialTheme.typography.labelLarge,
                            color = StudioTextSecondary
                        )
                        OutlinedButton(
                            onClick = viewModel::generateLyricsWithAi,
                            enabled = !state.isLyricsAiBusy,
                            border = BorderStroke(1.dp, StudioViolet),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("suno_ai_lyrics_button")
                        ) {
                            if (state.isLyricsAiBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = StudioViolet
                                )
                            } else {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = StudioViolet,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paroles IA", color = StudioViolet)
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val tags = listOf("Intro", "Couplet 1", "Refrain", "Couplet 2", "Pont", "Outro")
                        items(tags) { tag ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.appendLyricsSectionTag(tag) },
                                label = { Text("+ [$tag]") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.customLyrics,
                        onValueChange = viewModel::updateCustomLyrics,
                        label = { Text("Écrivez vos paroles ou cliquez sur + [Refrain] / Paroles IA") },
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("suno_custom_lyrics_input")
                    )
                }

                OutlinedTextField(
                    value = state.customStyleTags,
                    onValueChange = viewModel::updateCustomStyleTags,
                    label = { Text("Style de musique (ex: Afro-Trap, 808, Rap Français)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("suno_custom_style_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SunoStarterLibrary.styleSuggestions) { tag ->
                        FilterChip(
                            selected = state.customStyleTags.contains(tag, ignoreCase = true),
                            onClick = { viewModel.toggleStyleChip(tag) },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Instrumental uniquement",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = state.isInstrumental,
                    onCheckedChange = viewModel::setInstrumental,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StudioObsidian,
                        checkedTrackColor = StudioCyan
                    ),
                    modifier = Modifier.testTag("suno_instrumental_switch")
                )
            }

            if (!state.isInstrumental) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SunoVoicePersona.entries) { persona ->
                        FilterChip(
                            selected = state.voicePersona == persona,
                            onClick = { viewModel.setVoicePersona(persona) },
                            label = { Text(persona.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioAmber.copy(alpha = 0.24f),
                                selectedLabelColor = StudioAmber
                            )
                        )
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SunoSongLength.entries) { len ->
                    FilterChip(
                        selected = state.songLength == len,
                        onClick = { viewModel.setSongLength(len) },
                        label = { Text(len.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioEmerald.copy(alpha = 0.22f),
                            selectedLabelColor = StudioEmerald
                        )
                    )
                }
            }

            if (state.sunoAiError != null) {
                Surface(
                    color = StudioCoral.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, StudioCoral),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.sunoAiError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioCoral,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = viewModel::createSunoTrackWithAi,
                    enabled = !state.isSunoAiBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioViolet,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("suno_create_ai_button")
                ) {
                    if (state.isSunoAiBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Création IA...", maxLines = 1)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Créer avec IA", maxLines = 1)
                    }
                }

                Button(
                    onClick = viewModel::createSunoTrackOffline,
                    enabled = !state.isSunoAiBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioCyan,
                        contentColor = StudioObsidian
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("suno_create_offline_button")
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Studio Direct", maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SunoNowPlayingCard(
    song: SunoSongTrack,
    state: MusicStudioUiState,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLiveTts: () -> Unit,
    onExportWav: () -> Unit,
    onOpenInDaw: () -> Unit
) {
    val accent = Color(song.accentColorHex)
    val totalSec = (state.songTotalMs / 1000L).toInt().coerceAtLeast(1)
    val curSec = (state.songPosMs / 1000L).toInt().coerceAtLeast(0)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SunoCoverThumbnail(
                    accent = accent,
                    isPlaying = state.isPlayingSong,
                    modifier = Modifier.size(72.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (song.isAiGenerated) StudioViolet else accent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = when {
                                    song.vocalPcm44k != null -> "SUNO IA + VOIX"
                                    song.isAiGenerated -> "SUNO IA"
                                    else -> "STUDIO PRO"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (song.isAiGenerated) Color.White else accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "${song.bpm} BPM • ${song.musicalKey}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioAmber
                        )
                    }

                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.styleTags,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .testTag("suno_play_button")
                ) {
                    Icon(
                        imageVector = if (state.isPlayingSong) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlayingSong) "Pause" else "Lire la chanson",
                        tint = StudioObsidian,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Timeline & Seek
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Slider(
                    value = state.songPosMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..state.songTotalMs.coerceAtLeast(1000L).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatSongDuration(curSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary
                    )
                    val curSecName = song.sections.getOrNull(state.activeSectionIndex)?.name ?: ""
                    if (curSecName.isNotEmpty()) {
                        Text(
                            text = curSecName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = formatSongDuration(totalSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary
                    )
                }
            }

            // Section flow pills
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(song.sections.indices.toList()) { idx ->
                    val sec = song.sections[idx]
                    val isCurrent = idx == state.activeSectionIndex
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCurrent) accent.copy(alpha = 0.24f) else StudioSurfaceHigh,
                        border = BorderStroke(1.dp, if (isCurrent) accent else StudioBorder)
                    ) {
                        Text(
                            text = "${sec.name} (${sec.bars}m)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent) accent else StudioTextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Action toolbar: Voice TTS toggle, Export WAV, Edit in DAW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onToggleLiveTts,
                    border = BorderStroke(1.dp, if (state.liveTtsEnabled) StudioEmerald else StudioBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("suno_tts_toggle_button")
                ) {
                    Icon(
                        imageVector = if (state.liveTtsEnabled) Icons.Filled.RecordVoiceOver else Icons.Filled.VoiceOverOff,
                        contentDescription = null,
                        tint = if (state.liveTtsEnabled) StudioEmerald else StudioTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (state.liveTtsEnabled) "Voix ON" else "Voix OFF",
                        color = if (state.liveTtsEnabled) StudioEmerald else StudioTextMuted
                    )
                }

                OutlinedButton(
                    onClick = onOpenInDaw,
                    border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("suno_open_daw_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = StudioCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("DAW", color = StudioCyan)
                }

                Button(
                    onClick = onExportWav,
                    enabled = !state.isExporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioAmber,
                        contentColor = StudioObsidian
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("suno_export_wav_button")
                ) {
                    if (state.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = StudioObsidian
                        )
                    } else {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WAV Complet", maxLines = 1)
                    }
                }
            }

            // Synchronized Karaoke Lyrics Display
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Paroles & Structure (${song.chords.joinToString(" - ")})",
                        style = MaterialTheme.typography.labelLarge,
                        color = StudioAmber
                    )
                    song.sections.forEachIndexed { idx, sec ->
                        val active = idx == state.activeSectionIndex
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) accent.copy(alpha = 0.12f) else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "[${sec.name}]",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) accent else StudioTextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            if (sec.lyrics.isNotBlank()) {
                                Text(
                                    text = sec.lyrics,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (active) Color.White else StudioTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SunoLibraryCard(
    songs: List<SunoSongTrack>,
    activeSongId: String,
    isPlaying: Boolean,
    onSelect: (String) -> Unit,
    onRemix: (SunoSongTrack) -> Unit
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
                text = "Bibliothèque Suno (${songs.size} morceaux)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            songs.forEach { song ->
                val selected = song.id == activeSongId
                val accent = Color(song.accentColorHex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accent.copy(alpha = 0.12f) else StudioSurface)
                        .border(
                            width = 1.dp,
                            color = if (selected) accent else StudioBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect(song.id) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SunoCoverThumbnail(
                        accent = accent,
                        isPlaying = selected && isPlaying,
                        modifier = Modifier.size(48.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${song.styleTags} • ${formatSongDuration(song.durationSec)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { onRemix(song) },
                        border = BorderStroke(1.dp, StudioBorder),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "Remixer",
                            tint = accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remix", color = accent, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SunoCoverThumbnail(
    accent: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(StudioObsidian, accent.copy(alpha = 0.65f), StudioViolet.copy(alpha = 0.7f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = size.minDimension * 0.38f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = accent.copy(alpha = if (isPlaying) 0.9f else 0.55f),
                radius = size.minDimension * 0.16f,
                center = Offset(cx, cy)
            )
        }
    }
}

private fun formatSongDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
