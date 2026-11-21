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
    // Según la documentación de Google, el modelo TTS de mayor fidelidad y expresividad.
    const val MODEL = "gemini-3.8-flash-tts"
    private const val URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    private const val STYLE = "warm, natural and friendly personal assistant, conversational pace"
    const val SAMPLE_RATE = 24_000

    /** Voces prediseñadas de Gemini con su descripción. */
    val VOICES = listOf(
        "Sulafat" to "Cálida", "Achird" to "Amigable", "Kore" to "Firme",
        "Aoede" to "Ligera", "Puck" to "Animada", "Charon" to "Informativa",
    )
    const val DEFAULT_VOICE = "Sulafat"

    fun requestBody(text: String, voice: String): String = JSONObject()
        .put("contents", JSONArray().put(JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(JSONObject()
                .put("text", text)
                .put("speech_metadata", JSONObject().put("style", STYLE))))))
        .put("generationConfig", JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", JSONObject()
                .put("voiceConfig", JSONObject().put("voice", voice))))
        .toString()

    /** Llamada de red: ejecutar fuera del hilo principal. Devuelve un WAV listo para reproducir. */
    fun synthesize(text: String, apiKey: String, voice: String): ByteArray =
        ensureWav(extractAudio(httpPostJson(URL, requestBody(text, voice), mapOf("x-goog-api-key" to apiKey))))

    fun extractAudio(json: String): ByteArray {
        val data = JSONObject(json).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
            ?.optJSONObject("inlineData")?.optString("data").orEmpty()
        if (data.isEmpty()) throw IOException("Gemini no devolvió audio")
        return Base64.getDecoder().decode(data)
    }

    /** El modelo actual ya manda WAV; si llega PCM crudo (modelos anteriores) se le agrega la cabecera. */
    fun ensureWav(bytes: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        if (bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") return bytes
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1) // PCM, mono
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(bytes.size)
        }.array()
        return header + bytes
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
