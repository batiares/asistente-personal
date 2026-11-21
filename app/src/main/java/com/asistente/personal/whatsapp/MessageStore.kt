package com.asistente.personal.whatsapp

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** `reason`: por qué se marcó así ("Chat VIP", "Palabra clave", "IA: ..."). */
data class WaMessage(
    val time: Long,
    val chat: String,
    val text: String,
    val important: Boolean,
    val reason: String = "",
)

/** Mensajes de WhatsApp capturados de las notificaciones. */
val MessageStore = NotificationStore("whatsapp")

/** Correos capturados de las notificaciones de Gmail (no necesita Google Cloud). */
val MailStore = NotificationStore("gmail")

/** Guarda lo capturado SOLO en el teléfono (SharedPreferences). No se envía a ningún servidor. */
class NotificationStore(private val file: String) {
    private val KEY = "messages"
    private val MAX = 300

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<WaMessage> {
        val arr = runCatching { JSONArray(prefs(ctx).getString(KEY, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            WaMessage(o.getLong("time"), o.getString("chat"), o.getString("text"),
                o.getBoolean("important"), o.optString("reason"))
        }
    }

    private fun save(ctx: Context, list: List<WaMessage>) {
        val arr = JSONArray()
        list.take(MAX).forEach {
            arr.put(JSONObject().put("time", it.time).put("chat", it.chat).put("text", it.text)
                .put("important", it.important).put("reason", it.reason))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /** Devuelve false si es un duplicado (WhatsApp vuelve a publicar la misma notificación al actualizarla). */
    @Synchronized
    fun add(ctx: Context, m: WaMessage): Boolean {
        val list = load(ctx)
        if (list.take(50).any { it.chat == m.chat && it.text == m.text }) return false
        save(ctx, listOf(m) + list)
        return true
    }

    /** Cambia el mensaje que coincide (mismo momento, chat y texto); se usa cuando responde Gemini. */
    @Synchronized
    fun update(ctx: Context, target: WaMessage, change: (WaMessage) -> WaMessage) {
        val list = load(ctx)
        val i = list.indexOfFirst { it.time == target.time && it.chat == target.chat && it.text == target.text }
        if (i >= 0) save(ctx, list.toMutableList().also { it[i] = change(it[i]) })
    }

    fun clear(ctx: Context) = prefs(ctx).edit().remove(KEY).apply()
}

/** Preferencias del filtro que el usuario edita en la pestaña de Mensajes. */
object FilterConfig {
    private const val DEFAULT_KEYWORDS = "urgente, importante, examen, pago, reunión"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun keywords(ctx: Context): String = prefs(ctx).getString("keywords", DEFAULT_KEYWORDS)!!
    fun vipChats(ctx: Context): String = prefs(ctx).getString("vip", "")!!
    fun hideUnimportant(ctx: Context): Boolean = prefs(ctx).getBoolean("hide", false)
    /** Si hay clave de Gemini, la IA decide (las palabras clave quedan como pista y respaldo). */
    fun useAi(ctx: Context): Boolean = prefs(ctx).getBoolean("ai", true)

    fun save(ctx: Context, keywords: String, vipChats: String, hideUnimportant: Boolean, useAi: Boolean) =
        prefs(ctx).edit().putString("keywords", keywords).putString("vip", vipChats)
            .putBoolean("hide", hideUnimportant).putBoolean("ai", useAi).apply()
}
