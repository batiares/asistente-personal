package com.asistente.personal

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** GET sencillo que devuelve el cuerpo como texto o lanza IOException si el servidor responde con error. */
fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String =
    httpRequest(url, headers = headers)

/** POST con cuerpo JSON. Timeout largo: generar audio puede tardar. */
fun httpPostJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String =
    httpRequest(url, "POST", json, headers + ("Content-Type" to "application/json"), readTimeoutMs = 90_000)

private fun httpRequest(
    url: String,
    method: String = "GET",
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
    readTimeoutMs: Int = 15_000,
): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = readTimeoutMs
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IOException("HTTP $code ${err.take(300)}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
