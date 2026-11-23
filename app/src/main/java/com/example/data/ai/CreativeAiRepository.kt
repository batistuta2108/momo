package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
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

enum class AiImageRatio(val label: String) {
    SQUARE("1:1"),
    LANDSCAPE("16:9")
}

class CreativeAiRepository {

    private fun ensureFirebaseReady() {
        val ready = runCatching { FirebaseApp.getInstance() }.isSuccess
        if (!ready) {
            throw IllegalStateException(
                "IA Cloud indisponible dans cette build (google-services.json absent lors du build GitHub). " +
                    "Tous les studios de création locale (Musique WAV, Photo HD, Vidéo MP4) fonctionnent sans limite."
            )
        }
    }

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
