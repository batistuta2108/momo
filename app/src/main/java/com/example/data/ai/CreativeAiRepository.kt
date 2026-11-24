package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.engine.AudioSynthEngine
import com.example.engine.MusicalScale
import com.example.engine.PhotoFilterPreset
import com.example.engine.SongSection
import com.example.engine.SunoSongLength
import com.example.engine.SunoSongTrack
import com.example.engine.SunoVoicePersona
import com.example.engine.SynthWaveform
import com.example.engine.TrackType
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "CreativeAiRepo"

data class AiSongPlan(
    val title: String,
    val genre: String,
    val bpm: Int,
    val musicalKey: String,
    val chords: String,
    val lyrics: String,
    val kickSteps: List<Boolean>,
    val snareSteps: List<Boolean>,
    val hihatSteps: List<Boolean>,
    val bassSteps: List<Boolean>,
    val leadSteps: List<Boolean>
)

data class AiStoryboardScene(
    val title: String,
    val durationSec: Int,
    val cameraMove: String,
    val visualDirection: String,
    val caption: String
)

data class AiVideoStoryboard(
    val title: String,
    val hook: String,
    val musicMood: String,
    val scenes: List<AiStoryboardScene>
)

data class AiPhotoGradingAdvice(
    val filter: PhotoFilterPreset,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val vignette: Float,
    val overlayText: String
)

enum class AiImageRatio(val label: String) {
    SQUARE("1:1"),
    LANDSCAPE("16:9")
}

class CreativeAiRepository {

    private fun ensureFirebaseReady() {
        val ready = runCatching { FirebaseApp.getInstance() }.isSuccess
        if (!ready) {
            throw IllegalStateException(
                "IA Cloud non initialisée (google-services.json absent dans cette build). " +
                    "Utilisez les studios hors-ligne ou ajoutez app/google-services.json sur GitHub."
            )
        }
    }

    private val sunoSongSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string("Titre accrocheur du morceau"),
            "styleTags" to Schema.string("Tags de style musical séparés par des virgules"),
            "bpm" to Schema.integer("Tempo BPM réaliste entre 70 et 170"),
            "musicalKey" to Schema.string("Tonalité musicale, ex: La Mineur"),
            "waveform" to Schema.enumeration(listOf("SINE", "TRIANGLE", "SQUARE", "SAW"), "Type de synthé"),
            "scale" to Schema.enumeration(listOf("MINOR", "MAJOR", "PHRYGIAN", "PENTATONIC"), "Gamme musicale"),
            "chords" to Schema.array(Schema.string(), "4 accords principaux ex: Am, F, C, G"),
            "sections" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string("Nom de section: Intro, Couplet 1, Refrain, Pont, Refrain Final, Outro"),
                        "bars" to Schema.integer("Nombre de mesures (2 à 8)"),
                        "energy" to Schema.double("Intensité énergétique de 0.35 à 1.0"),
                        "lyrics" to Schema.string("Paroles chantées ou description courte de cette section"),
                        "drumsEnabled" to Schema.boolean("Batterie active dans cette section"),
                        "bassEnabled" to Schema.boolean("Basse active dans cette section"),
                        "chordsEnabled" to Schema.boolean("Accords actifs dans cette section"),
                        "leadEnabled" to Schema.boolean("Synthé lead actif dans cette section"),
                        "arpEnabled" to Schema.boolean("Arpégiateur actif dans cette section")
                    )
                ),
                description = "Structure complète du morceau en 5 à 6 sections"
            ),
            "kickSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour le kick"),
            "snareSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour la caisse claire"),
            "hihatSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour le charleston"),
            "clapSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour le clap"),
            "bassSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour la basse"),
            "leadSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour la mélodie synthé")
        )
    )

    private val lyricsSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string("Titre de la chanson"),
            "lyrics" to Schema.string("Paroles complètes avec [Intro], [Couplet 1], [Refrain], [Couplet 2], [Pont], [Outro]")
        )
    )

    private val songSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string("Titre du morceau"),
            "genre" to Schema.string("Genre musical"),
            "bpm" to Schema.integer("Tempo BPM entre 60 et 180"),
            "musicalKey" to Schema.string("Tonalité musicale, ex: Do Mineur"),
            "chords" to Schema.string("Suite d'accords principale"),
            "lyrics" to Schema.string("Paroles complètes structurées avec Couplet 1, Refrain, Couplet 2, Pont"),
            "kickSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour le kick"),
            "snareSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour la caisse claire"),
            "hihatSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour le charleston"),
            "bassSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour la basse"),
            "leadSteps" to Schema.array(Schema.boolean(), "Exactement 16 pas pour la mélodie synthé")
        )
    )

    private val storyboardSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string("Titre du projet vidéo"),
            "hook" to Schema.string("Accroche des 3 premières secondes"),
            "musicMood" to Schema.string("Ambiance musicale recommandée"),
            "scenes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string("Nom de la scène"),
                        "durationSec" to Schema.integer("Durée suggérée en secondes"),
                        "cameraMove" to Schema.string("Mouvement caméra ou transition"),
                        "visualDirection" to Schema.string("Description visuelle du plan"),
                        "caption" to Schema.string("Sous-titre ou texte à l'écran")
                    )
                ),
                description = "Liste ordonnée des scènes du montage"
            )
        )
    )

    private val photoGradingSchema = Schema.obj(
        properties = mapOf(
            "filter" to Schema.enumeration(
                PhotoFilterPreset.entries.map { it.name },
                "Preset colorimétrique le plus adapté"
            ),
            "brightness" to Schema.double("Luminosité entre -50 et 50"),
            "contrast" to Schema.double("Contraste entre 0.7 et 1.5"),
            "saturation" to Schema.double("Saturation entre 0.4 et 1.7"),
            "warmth" to Schema.double("Chaleur entre -0.6 et 0.6"),
            "vignette" to Schema.double("Vignette entre 0.0 et 0.8"),
            "overlayText" to Schema.string("Court titre cinématique stylé (2-4 mots)")
        )
    )

    private val sunoModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = sunoSongSchema
            }
        )
    }

    private val lyricsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = lyricsSchema
            }
        )
    }

    private val songModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = songSchema
            }
        )
    }

    private val storyboardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            }
        )
    }

    private val photoGradingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = photoGradingSchema
            }
        )
    }

    private val squareImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val landscapeImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )
    }

    suspend fun generateSunoFullSong(
        prompt: String,
        customLyrics: String?,
        styleTags: String?,
        titleHint: String?,
        isInstrumental: Boolean,
        voicePersona: SunoVoicePersona,
        length: SunoSongLength
    ): Pair<SunoSongTrack, Boolean> {
        try {
            ensureFirebaseReady()
            val mult = length.barMultiplier
            val fullPrompt = buildString {
                appendLine("Tu es Suno Studio IA, un producteur musical complet de niveau mondial.")
                appendLine("Crée une chanson complète structurée en sections (Intro, Couplet 1, Refrain, Couplet 2, Pont, Outro).")
                if (!titleHint.isNullOrBlank()) appendLine("Titre souhaité : $titleHint")
                if (!styleTags.isNullOrBlank()) appendLine("Style musical : $styleTags")
                if (prompt.isNotBlank()) appendLine("Description / Ambiance : $prompt")
                if (!customLyrics.isNullOrBlank() && !isInstrumental) {
                    appendLine("Utilise et adapte ces paroles exactes dans les sections :\n$customLyrics")
                }
                appendLine("Instrumental uniquement : $isInstrumental.")
                appendLine("Longueur des couplets/refrains : environ ${4 * mult} mesures par section.")
                appendLine("Génère 4 accords cohérents (ex: Am, F, C, G), un BPM réaliste, et 16 pas booléens par piste rythmique/mélodique.")
            }

            val response = withTimeout(60_000L) {
                sunoModel.generateContent(fullPrompt)
            }
            val jsonString = response.text
                ?: throw IllegalStateException("Le modèle Suno IA n'a renvoyé aucune réponse.")

            val json = JSONObject(jsonString)
            fun readSteps(key: String): List<Boolean> {
                val arr = json.optJSONArray(key)
                return List(16) { idx ->
                    if (arr != null && idx < arr.length()) arr.optBoolean(idx, false) else false
                }
            }

            val chordsArr = json.optJSONArray("chords")
            val chords = buildList {
                if (chordsArr != null) {
                    for (i in 0 until chordsArr.length()) {
                        val c = chordsArr.optString(i).trim()
                        if (c.isNotEmpty()) add(c)
                    }
                }
            }.ifEmpty { listOf("Am", "F", "C", "G") }

            val secArr = json.optJSONArray("sections")
            val sections = buildList {
                if (secArr != null) {
                    for (i in 0 until secArr.length()) {
                        val o = secArr.optJSONObject(i) ?: continue
                        add(
                            SongSection(
                                name = o.optString("name", "Section ${i + 1}"),
                                bars = (o.optInt("bars", 4) * mult).coerceIn(2, 16),
                                energy = o.optDouble("energy", 0.8).toFloat().coerceIn(0.35f, 1.0f),
                                lyrics = o.optString("lyrics", ""),
                                drumsEnabled = o.optBoolean("drumsEnabled", true),
                                bassEnabled = o.optBoolean("bassEnabled", true),
                                chordsEnabled = o.optBoolean("chordsEnabled", true),
                                leadEnabled = o.optBoolean("leadEnabled", true),
                                arpEnabled = o.optBoolean("arpEnabled", true)
                            )
                        )
                    }
                }
            }.ifEmpty {
                listOf(
                    SongSection("Intro", 2 * mult, 0.45f, "(Intro)", drumsEnabled = false),
                    SongSection("Couplet 1", 4 * mult, 0.78f, customLyrics ?: prompt),
                    SongSection("Refrain", 4 * mult, 1.0f, customLyrics ?: prompt),
                    SongSection("Outro", 2 * mult, 0.42f, "(Outro)", drumsEnabled = false, bassEnabled = false)
                )
            }

            val wfName = json.optString("waveform", "SAW")
            val waveform = SynthWaveform.entries.firstOrNull { it.name == wfName } ?: SynthWaveform.SAW
            val scName = json.optString("scale", "MINOR")
            val scale = MusicalScale.entries.firstOrNull { it.name == scName } ?: MusicalScale.MINOR

            val grid = mapOf(
                TrackType.KICK to readSteps("kickSteps"),
                TrackType.SNARE to readSteps("snareSteps"),
                TrackType.HIHAT to readSteps("hihatSteps"),
                TrackType.CLAP to readSteps("clapSteps"),
                TrackType.BASS to readSteps("bassSteps"),
                TrackType.LEAD to readSteps("leadSteps")
            )

            val assembledLyrics = sections.joinToString("\n\n") { "[${it.name}]\n${it.lyrics}" }
            val title = json.optString("title", titleHint?.ifBlank { null } ?: "Création Suno IA")
            val tags = json.optString("styleTags", styleTags?.ifBlank { null } ?: "Suno AI Studio")

            // Try generating real Gemini AI vocal performance with gemini-3.1-flash-tts-preview (no billing required)
            var vocalPcm44k: ShortArray? = null
            var vocalReady = false
            if (!isInstrumental) {
                val singableLines = sections
                    .filter { sec ->
                        val n = sec.name.lowercase()
                        "couplet" in n || "refrain" in n || "chorus" in n || "pont" in n
                    }
                    .joinToString("\n") { it.lyrics }
                    .trim()
                    .take(550)

                if (singableLines.isNotBlank()) {
                    vocalPcm44k = runCatching {
                        generateVocalTtsPcm(
                            lyrics = singableLines,
                            styleTags = tags,
                            voicePersona = voicePersona
                        )
                    }.getOrNull()
                    vocalReady = vocalPcm44k != null && vocalPcm44k.isNotEmpty()
                }
            }

            val accents = listOf(0xFF00E5FF, 0xFF8E63FF, 0xFFFFB300, 0xFF10E0A0)
            val accent = accents[kotlin.math.abs(title.hashCode()) % accents.size]

            val track = SunoSongTrack(
                title = title,
                styleTags = tags,
                bpm = json.optInt("bpm", 122).coerceIn(65, 180),
                musicalKey = json.optString("musicalKey", "La Mineur"),
                chords = chords,
                waveform = waveform,
                scale = scale,
                swing = 0.08f,
                isInstrumental = isInstrumental,
                voicePersona = voicePersona,
                sections = sections,
                fullLyrics = assembledLyrics,
                grid = grid,
                vocalPcm44k = vocalPcm44k,
                accentColorHex = accent,
                isAiGenerated = true
            )
            return track to vocalReady
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Suno AI generation timed out", e)
            throw IllegalStateException("Délai dépassé après 60s lors de la création Suno IA.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Suno AI generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Échec de la création Suno IA.", e)
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private suspend fun generateVocalTtsPcm(
        lyrics: String,
        styleTags: String,
        voicePersona: SunoVoicePersona
    ): ShortArray? {
        val ttsConfig = generationConfig {
            responseModalities = listOf(ResponseModality.AUDIO)
            speechConfig = SpeechConfig(
                voice = Voice(voicePersona.geminiVoiceName)
            )
        }
        val ttsModel = Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-tts-preview",
            generationConfig = ttsConfig
        )
        val cue = "Perform these song lyrics expressively with musical rhythm, emotion and vocal presence in the style of $styleTags:\n$lyrics"
        val response = withTimeout(45_000L) {
            ttsModel.generateContent(cue)
        }
        val part = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<InlineDataPart>()
            ?.firstOrNull() ?: return null
        return AudioSynthEngine.resample24kTo44k(part.inlineData)
    }

    suspend fun generateSunoLyricsOnly(topic: String, styleTags: String): Pair<String, String> {
        try {
            ensureFirebaseReady()
            val prompt = """
                Écris des paroles de chanson originales et percutantes (en français ou selon la demande) sur le thème : "$topic" (Style : $styleTags).
                Structure obligatoirement avec des balises : [Intro], [Couplet 1], [Refrain], [Couplet 2], [Pont], [Outro].
            """.trimIndent()
            val response = withTimeout(45_000L) {
                lyricsModel.generateContent(prompt)
            }
            val json = JSONObject(
                response.text ?: throw IllegalStateException("Réponse vide du parolier IA.")
            )
            return json.optString("title", "Nouvelle Chanson") to json.optString("lyrics", "")
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Délai dépassé pour la génération des paroles IA.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(e.localizedMessage ?: "Échec de génération des paroles IA.", e)
        }
    }

    suspend fun generateSongPlan(prompt: String): AiSongPlan {
        try {
            ensureFirebaseReady()
            val fullPrompt = """
                Tu es un producteur musical pro. Crée une composition complète en français (ou dans la langue demandée) pour :
                "$prompt"
                Génère un rythme cohérent à 16 pas (exactement 16 booléens par piste : kickSteps, snareSteps, hihatSteps, bassSteps, leadSteps), un BPM réaliste entre 65 et 175, une suite d'accords et des paroles complètes.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                songModel.generateContent(fullPrompt)
            }
            val jsonString = response.text
                ?: throw IllegalStateException("Le modèle n'a renvoyé aucune réponse.")

            val json = JSONObject(jsonString)
            fun readSteps(key: String): List<Boolean> {
                val arr = json.optJSONArray(key)
                return List(16) { idx ->
                    if (arr != null && idx < arr.length()) arr.optBoolean(idx, false) else false
                }
            }

            return AiSongPlan(
                title = json.optString("title", "Composition Studio"),
                genre = json.optString("genre", "Électro"),
                bpm = json.optInt("bpm", 120).coerceIn(60, 190),
                musicalKey = json.optString("musicalKey", "La Mineur"),
                chords = json.optString("chords", "Am - F - C - G"),
                lyrics = json.optString("lyrics", ""),
                kickSteps = readSteps("kickSteps"),
                snareSteps = readSteps("snareSteps"),
                hihatSteps = readSteps("hihatSteps"),
                bassSteps = readSteps("bassSteps"),
                leadSteps = readSteps("leadSteps")
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Song generation timed out", e)
            throw IllegalStateException("Délai dépassé après 60s lors de la création musicale.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Song generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Échec de la génération musicale.", e)
        }
    }

    suspend fun generateVideoStoryboard(prompt: String): AiVideoStoryboard {
        try {
            ensureFirebaseReady()
            val fullPrompt = """
                Tu es un réalisateur et monteur vidéo pro. Crée un storyboard structuré en français pour le projet vidéo :
                "$prompt"
                Propose 4 à 6 scènes dynamiques avec mouvement caméra, durée en secondes, direction visuelle et sous-titres percutants.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                storyboardModel.generateContent(fullPrompt)
            }
            val jsonString = response.text
                ?: throw IllegalStateException("Le modèle n'a renvoyé aucune réponse.")

            val json = JSONObject(jsonString)
            val scenesArr = json.optJSONArray("scenes")
            val scenes = buildList {
                if (scenesArr != null) {
                    for (i in 0 until scenesArr.length()) {
                        val obj = scenesArr.optJSONObject(i) ?: continue
                        add(
                            AiStoryboardScene(
                                title = obj.optString("title", "Scène ${i + 1}"),
                                durationSec = obj.optInt("durationSec", 4).coerceIn(2, 60),
                                cameraMove = obj.optString("cameraMove", "Zoom lent"),
                                visualDirection = obj.optString("visualDirection", ""),
                                caption = obj.optString("caption", "")
                            )
                        )
                    }
                }
            }

            return AiVideoStoryboard(
                title = json.optString("title", "Montage Vidéo"),
                hook = json.optString("hook", ""),
                musicMood = json.optString("musicMood", "Cinématique moderne"),
                scenes = scenes
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Storyboard generation timed out", e)
            throw IllegalStateException("Délai dépassé après 60s pour le storyboard.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Storyboard generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Échec de la génération du storyboard.", e)
        }
    }

    suspend fun generatePhotoGradingAdvice(prompt: String): AiPhotoGradingAdvice {
        try {
            ensureFirebaseReady()
            val response = withTimeout(45_000L) {
                photoGradingModel.generateContent(
                    "Tu es un directeur photo et étalonneur cinéma. Propose des réglages colorimétriques et un court titre à incruster pour : \"$prompt\""
                )
            }
            val json = JSONObject(
                response.text ?: throw IllegalStateException("Réponse vide du coloriste IA.")
            )
            val filterName = json.optString("filter", PhotoFilterPreset.CINEMA.name)
            val preset = PhotoFilterPreset.entries.firstOrNull { it.name == filterName }
                ?: PhotoFilterPreset.CINEMA
            return AiPhotoGradingAdvice(
                filter = preset,
                brightness = json.optDouble("brightness", 0.0).toFloat().coerceIn(-60f, 60f),
                contrast = json.optDouble("contrast", 1.15).toFloat().coerceIn(0.6f, 1.6f),
                saturation = json.optDouble("saturation", 1.1).toFloat().coerceIn(0.2f, 1.8f),
                warmth = json.optDouble("warmth", 0.1).toFloat().coerceIn(-0.8f, 0.8f),
                vignette = json.optDouble("vignette", 0.25).toFloat().coerceIn(0f, 0.9f),
                overlayText = json.optString("overlayText", "")
            )
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Délai dépassé pour l'étalonnage IA.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(e.localizedMessage ?: "Échec de l'étalonnage IA.", e)
        }
    }

    suspend fun generateImage(prompt: String, ratio: AiImageRatio): Bitmap {
        try {
            ensureFirebaseReady()
            val model = when (ratio) {
                AiImageRatio.SQUARE -> squareImageModel
                AiImageRatio.LANDSCAPE -> landscapeImageModel
            }
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            return imagePart?.image
                ?: throw IllegalStateException("Aucune image renvoyée par le modèle.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            throw IllegalStateException("La génération d'image a expiré après 60 secondes.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Échec de la génération d'image IA.", e)
        }
    }

    suspend fun editImage(inputBitmap: Bitmap, instruction: String): Bitmap {
        try {
            ensureFirebaseReady()
            val response = withTimeout(60_000L) {
                squareImageModel.generateContent(
                    content {
                        image(inputBitmap)
                        text(instruction)
                    }
                )
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            return imagePart?.image
                ?: throw IllegalStateException("Aucune image retouchée renvoyée par le modèle.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image edit timed out", e)
            throw IllegalStateException("La retouche IA a expiré après 60 secondes.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image edit failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Échec de la retouche photo IA.", e)
        }
    }
}
