package com.asistente.personal.t1

import com.asistente.personal.httpGet
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Match(
    val id: String,
    val start: Instant,
    val league: String,
    val block: String,
    val opponent: String,
    val opponentCode: String,
    val state: String,        // unstarted | inProgress | completed
    val bestOf: Int,
    val t1Wins: Int?,
    val opponentWins: Int?,
) {
    val finished get() = state == "completed"
}

object T1Schedule {
    private const val URL = "https://esports-api.lolesports.com/persisted/gw/getSchedule?hl=es-MX"
    // Clave pública que usa la propia web lolesports.com (API no oficial, puede cambiar).
    private const val API_KEY = "0TvQnueqKa5mxJntVWt0w4LpLfEkrV1Ta8rQBb9Z"
    const val TEAM = "T1"

    val MEXICO: ZoneId = ZoneId.of("America/Mexico_City")
    val COREA: ZoneId = ZoneId.of("Asia/Seoul")
    private val ES_MX = Locale("es", "MX")
    private val FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES_MX)
    private val HORA = DateTimeFormatter.ofPattern("h:mm a", ES_MX)

    class Page(val matches: List<Match>, val older: String?, val newer: String?)

    fun parse(json: String, team: String = TEAM): Page {
        val schedule = JSONObject(json).getJSONObject("data").getJSONObject("schedule")
        val events = schedule.getJSONArray("events")
        val out = mutableListOf<Match>()
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            val match = e.optJSONObject("match") ?: continue
            val teams = match.optJSONArray("teams") ?: continue
            if (teams.length() != 2) continue
            val a = teams.getJSONObject(0)
            val b = teams.getJSONObject(1)
            val (us, them) = when (team) {
                a.optString("code") -> a to b
                b.optString("code") -> b to a
                else -> continue
            }
            out += Match(
                id = match.optString("id"),
                start = Instant.parse(e.getString("startTime")),
                league = e.optJSONObject("league")?.optString("name").orEmpty(),
                block = e.optString("blockName"),
                opponent = them.optString("name"),
                opponentCode = them.optString("code"),
                state = e.optString("state"),
                bestOf = match.optJSONObject("strategy")?.optInt("count") ?: 1,
                t1Wins = us.optJSONObject("result")?.optInt("gameWins"),
                opponentWins = them.optJSONObject("result")?.optInt("gameWins"),
            )
        }
        val pages = schedule.optJSONObject("pages")
        fun token(name: String) = pages?.optString(name)?.takeIf { it.isNotEmpty() && it != "null" }
        return Page(out, token("older"), token("newer"))
    }

    private fun load(pageToken: String?): Page {
        val url = if (pageToken == null) URL else "$URL&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"
        return parse(httpGet(url, mapOf("x-api-key" to API_KEY)))
    }

    /** Llamada de red: ejecutar fuera del hilo principal. Partidos recientes + próximos, ordenados por fecha. */
    fun fetch(): List<Match> {
        val first = load(null)
        val all = first.matches.toMutableList()
        var newer = first.newer
        repeat(3) {
            val token = newer ?: return@repeat
            val p = load(token)
            all += p.matches
            newer = p.newer
        }
        // En pretemporada la primera página puede no traer a T1: se mira una página más atrás.
        if (all.isEmpty() && first.older != null) all += load(first.older).matches
        return all.distinctBy { it.id }.sortedBy { it.start }
    }

    fun next(matches: List<Match>): Match? = matches.firstOrNull { !it.finished }

    fun enMexico(i: Instant): ZonedDateTime = i.atZone(MEXICO)
    fun enCorea(i: Instant): ZonedDateTime = i.atZone(COREA)

    fun fechaMx(i: Instant): String = FECHA.format(enMexico(i)).replaceFirstChar { it.uppercase() }
    fun horaMx(i: Instant): String = HORA.format(enMexico(i))
    fun horaCorea(i: Instant): String = HORA.format(enCorea(i)) + " (" + FECHA.format(enCorea(i)) + ")"
    fun fechaCorta(i: Instant): String = CORTA.format(enMexico(i)).uppercase()
    private val CORTA = DateTimeFormatter.ofPattern("dd MMM", ES_MX)

    /** Cuenta regresiva tipo "2D 04H 13M" (o "04H 13M" si falta menos de un día). */
    fun faltan(start: Instant, now: Instant): String {
        val min = maxOf(0L, java.time.Duration.between(now, start).toMinutes())
        val d = min / (24 * 60)
        val h = (min / 60) % 24
        val m = min % 60
        return (if (d > 0) "${d}D " else "") + "%02dH %02dM".format(h, m)
    }
}
