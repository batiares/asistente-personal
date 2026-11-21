package com.asistente.personal.t1

import com.asistente.personal.httpGet
import org.json.JSONObject
import org.w3c.dom.Element
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** MVP de cada serie, tomado de Leaguepedia (lol.fandom.com), que registra el MVP oficial de la liga. */
object T1Mvp {
    private const val API = "https://lol.fandom.com/api.php"
    // Leaguepedia pide un User-Agent que identifique al cliente.
    private val HEADERS = mapOf("User-Agent" to "AsistentePersonal/0.1 (Android app)")
    private val UTC_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Llamada de red: ejecutar fuera del hilo principal. Devuelve hora de inicio (UTC) -> MVP. */
    fun fetch(team: String = T1Schedule.TEAM, limit: Int = 40): Map<Instant, String> {
        val where = "(Team1=\"$team\" OR Team2=\"$team\") AND MVP IS NOT NULL"
        val url = "$API?action=cargoquery&format=json&tables=MatchSchedule" +
            "&fields=MVP,DateTime_UTC&order_by=DateTime_UTC%20DESC&limit=$limit" +
            "&where=" + URLEncoder.encode(where, "UTF-8")
        return parse(httpGet(url, HEADERS))
    }

    fun parse(json: String): Map<Instant, String> {
        val rows = JSONObject(json).optJSONArray("cargoquery") ?: return emptyMap()
        val out = mutableMapOf<Instant, String>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i).getJSONObject("title")
            val name = cleanName(r.optString("MVP"))
            val time = r.optString("DateTime UTC")
            if (name == null || time.isEmpty()) continue
            out[LocalDateTime.parse(time, UTC_FMT).toInstant(ZoneOffset.UTC)] = name
        }
        return out
    }

    /** "Bin (Chen Ze-Bin)" -> "Bin"; "N/A" o vacío -> null. */
    fun cleanName(raw: String): String? {
        val n = raw.substringBefore("(").trim()
        return n.takeUnless { it.isEmpty() || it.equals("N/A", ignoreCase = true) }
    }

    /** Pega el MVP al partido que empezó a la misma hora (±6 h por si una fuente ajusta el horario). */
    fun attach(matches: List<Match>, mvps: Map<Instant, String>): List<Match> = matches.map { m ->
        val hit = mvps.entries.minByOrNull { Duration.between(it.key, m.start).abs() }
        if (m.finished && hit != null && Duration.between(hit.key, m.start).abs() <= Duration.ofHours(6))
            m.copy(mvp = hit.value) else m
    }
}

data class NewsItem(val title: String, val source: String, val link: String, val published: Instant)

/** Noticias recientes de T1 desde Google News (español, México). */
object T1News {
    private const val URL = "https://news.google.com/rss/search?" +
        "q=T1+Faker+OR+%22T1+LoL%22+OR+%22T1+LCK%22&hl=es-419&gl=MX&ceid=MX:es-419"

    /** Llamada de red: ejecutar fuera del hilo principal. */
    fun fetch(max: Int = 8): List<NewsItem> = parse(httpGet(URL)).take(max)

    /** Lee el RSS y lo ordena de la nota más nueva a la más vieja. */
    fun parse(xml: String): List<NewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // El RSS viene de fuera: sin DTD ni entidades externas. (El parser de Android no soporta
            // esta opción, pero tampoco resuelve entidades externas; en la JVM de los tests sí aplica.)
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val items = doc.getElementsByTagName("item")
        val out = mutableListOf<NewsItem>()
        for (i in 0 until items.length) {
            val e = items.item(i) as Element
            fun text(tag: String) = e.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
            val rawTitle = text("title")
            val source = text("source").ifEmpty { rawTitle.substringAfterLast(" - ", "") }
            // Google agrega " - Medio" al final del título; se quita porque el medio va aparte.
            val title = if (source.isNotEmpty()) rawTitle.removeSuffix(" - $source") else rawTitle
            val published = runCatching {
                ZonedDateTime.parse(text("pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            }.getOrNull() ?: continue
            out += NewsItem(title, source, text("link"), published)
        }
        return out.sortedByDescending { it.published }
    }

    /** "hace 3 h", "hace 2 d", ... */
    fun hace(i: Instant, now: Instant): String {
        val min = maxOf(0L, Duration.between(i, now).toMinutes())
        return when {
            min < 60 -> "hace ${min} min"
            min < 24 * 60 -> "hace ${min / 60} h"
            else -> "hace ${min / (24 * 60)} d"
        }
    }
}
