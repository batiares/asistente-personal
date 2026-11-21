package com.asistente.personal.ai

import com.asistente.personal.httpPostJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Gemini decide si un mensaje de WhatsApp merece atención, entendiendo el contexto (no solo palabras). */
object GeminiFilter {
    // Rápido y gratuito primero; el más inteligente de respaldo.
    private val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash")
    @Volatile private var working = 0

    data class Verdict(val important: Boolean, val reason: String)

    fun prompt(chat: String, text: String, context: List<String>, keywords: List<String>): String = buildString {
        appendLine("Eres el filtro de WhatsApp de un usuario en México. Decide si el MENSAJE NUEVO merece su atención.")
        appendLine("IMPORTANTE: le piden algo o esperan su respuesta, hay fechas, horas o plazos, dinero o pagos,")
        appendLine("escuela o trabajo, salud o emergencias, planes que le afectan, o lo mencionan directamente.")
        appendLine("NO IMPORTANTE: memes, stickers, saludos, bromas, cadenas, publicidad o plática casual.")
        if (keywords.isNotEmpty()) appendLine("Temas que el usuario marcó como importantes: ${keywords.joinToString(", ")}.")
        appendLine("Chat: $chat")
        if (context.isNotEmpty()) {
            appendLine("Mensajes anteriores del chat (solo contexto):")
            context.forEach { appendLine("- ${it.take(200)}") }
        }
        appendLine("MENSAJE NUEVO: ${text.take(800)}")
        append("""Responde SOLO con JSON: {"important": true o false, "reason": "máximo 6 palabras en español"}""")
    }

    fun requestBody(prompt: String): String = JSONObject()
        .put("contents", JSONArray().put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        .put("generationConfig", JSONObject().put("temperature", 0))
        .toString()

    /** Toma el texto de la respuesta y extrae el primer objeto JSON (tolera ```json ... ``` alrededor). */
    fun parseVerdict(responseJson: String): Verdict {
        val parts = JSONObject(responseJson).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        val text = (0 until (parts?.length() ?: 0)).joinToString("") { parts!!.getJSONObject(it).optString("text") }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("Gemini no devolvió JSON")
        val o = JSONObject(text.substring(start, end + 1))
        if (!o.has("important")) throw IOException("Respuesta sin 'important'")
        return Verdict(o.getBoolean("important"), o.optString("reason").take(60))
    }

    /** Llamada de red: ejecutar fuera del hilo principal. */
    fun classify(apiKey: String, chat: String, text: String, context: List<String>, keywords: List<String>): Verdict {
        val body = requestBody(prompt(chat, text, context, keywords))
        var last: Exception? = null
        for (i in MODELS.indices.sortedBy { if (it == working) -1 else it }) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/${MODELS[i]}:generateContent"
                return parseVerdict(httpPostJson(url, body, mapOf("x-goog-api-key" to apiKey))).also { working = i }
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException(last?.message ?: "Gemini no respondió")
    }

    // shortcut: tope fijo de 12 consultas por minuto para no agotar la cuota gratuita en grupos muy activos;
    // al pasarse se usa el filtro de palabras clave. Subirlo si la clave tiene facturación.
    private val calls = ArrayDeque<Long>()

    @Synchronized
    fun allowCall(now: Long = System.currentTimeMillis(), perMinute: Int = 12): Boolean {
        while (calls.isNotEmpty() && now - calls.first() > 60_000) calls.removeFirst()
        if (calls.size >= perMinute) return false
        calls.addLast(now)
        return true
    }
}
