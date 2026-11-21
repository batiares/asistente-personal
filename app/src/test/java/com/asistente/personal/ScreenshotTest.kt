package com.asistente.personal

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.asistente.personal.gmail.Email
import com.asistente.personal.t1.Match
import com.asistente.personal.t1.NewsItem
import com.asistente.personal.ui.AppActions
import com.asistente.personal.ui.AppState
import com.asistente.personal.ui.AsistenteApp
import com.asistente.personal.ui.AsistenteTheme
import com.asistente.personal.whatsapp.WaMessage
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Genera capturas de las pantallas con datos de ejemplo (en CI: ./gradlew recordPaparazziDebug). */
class ScreenshotTest {

    @get:Rule
    // Pantalla extra alta para que la captura de T1 muestre también noticias y resultados.
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 4200),
        theme = "android:Theme.Material.NoActionBar",
    )

    private object NoActions : AppActions {
        override fun refreshEmails() {}
        override fun refreshT1() {}
        override fun speakSummary() {}
        override fun openAppSettings() {}
        override fun openNotificationAccess() {}
        override fun openUrl(url: String) {}
        override fun addToCalendar(m: Match) {}
        override fun saveFilter(keywords: String, vipChats: String, hideUnimportant: Boolean, useAi: Boolean) {}
        override fun skipSection() {}
        override fun stopSpeaking() {}
        override fun clearMessages() {}
        override fun openGmail() {}
        override fun clearMail() {}
        override fun saveVoice(apiKey: String, voice: String) {}
        override fun testVoice(apiKey: String, voice: String) {}
    }

    @Test fun leyendoWhatsapp() = paparazzi.snapshot {
        val s = sample().apply { speaking = true; speakingSection = 1 }
        AsistenteTheme { AsistenteApp(s, NoActions, 1) }
    }

    @Test fun voz() = paparazzi.snapshot {
        val s = sample().apply { voiceKey = "AIzaEjemploNoReal"; voiceName = "Sulafat"; voiceStatus = "Generando voz con Gemini (Sulafat)…" }
        AsistenteTheme { AsistenteApp(s, NoActions, 0, initialVoiceSettings = true) }
    }

    private fun match(id: String, daysFromNow: Long, rival: String, code: String, state: String, t1: Int?, opp: Int?) =
        Match(id, Instant.now().plus(Duration.ofDays(daysFromNow)).plus(Duration.ofHours(5)), "LCK", "Semana 3",
            rival, code, state, 3, t1, opp)

    private fun sample() = AppState().apply {
        emails = listOf(
            Email("1", "Mi Banco <avisos@mibanco.mx>", "Tu pago vence mañana", "Recuerda realizar tu pago antes del 21 de noviembre para evitar cargos."),
            Email("2", "Coordinación Académica <coord@uni.mx>", "Cambio de horario: examen final", "El examen de Cálculo se mueve al lunes a las 9:00 en el aula 204."),
            Email("3", "GitHub <noreply@github.com>", "[asistente-personal] Run succeeded", "Compilar y publicar APK: todas las tareas terminaron con éxito."),
        )
        val now = System.currentTimeMillis()
        emails = emptyList()
        mail = listOf(
            WaMessage(now - 300_000, "Mi Banco", "Tu pago vence mañana\nRecuerda realizar tu pago antes del 21 de noviembre.", true),
            WaMessage(now - 5_400_000, "Coordinación Académica", "Cambio de horario: examen final\nEl examen de Cálculo se mueve al lunes a las 9:00.", true),
        )
        messages = listOf(
            WaMessage(now - 600_000, "Equipo Proyecto Final", "Ana: la entrega se adelantó a mañana a las 8", true, "IA: Entrega adelantada, plazo mañana"),
            WaMessage(now - 3_600_000, "Familia", "Mamá: ¿vienes a comer el domingo?", true, "Chat VIP"),
            WaMessage(now - 7_200_000, "Memes 2.0", "Luis: jajaja hoy no puedo, mira este video", false, "IA: Plática casual"),
        )
        listenerEnabled = true
        news = listOf(
            NewsItem("T1 confirma su plantilla para la próxima temporada", "Medio Ejemplo",
                "https://example.com/1", Instant.now().minus(Duration.ofHours(3))),
            NewsItem("Faker habla del cierre de temporada y los planes del equipo", "Otro Medio",
                "https://example.com/2", Instant.now().minus(Duration.ofDays(2))),
        )
        matches = listOf(
            match("a", -12, "Gen.G", "GEN", "completed", 3, 1).copy(mvp = "Faker"),
            match("b", -5, "Hanwha Life Esports", "HLE", "completed", 1, 2).copy(mvp = "Zeka"),
            match("c", 2, "Gen.G", "GEN", "unstarted", null, null),
            match("d", 6, "Dplus KIA", "DK", "unstarted", null, null),
        )
    }

    @Test fun correos() = paparazzi.snapshot { AsistenteTheme { AsistenteApp(sample(), NoActions, 0) } }
    @Test fun mensajes() = paparazzi.snapshot { AsistenteTheme { AsistenteApp(sample(), NoActions, 1) } }
    @Test fun t1() = paparazzi.snapshot { AsistenteTheme { AsistenteApp(sample(), NoActions, 2) } }

    @Test fun primerUso() = paparazzi.snapshot {
        val s = sample().apply { emails = emptyList(); gmailNeedsSetup = true; listenerEnabled = false }
        AsistenteTheme { AsistenteApp(s, NoActions, 1) }
    }

    @Test fun gmailSinEnlazar() = paparazzi.snapshot {
        val s = sample().apply { emails = emptyList(); gmailNeedsSetup = true }
        AsistenteTheme { AsistenteApp(s, NoActions, 0) }
    }
}
