package com.asistente.personal.gmail

import com.asistente.personal.httpGet
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.net.URLEncoder

data class Email(val id: String, val from: String, val subject: String, val snippet: String) {
    /** "Juan Pérez <juan@x.com>" -> "Juan Pérez" */
    val fromName: String get() = from.substringBefore("<").trim().trim('"').ifEmpty { from }
    val webUrl: String get() = "https://mail.google.com/mail/u/0/#inbox/$id"
}

object GmailRepository {
    private const val SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"

    // Se reutiliza la clasificación "Importante" que Gmail ya hace sola.
    const val QUERY = "is:important is:unread newer_than:14d"

    fun authRequest(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Llamada de red: ejecutar fuera del hilo principal. */
    fun importantUnread(token: String, max: Int = 20): List<Email> {
        val auth = mapOf("Authorization" to "Bearer $token")
        val q = URLEncoder.encode(QUERY, "UTF-8")
        val ids = JSONObject(httpGet("$BASE/messages?q=$q&maxResults=$max", auth))
            .optJSONArray("messages") ?: return emptyList()
        return (0 until ids.length()).map { i ->
            val id = ids.getJSONObject(i).getString("id")
            parseMessage(JSONObject(httpGet(
                "$BASE/messages/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject", auth)))
        }
    }

    fun parseMessage(m: JSONObject): Email {
        val headers = m.optJSONObject("payload")?.optJSONArray("headers")
        fun header(name: String): String {
            if (headers == null) return ""
            for (i in 0 until headers.length()) {
                val h = headers.getJSONObject(i)
                if (h.optString("name").equals(name, ignoreCase = true)) return h.optString("value")
            }
            return ""
        }
        return Email(
            id = m.getString("id"),
            from = header("From"),
            subject = header("Subject").ifEmpty { "(sin asunto)" },
            snippet = m.optString("snippet"),
        )
    }
}
