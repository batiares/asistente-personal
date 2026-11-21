package com.asistente.personal

import com.asistente.personal.gmail.GmailRepository
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
