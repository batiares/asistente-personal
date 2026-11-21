package com.asistente.personal.voice

import android.content.Context
import com.asistente.personal.httpPostJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Voz natural con Gemini TTS (https://ai.google.dev/gemini-api/docs/speech-generation). */
object GeminiVoice {
    /** Modelos a intentar en orden. `legacy` = formato de petición de los modelos TTS anteriores a 3.8. */
    private data class Attempt(val model: String, val legacy: Boolean)
    private val ATTEMPTS = listOf(
        Attempt("gemini-3.8-flash-tts", legacy = false),
        Attempt("gemini-3.8-flash-lite-tts", legacy = false),
        Attempt("gemini-2.5-flash-preview-tts", legacy = true),
        Attempt("gemini-3.1-flash-tts-preview", legacy = true),
    )
    // Recuerda el último modelo que funcionó para empezar por él.
    @Volatile private var working = 0

    private const val STYLE = "warm, natural and friendly personal assistant, conversational pace"
    const val SAMPLE_RATE = 24_000

    /** Voces prediseñadas de Gemini con su descripción. */
    val VOICES = listOf(
        "Sulafat" to "Cálida", "Achird" to "Amigable", "Kore" to "Firme",
        "Aoede" to "Ligera", "Puck" to "Animada", "Charon" to "Informativa",
    )
    const val DEFAULT_VOICE = "Sulafat"

    private fun url(model: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    fun requestBody(text: String, voice: String, legacy: Boolean = false): String {
        val part = if (legacy) JSONObject().put("text", "Say in a $STYLE tone: $text")
        else JSONObject().put("text", text).put("speech_metadata", JSONObject().put("style", STYLE))
        val voiceConfig = if (legacy) JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))
        else JSONObject().put("voice", voice)
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(part))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", voiceConfig)))
            .toString()
    }

    /** Llamada de red: ejecutar fuera del hilo principal. Devuelve un WAV listo para reproducir. */
    fun synthesize(text: String, apiKey: String, voice: String): ByteArray {
        var last: Exception? = null
        val order = ATTEMPTS.indices.sortedBy { if (it == working) -1 else it }
        for (i in order) {
            val a = ATTEMPTS[i]
            try {
                val json = httpPostJson(url(a.model), requestBody(text, voice, a.legacy), mapOf("x-goog-api-key" to apiKey))
                return ensureWav(extractAudio(json)).also { working = i }
            } catch (e: Exception) {
                last = e
                if (isBadKey(e)) break // con una clave inválida ningún modelo va a funcionar
            }
        }
        throw IOException(friendlyError(last))
    }

    private fun isBadKey(e: Exception) = e.message.orEmpty().let { "API_KEY_INVALID" in it || "API key not valid" in it }

    fun friendlyError(e: Exception?): String {
        val m = e?.message.orEmpty()
        return when {
            "API_KEY_INVALID" in m || "API key not valid" in m -> "La clave de Gemini no es válida. Revísala en ⚙."
            "HTTP 429" in m -> "Se acabó la cuota gratuita de Gemini por ahora. Intenta más tarde."
            "HTTP 403" in m -> "Tu clave no tiene permiso para la voz de Gemini (${m.take(120)})."
            m.isEmpty() -> "Error desconocido de Gemini."
            else -> m.take(160)
        }
    }

    /** Busca el audio en cualquier parte de la respuesta (a veces viene texto antes). */
    fun extractAudio(json: String): ByteArray {
        val parts = JSONObject(json).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        for (i in 0 until (parts?.length() ?: 0)) {
            val data = parts!!.getJSONObject(i).optJSONObject("inlineData")?.optString("data").orEmpty()
            if (data.isNotEmpty()) return Base64.getDecoder().decode(data)
        }
        throw IOException("Gemini respondió sin audio")
    }

    /**
     * Devuelve un WAV con cabecera correcta. Si llega PCM crudo se le agrega; si llega WAV se rehace la
     * cabecera (algunos WAV de streaming traen tamaños inválidos que MediaPlayer rechaza).
     */
    fun ensureWav(bytes: ByteArray, defaultRate: Int = SAMPLE_RATE): ByteArray {
        if (bytes.size >= 12 && ascii(bytes, 0) == "RIFF" && ascii(bytes, 8) == "WAVE") {
            val rate = if (bytes.size >= 28 && ascii(bytes, 12) == "fmt ") le32(bytes, 24) else defaultRate
            var p = 12
            while (p + 8 <= bytes.size) {
                if (ascii(bytes, p) == "data") return wav(bytes.copyOfRange(p + 8, bytes.size), rate)
                p += 8 + le32(bytes, p + 4).coerceAtLeast(0)
            }
            return bytes
        }
        return wav(bytes, defaultRate)
    }

    private fun ascii(b: ByteArray, at: Int) = if (at + 4 <= b.size) String(b, at, 4, Charsets.US_ASCII) else ""
    private fun le32(b: ByteArray, at: Int) = ByteBuffer.wrap(b, at, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1) // PCM, mono
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }.array()
        return header + pcm
    }
}

/** Clave y voz elegidas. La clave se guarda SOLO en este teléfono (nunca va en el código ni en GitHub). */
object VoiceConfig {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("voice", Context.MODE_PRIVATE)
    fun apiKey(ctx: Context): String = prefs(ctx).getString("key", "")!!
    fun voice(ctx: Context): String = prefs(ctx).getString("voice", GeminiVoice.DEFAULT_VOICE)!!
    fun save(ctx: Context, apiKey: String, voice: String) =
        prefs(ctx).edit().putString("key", apiKey.trim()).putString("voice", voice).apply()
}
