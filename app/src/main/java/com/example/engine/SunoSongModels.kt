package com.example.engine

import java.util.UUID

enum class SunoCreateMode(val label: String) {
    SIMPLE("Simple"),
    CUSTOM("Personnalisé")
}

enum class SunoSongLength(val label: String, val barMultiplier: Int) {
    COMPACT("Court (~45s)", 1),
    FULL("Complet (~1m30)", 2),
    EXTENDED("Étendu (~2m30)", 3)
}

enum class SunoVoicePersona(val label: String, val geminiVoiceName: String, val pitch: Float, val speechRate: Float) {
    MALE_DEEP("Voix Homme", "Charon", 0.85f, 0.92f),
    MALE_RAP("Flow Rap / Urbain", "Fenrir", 0.95f, 1.12f),
    FEMALE_WARM("Voix Femme Soul", "Aoede", 1.15f, 0.92f),
    FEMALE_BRIGHT("Voix Femme Pop", "Zephyr", 1.25f, 1.0f),
    DUET("Chœur / Mixte", "Puck", 1.05f, 0.96f)
}

data class SongSection(
    val name: String,
    val bars: Int,
    val energy: Float,
    val lyrics: String,
    val drumsEnabled: Boolean = true,
    val bassEnabled: Boolean = true,
    val chordsEnabled: Boolean = true,
    val leadEnabled: Boolean = true,
    val arpEnabled: Boolean = false
)

data class SunoSongTrack(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val styleTags: String,
    val bpm: Int,
    val musicalKey: String,
    val chords: List<String>,
    val waveform: SynthWaveform,
    val scale: MusicalScale,
    val swing: Float,
    val isInstrumental: Boolean,
    val voicePersona: SunoVoicePersona,
    val sections: List<SongSection>,
    val fullLyrics: String,
    val grid: Map<TrackType, List<Boolean>>,
    val vocalPcm44k: ShortArray? = null,
    val accentColorHex: Long = 0xFF00E5FF,
    val isAiGenerated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalBars: Int
        get() = sections.sumOf { it.bars.coerceAtLeast(1) }

    val durationSec: Int
        get() {
            val steps = totalBars * 16
            val secPerStep = 60.0 / bpm.coerceIn(60, 200) / 4.0
            return (steps * secPerStep).toInt().coerceAtLeast(1)
        }
}

object SunoStarterLibrary {

    val styleSuggestions = listOf(
        "Afro-Trap Nocturne",
        "Rap Français Épique",
        "Synthwave 80s",
        "Lo-Fi Pluie Café",
        "Afrobeat Solaire",
        "Electro House Club",
        "Drill Sombre",
        "R&B Soul Doux"
    )

    val quickPrompts = listOf(
        "Afro-trap mélancolique nocturne avec 808 profonde et refrain entraînant",
        "Synthwave rétro années 80 conduite de nuit sous les néons",
        "Lo-fi chill pluie sur la fenêtre avec piano doux et basse chaude",
        "Rap français motivant sur le travail et la réussite, tempo 132 BPM"
    )

    fun starterTracks(): List<SunoSongTrack> = listOf(
        SunoSongTrack(
            id = "suno_starter_1",
            title = "Nuit sur Bergama",
            styleTags = "Afro-Trap, 808 Profonde, Synthé Spatial, Français",
            bpm = 128,
            musicalKey = "La Mineur",
            chords = listOf("Am", "F", "C", "G"),
            waveform = SynthWaveform.SAW,
            scale = MusicalScale.PHRYGIAN,
            swing = 0.08f,
            isInstrumental = false,
            voicePersona = SunoVoicePersona.MALE_RAP,
            sections = listOf(
                SongSection(
                    name = "Intro",
                    bars = 4,
                    energy = 0.45f,
                    lyrics = "(Ambiance nocturne, nappes synthétiques et basses profondes)",
                    drumsEnabled = false,
                    bassEnabled = true,
                    chordsEnabled = true,
                    leadEnabled = false,
                    arpEnabled = true
                ),
                SongSection(
                    name = "Couplet 1",
                    bars = 8,
                    energy = 0.75f,
                    lyrics = "Sous les lumières bleues la ville s'éveille encore,\nChaque note dans le noir dessine notre décor,\nOn avance sans frein sur le fil du tempo,\nHydra Studio allume les néons du studio.",
                    drumsEnabled = true,
                    bassEnabled = true,
                    chordsEnabled = true,
                    leadEnabled = false,
                    arpEnabled = true
                ),
                SongSection(
                    name = "Refrain",
                    bars = 8,
                    energy = 1.0f,
                    lyrics = "Monte le son jusqu'au ciel ce soir,\nAucune limite, on écrit l'histoire !\nLes basses vibrent fort sous les étoiles d'or,\nHydra Bergama résonne encore et encore.",
                    drumsEnabled = true,
                    bassEnabled = true,
                    chordsEnabled = true,
                    leadEnabled = true,
                    arpEnabled = true
                ),
                SongSection(
                    name = "Pont",
                    bars = 4,
                    energy = 0.58f,
                    lyrics = "Silence un instant, juste l'écho des accords,\nLe souffle remonte avant le dernier essor.",
                    drumsEnabled = false,
                    bassEnabled = true,
                    chordsEnabled = true,
                    leadEnabled = true,
                    arpEnabled = true
                ),
                SongSection(
                    name = "Refrain Final",
                    bars = 8,
                    energy = 1.0f,
                    lyrics = "Monte le son jusqu'au ciel ce soir,\nAucune limite, on écrit l'histoire !\nLes basses vibrent fort sous les étoiles d'or,\nHydra Bergama résonne encore et encore.",
                    drumsEnabled = true,
                    bassEnabled = true,
                    chordsEnabled = true,
                    leadEnabled = true,
                    arpEnabled = true
                ),
                SongSection(
                    name = "Outro",
                    bars = 4,
                    energy = 0.40f,
                    lyrics = "(Fondu progressif sur les nappes synthé)",
                    drumsEnabled = false,
                    bassEnabled = false,
                    chordsEnabled = true,
                    leadEnabled = true,
                    arpEnabled = false
                )
            ),
            fullLyrics = "[Intro]\n(Ambiance nocturne)\n\n[Couplet 1]\nSous les lumières bleues la ville s'éveille encore...\n\n[Refrain]\nMonte le son jusqu'au ciel ce soir,\nAucune limite, on écrit l'histoire !",
            grid = BeatPresets.all.first().grid,
            accentColorHex = 0xFF00E5FF,
            isAiGenerated = false
        ),
        SunoSongTrack(
            id = "suno_starter_2",
            title = "Horizon Néon 84",
            styleTags = "Synthwave, Rétro 80s, Arpèges, Instrumental",
            bpm = 118,
            musicalKey = "Ré Mineur",
            chords = listOf("Dm", "Bb", "F", "C"),
            waveform = SynthWaveform.SQUARE,
            scale = MusicalScale.MINOR,
            swing = 0.04f,
            isInstrumental = true,
            voicePersona = SunoVoicePersona.FEMALE_BRIGHT,
            sections = listOf(
                SongSection("Intro", 4, 0.5f, "(Nappes analogiques et arpège rétro)", drumsEnabled = false, bassEnabled = false, chordsEnabled = true, leadEnabled = false, arpEnabled = true),
                SongSection("Thème A", 8, 0.85f, "(Basse synthwave pulsatile)", drumsEnabled = true, bassEnabled = true, chordsEnabled = true, leadEnabled = true, arpEnabled = true),
                SongSection("Climax", 8, 1.0f, "(Solo synthé et batterie pleine puissance)", drumsEnabled = true, bassEnabled = true, chordsEnabled = true, leadEnabled = true, arpEnabled = true),
                SongSection("Outro", 4, 0.45f, "(Extinction douce des néons)", drumsEnabled = false, bassEnabled = true, chordsEnabled = true, leadEnabled = false, arpEnabled = true)
            ),
            fullLyrics = "[Instrumental Synthwave 80s]",
            grid = BeatPresets.all[3].grid,
            accentColorHex = 0xFF8E63FF,
            isAiGenerated = false
        )
    )

    fun buildLocalSunoTrack(
        titleInput: String,
        promptOrStyle: String,
        lyricsInput: String,
        isInstrumental: Boolean,
        voicePersona: SunoVoicePersona,
        length: SunoSongLength
    ): SunoSongTrack {
        val styleLower = promptOrStyle.lowercase()
        val preset = when {
            "lo-fi" in styleLower || "lofi" in styleLower || "chill" in styleLower || "pluie" in styleLower -> BeatPresets.all[1]
            "afro" in styleLower -> BeatPresets.all[2]
            "synth" in styleLower || "80" in styleLower || "house" in styleLower || "electro" in styleLower -> BeatPresets.all[3]
            else -> BeatPresets.all[0]
        }
        val chords = when {
            "lo-fi" in styleLower || "lofi" in styleLower -> listOf("Dm", "G", "C", "Am")
            "afro" in styleLower -> listOf("Em", "C", "G", "D")
            "synth" in styleLower || "house" in styleLower -> listOf("Dm", "Bb", "F", "C")
            "joye" in styleLower || "pop" in styleLower || "soleil" in styleLower -> listOf("C", "G", "Am", "F")
            else -> listOf("Am", "F", "C", "G")
        }
        val keyName = when (chords.first()) {
            "Dm" -> "Ré Mineur"
            "Em" -> "Mi Mineur"
            "C" -> "Do Majeur"
            else -> "La Mineur"
        }

        val mult = length.barMultiplier
        val parsedSections = parseSectionsFromLyrics(lyricsInput, mult, isInstrumental)
        val cleanTitle = titleInput.trim().ifBlank {
            promptOrStyle.trim().split(Regex("\\s+")).take(3).joinToString(" ")
                .replaceFirstChar { it.uppercase() }
                .ifBlank { "Création Suno Studio" }
        }
        val cleanTags = promptOrStyle.trim().ifBlank { "${preset.name}, Studio HD" }
        val accentColors = listOf(0xFF00E5FF, 0xFF8E63FF, 0xFFFFB300, 0xFF10E0A0, 0xFFFF5370)
        val accent = accentColors[kotlin.math.abs(cleanTitle.hashCode()) % accentColors.size]

        return SunoSongTrack(
            title = cleanTitle,
            styleTags = cleanTags,
            bpm = preset.bpm,
            musicalKey = keyName,
            chords = chords,
            waveform = preset.waveform,
            scale = preset.scale,
            swing = if ("lofi" in styleLower || "lo-fi" in styleLower) 0.16f else 0.07f,
            isInstrumental = isInstrumental,
            voicePersona = voicePersona,
            sections = parsedSections,
            fullLyrics = lyricsInput.ifBlank {
                parsedSections.joinToString("\n\n") { "[${it.name}]\n${it.lyrics}" }
            },
            grid = preset.grid,
            accentColorHex = accent,
            isAiGenerated = false
        )
    }

    private fun parseSectionsFromLyrics(
        rawLyrics: String,
        barMultiplier: Int,
        isInstrumental: Boolean
    ): List<SongSection> {
        if (rawLyrics.isBlank()) {
            return listOf(
                SongSection("Intro", 2 * barMultiplier, 0.45f, if (isInstrumental) "(Intro instrumentale)" else "(Intro atmosphérique)", drumsEnabled = false, bassEnabled = true, chordsEnabled = true, leadEnabled = false, arpEnabled = true),
                SongSection("Couplet 1", 4 * barMultiplier, 0.76f, if (isInstrumental) "(Couplet mélodique)" else "Rythme posé dans la nuit claire,\nChaque mesure résonne sur terre.", drumsEnabled = true, bassEnabled = true, chordsEnabled = true, leadEnabled = false, arpEnabled = true),
                SongSection("Refrain", 4 * barMultiplier, 1.0f, if (isInstrumental) "(Refrain pleine puissance)" else "Hydra Studio fait vibrer le son,\nSans limite à l'horizon !", drumsEnabled = true, bassEnabled = true, chordsEnabled = true, leadEnabled = true, arpEnabled = true),
                SongSection("Pont", 2 * barMultiplier, 0.58f, if (isInstrumental) "(Pont mélodique)" else "Respiration avant l'envol final.", drumsEnabled = false, bassEnabled = true, chordsEnabled = true, leadEnabled = true, arpEnabled = true),
                SongSection("Refrain Final", 4 * barMultiplier, 1.0f, if (isInstrumental) "(Final énergétique)" else "Hydra Studio fait vibrer le son,\nSans limite à l'horizon !", drumsEnabled = true, bassEnabled = true, chordsEnabled = true, leadEnabled = true, arpEnabled = true),
                SongSection("Outro", 2 * barMultiplier, 0.40f, "(Clôture progressive)", drumsEnabled = false, bassEnabled = false, chordsEnabled = true, leadEnabled = true, arpEnabled = false)
            )
        }

        val regex = Regex("""\[([^\]]+)]""")
        val matches = regex.findAll(rawLyrics).toList()
        if (matches.isEmpty()) {
            val lines = rawLyrics.lines().filter { it.isNotBlank() }
            val mid = (lines.size / 2).coerceAtLeast(1)
            val part1 = lines.take(mid).joinToString("\n")
            val part2 = lines.drop(mid).joinToString("\n").ifBlank { part1 }
            return listOf(
                SongSection("Intro", 2 * barMultiplier, 0.45f, "(Mise en place)", drumsEnabled = false, bassEnabled = true, chordsEnabled = true, leadEnabled = false, arpEnabled = true),
                SongSection("Couplet", 4 * barMultiplier, 0.78f, part1, drumsEnabled = true, bassEnabled = true, chordsEnabled = true, leadEnabled = false, arpEnabled = true),
                SongSection("Refrain", 4 * barMultiplier, 1.0f, part2, drumsEnabled = true, bassEnabled = true, chordsEnabled = true, leadEnabled = true, arpEnabled = true),
                SongSection("Outro", 2 * barMultiplier, 0.42f, "(Outro)", drumsEnabled = false, bassEnabled = false, chordsEnabled = true, leadEnabled = true, arpEnabled = false)
            )
        }

        return matches.mapIndexed { idx, match ->
            val name = match.groupValues[1].trim()
            val start = match.range.last + 1
            val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else rawLyrics.length
            val text = rawLyrics.substring(start, end).trim()
            val lower = name.lowercase()
            when {
                "intro" in lower -> SongSection(name, 2 * barMultiplier, 0.45f, text.ifBlank { "(Intro)" }, drumsEnabled = false, bassEnabled = true, chordsEnabled = true, leadEnabled = false, arpEnabled = true)
                "outro" in lower -> SongSection(name, 2 * barMultiplier, 0.40f, text.ifBlank { "(Outro)" }, drumsEnabled = false, bassEnabled = false, chordsEnabled = true, leadEnabled = true, arpEnabled = false)
                "pont" in lower || "bridge" in lower -> SongSection(name, 2 * barMultiplier, 0.58f, text.ifBlank { "(Pont)" }, drumsEnabled = false, bassEnabled = true, chordsEnabled = true, leadEnabled = true, arpEnabled = true)
                "refrain" in lower || "chorus" in lower || "drop" in lower -> SongSection(name, 4 * barMultiplier, 1.0f, text, drumsEnabled = true, bassEnabled = true, chordsEnabled = true, leadEnabled = true, arpEnabled = true)
                else -> SongSection(name, 4 * barMultiplier, 0.78f, text, drumsEnabled = true, bassEnabled = true, chordsEnabled = true, leadEnabled = false, arpEnabled = true)
            }
        }
    }
}
