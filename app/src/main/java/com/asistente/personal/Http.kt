package com.asistente.personal

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** GET sencillo que devuelve el cuerpo como texto o lanza IOException si el servidor responde con error. */
fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IOException("HTTP $code ${err.take(200)}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
