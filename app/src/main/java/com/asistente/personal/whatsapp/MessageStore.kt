package com.asistente.personal.whatsapp

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class WaMessage(val time: Long, val chat: String, val text: String, val important: Boolean)

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
            WaMessage(o.getLong("time"), o.getString("chat"), o.getString("text"), o.getBoolean("important"))
        }
    }

    /** Devuelve false si es un duplicado (WhatsApp vuelve a publicar la misma notificación al actualizarla). */
    @Synchronized
    fun add(ctx: Context, m: WaMessage): Boolean {
        val list = load(ctx)
        if (list.take(50).any { it.chat == m.chat && it.text == m.text }) return false
        val arr = JSONArray()
        (listOf(m) + list).take(MAX).forEach {
            arr.put(
                JSONObject().put("time", it.time).put("chat", it.chat)
                    .put("text", it.text).put("important", it.important)
            )
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
        return true
    }

    fun clear(ctx: Context) = prefs(ctx).edit().remove(KEY).apply()
}

/** Preferencias del filtro que el usuario edita en la pestaña de Mensajes. */
object FilterConfig {
    private const val DEFAULT_KEYWORDS = "urgente, importante, examen, pago, reunión, hoy, mañana"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun keywords(ctx: Context): String = prefs(ctx).getString("keywords", DEFAULT_KEYWORDS)!!
    fun vipChats(ctx: Context): String = prefs(ctx).getString("vip", "")!!
    fun hideUnimportant(ctx: Context): Boolean = prefs(ctx).getBoolean("hide", false)

    fun save(ctx: Context, keywords: String, vipChats: String, hideUnimportant: Boolean) =
        prefs(ctx).edit().putString("keywords", keywords).putString("vip", vipChats)
            .putBoolean("hide", hideUnimportant).apply()
}
