package com.asistente.personal.whatsapp

import java.text.Normalizer

object ImportanceFilter {
    private val ACENTOS = Regex("\\p{Mn}+")

    /** Minúsculas y sin acentos, para que "Reunión" == "reunion". */
    fun normalize(s: String): String =
        ACENTOS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

    fun parseList(s: String): List<String> = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Importante si viene de un chat VIP o el texto contiene alguna palabra clave. */
    fun isImportant(chat: String, text: String, keywords: List<String>, vipChats: List<String>): Boolean {
        val c = normalize(chat)
        val t = normalize(text)
        return vipChats.any { c.contains(normalize(it)) } || keywords.any { t.contains(normalize(it)) }
    }

    // WhatsApp publica avisos como "5 mensajes nuevos" o "Buscando mensajes nuevos" que no son mensajes reales.
    private val AVISOS = Regex(
        "^\\d+ (mensajes nuevos|new messages|correos nuevos|new emails)|buscando mensajes|checking for new messages",
        RegexOption.IGNORE_CASE,
    )

    fun isSystemNotice(text: String): Boolean = AVISOS.containsMatchIn(text.trim())
}
