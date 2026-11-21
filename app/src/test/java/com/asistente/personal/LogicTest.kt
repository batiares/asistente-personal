package com.asistente.personal

import com.asistente.personal.gmail.GmailRepository
import com.asistente.personal.ai.GeminiFilter
import com.asistente.personal.voice.GeminiVoice
import com.asistente.personal.t1.T1Mvp
import com.asistente.personal.t1.T1News
import com.asistente.personal.t1.T1Schedule
import com.asistente.personal.whatsapp.ImportanceFilter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LogicTest {

    private val schedule = """
        {"data":{"schedule":{"pages":{"older":"abc","newer":null},"events":[
          {"startTime":"2026-11-08T22:00:00Z","state":"completed","type":"match","blockName":"Final",
           "league":{"name":"Worlds"},
           "match":{"id":"1","teams":[
              {"name":"Gen.G","code":"GEN","result":{"outcome":"loss","gameWins":1}},
              {"name":"T1","code":"T1","result":{"outcome":"win","gameWins":3}}],
            "strategy":{"type":"bestOf","count":5}}},
          {"startTime":"2026-11-09T10:00:00Z","state":"unstarted","type":"match","blockName":"Semana 1",
           "league":{"name":"LCK"},
           "match":{"id":"2","teams":[{"name":"Gen.G","code":"GEN"},{"name":"Hanwha","code":"HLE"}],
            "strategy":{"type":"bestOf","count":3}}},
          {"startTime":"2026-11-10T08:00:00Z","state":"unstarted","type":"show","blockName":"Show","league":{"name":"LCK"}}
        ]}}}
    """.trimIndent()

    @Test fun soloPartidosDeT1() {
        val page = T1Schedule.parse(schedule)
        assertEquals(1, page.matches.size)
        val m = page.matches[0]
        assertEquals("Gen.G", m.opponent)
        assertEquals(3, m.t1Wins)
        assertEquals(1, m.opponentWins)
        assertEquals(5, m.bestOf)
        assertEquals("abc", page.older)
        assertNull(page.newer)
        assertNull(T1Schedule.next(page.matches)) // ya terminó
    }

    @Test fun horaDeMexicoYCorea() {
        val t = Instant.parse("2026-11-08T22:00:00Z")
        // México centro es UTC-6 todo el año (sin horario de verano desde 2022).
        assertEquals(16, T1Schedule.enMexico(t).hour)
        assertEquals(8, T1Schedule.enMexico(t).dayOfMonth)
        // Corea es UTC+9: ya es el día siguiente a las 7 am.
        assertEquals(7, T1Schedule.enCorea(t).hour)
        assertEquals(9, T1Schedule.enCorea(t).dayOfMonth)
    }

    @Test fun mvpDeLaSerie() {
        val json = """{"cargoquery":[
            {"title":{"MVP":"Bin (Chen Ze-Bin)","DateTime UTC":"2026-11-08 22:00:00"}},
            {"title":{"MVP":"N/A","DateTime UTC":"2026-10-28 20:00:00"}},
            {"title":{"MVP":"Faker","DateTime UTC":"2026-11-06 22:00:00"}}]}"""
        val mvps = T1Mvp.parse(json)
        assertEquals(2, mvps.size)
        val withMvp = T1Mvp.attach(T1Schedule.parse(schedule).matches, mvps)
        assertEquals("Bin", withMvp.single().mvp) // mismo inicio 2026-11-08 22:00 UTC
        val far = withMvp.single().copy(start = Instant.parse("2026-01-01T00:00:00Z"), mvp = null)
        assertNull(T1Mvp.attach(listOf(far), mvps).single().mvp)
    }

    @Test fun noticias() {
        val rss = """<?xml version="1.0" encoding="UTF-8"?><rss><channel>
            <item><title>T1 anuncia su nueva plantilla - Medio A</title><link>https://a.example/1</link>
              <pubDate>Thu, 12 Nov 2026 08:00:00 GMT</pubDate><source url="https://a.example">Medio A</source></item>
            <item><title>Faker habla tras Worlds - Medio B</title><link>https://b.example/2</link>
              <pubDate>Fri, 20 Nov 2026 10:00:00 GMT</pubDate><source url="https://b.example">Medio B</source></item>
            <item><title>Sin fecha</title><link>x</link></item>
        </channel></rss>"""
        val news = T1News.parse(rss)
        assertEquals(2, news.size)
        assertEquals("Faker habla tras Worlds", news[0].title) // la más nueva primero, sin " - Medio"
        assertEquals("Medio B", news[0].source)
        assertEquals("hace 2 h", T1News.hace(news[0].published, Instant.parse("2026-11-20T12:30:00Z")))
    }

    @Test fun vozGemini() {
        val body = JSONObject(GeminiVoice.requestBody("Hola", "Kore"))
        val gen = body.getJSONObject("generationConfig")
        assertEquals("AUDIO", gen.getJSONArray("responseModalities").getString(0))
        assertEquals("Kore", gen.getJSONObject("speechConfig").getJSONObject("voiceConfig").getString("voice"))
        assertEquals("Hola", body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))

        val pcm = byteArrayOf(1, 2, 3, 4)
        val b64 = java.util.Base64.getEncoder().encodeToString(pcm)
        val resp = """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/l16","data":"$b64"}}]}}]}"""
        val audio = GeminiVoice.extractAudio(resp)
        assertTrue(audio.contentEquals(pcm))

        val wav = GeminiVoice.ensureWav(audio)
        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertTrue(GeminiVoice.ensureWav(wav).contentEquals(wav)) // un WAV no se toca
    }

    @Test fun vozRobusta() {
        // Formato anterior para modelos viejos
        val legacy = JSONObject(GeminiVoice.requestBody("Hola", "Kore", legacy = true))
            .getJSONObject("generationConfig").getJSONObject("speechConfig").getJSONObject("voiceConfig")
        assertEquals("Kore", legacy.getJSONObject("prebuiltVoiceConfig").getString("voiceName"))

        // Audio en la segunda parte (la primera trae texto)
        val b64 = java.util.Base64.getEncoder().encodeToString(byteArrayOf(9, 9))
        val resp = """{"candidates":[{"content":{"parts":[{"text":"ok"},{"inlineData":{"data":"$b64"}}]}}]}"""
        assertTrue(GeminiVoice.extractAudio(resp).contentEquals(byteArrayOf(9, 9)))

        // WAV con tamaños inválidos (estilo streaming): se rehace la cabecera conservando el audio y la frecuencia
        val good = GeminiVoice.ensureWav(byteArrayOf(1, 2, 3, 4), defaultRate = 16_000)
        val broken = good.copyOf().also { for (i in 40..43) it[i] = 0xFF.toByte() }
        val fixed = GeminiVoice.ensureWav(broken)
        assertTrue(fixed.contentEquals(good))

        assertEquals("La clave de Gemini no es válida. Revísala en ⚙.",
            GeminiVoice.friendlyError(java.io.IOException("HTTP 400 API key not valid. Please pass a valid API key.")))
    }

    @Test fun filtroConGemini() {
        val resp = """{"candidates":[{"content":{"parts":[{"text":"```json\n{\"important\": true, \"reason\": \"Entrega adelantada\"}\n```"}]}}]}"""
        val v = GeminiFilter.parseVerdict(resp)
        assertTrue(v.important)
        assertEquals("Entrega adelantada", v.reason)

        val p = GeminiFilter.prompt("Equipo", "jaja hoy no puedo", listOf("Ana: ¿vienes?"), listOf("examen"))
        assertTrue(p.contains("MENSAJE NUEVO: jaja hoy no puedo"))
        assertTrue(p.contains("examen"))

        // Tope de consultas por minuto
        val t0 = 1_000_000L
        repeat(3) { assertTrue(GeminiFilter.allowCall(t0 + it, perMinute = 3)) }
        assertFalse(GeminiFilter.allowCall(t0 + 10, perMinute = 3))
        assertTrue(GeminiFilter.allowCall(t0 + 61_000, perMinute = 3))
    }

    @Test fun cuentaRegresiva() {
        val now = Instant.parse("2026-11-20T00:00:00Z")
        assertEquals("2D 04H 05M", T1Schedule.faltan(Instant.parse("2026-11-22T04:05:00Z"), now))
        assertEquals("00H 30M", T1Schedule.faltan(Instant.parse("2026-11-20T00:30:00Z"), now))
        assertEquals("00H 00M", T1Schedule.faltan(Instant.parse("2026-11-19T00:00:00Z"), now))
    }

    @Test fun filtroDeImportancia() {
        val keys = ImportanceFilter.parseList("urgente, reunión , @Robert")
        assertEquals(listOf("urgente", "reunión", "@Robert"), keys)
        val vip = listOf("Familia")
        assertTrue(ImportanceFilter.isImportant("Trabajo", "Mañana hay REUNION a las 9", keys, vip))
        assertTrue(ImportanceFilter.isImportant("Grupo X", "oye @robert mira esto", keys, vip))
        assertTrue(ImportanceFilter.isImportant("Familia Pérez", "jaja", keys, vip))
        assertFalse(ImportanceFilter.isImportant("Memes", "jajaja mira este video", keys, vip))
        assertTrue(ImportanceFilter.isSystemNotice("5 mensajes nuevos"))
        assertFalse(ImportanceFilter.isSystemNotice("tengo 5 mensajes nuevos que contarte"))
    }

    @Test fun parseoDeCorreo() {
        val json = JSONObject(
            """{"id":"m1","snippet":"Hola","payload":{"headers":[
               {"name":"From","value":"\"Juan Pérez\" <juan@x.com>"},{"name":"Subject","value":"Pago pendiente"}]}}"""
        )
        val e = GmailRepository.parseMessage(json)
        assertEquals("Juan Pérez", e.fromName)
        assertEquals("Pago pendiente", e.subject)
        assertEquals("(sin asunto)", GmailRepository.parseMessage(JSONObject("""{"id":"m2"}""")).subject)
    }
}
