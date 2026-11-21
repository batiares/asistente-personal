package com.asistente.personal

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.asistente.personal.gmail.Email
import com.asistente.personal.t1.Match
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
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar")

    private object NoActions : AppActions {
        override fun refreshEmails() {}
        override fun refreshT1() {}
        override fun speakSummary() {}
        override fun openAppSettings() {}
        override fun openNotificationAccess() {}
        override fun openUrl(url: String) {}
        override fun addToCalendar(m: Match) {}
        override fun saveFilter(keywords: String, vipChats: String, hideUnimportant: Boolean) {}
        override fun clearMessages() {}
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
        messages = listOf(
            WaMessage(now - 600_000, "Equipo Proyecto Final", "Ana: URGENTE, la entrega se adelantó a mañana", true),
            WaMessage(now - 3_600_000, "Familia", "Mamá: ¿vienes a comer el domingo?", true),
            WaMessage(now - 7_200_000, "Memes 2.0", "Luis: jajaja mira este video", false),
        )
        listenerEnabled = true
        matches = listOf(
            match("a", -12, "Gen.G", "GEN", "completed", 3, 1),
            match("b", -5, "Hanwha Life Esports", "HLE", "completed", 1, 2),
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
