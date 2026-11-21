package com.asistente.personal

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.asistente.personal.gmail.GmailRepository
import com.asistente.personal.t1.Match
import com.asistente.personal.t1.T1Mvp
import com.asistente.personal.t1.T1News
import com.asistente.personal.t1.T1Schedule
import com.asistente.personal.ui.AppActions
import com.asistente.personal.ui.AppState
import com.asistente.personal.ui.AsistenteApp
import com.asistente.personal.ui.AsistenteTheme
import com.asistente.personal.whatsapp.FilterConfig
import com.asistente.personal.whatsapp.MailStore
import com.asistente.personal.whatsapp.MessageStore
import com.asistente.personal.whatsapp.WhatsAppListenerService
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity(), AppActions {

    private val state = AppState()
    private var tts: TextToSpeech? = null

    // Campo (no lambda suelta) para que SharedPreferences no lo pierda: solo guarda referencias débiles.
    private val messagesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        state.messages = MessageStore.load(this)
        state.mail = MailStore.load(this)
    }

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            val token = runCatching {
                Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(res.data).accessToken
            }.getOrNull()
            if (token != null) loadEmails(token) else state.emailStatus = "Permiso de Gmail cancelado."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val dark = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(dark, dark)
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("es", "MX") }

        state.messages = MessageStore.load(this)
        state.keywords = FilterConfig.keywords(this)
        state.vipChats = FilterConfig.vipChats(this)
        state.hideUnimportant = FilterConfig.hideUnimportant(this)
        state.mail = MailStore.load(this)
        MessageStore.prefs(this).registerOnSharedPreferenceChangeListener(messagesListener)
        MailStore.prefs(this).registerOnSharedPreferenceChangeListener(messagesListener)

        refreshT1()
        setContent { AsistenteTheme { AsistenteApp(state, this) } }
    }

    override fun onResume() {
        super.onResume()
        state.listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    override fun onDestroy() {
        MessageStore.prefs(this).unregisterOnSharedPreferenceChangeListener(messagesListener)
        MailStore.prefs(this).unregisterOnSharedPreferenceChangeListener(messagesListener)
        tts?.shutdown()
        super.onDestroy()
    }

    // ---------- Gmail ----------

    override fun refreshEmails() {
        state.emailStatus = "Conectando con Gmail…"
        Identity.getAuthorizationClient(this).authorize(GmailRepository.authRequest())
            .addOnSuccessListener { r ->
                val pending = r.pendingIntent
                val token = r.accessToken
                when {
                    r.hasResolution() && pending != null ->
                        authLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    token != null -> loadEmails(token)
                    else -> state.emailStatus = "Gmail no devolvió acceso. Intenta de nuevo."
                }
            }
            .addOnFailureListener {
                val code = (it as? ApiException)?.statusCode
                // DEVELOPER_ERROR (10) = la app no está registrada en Google Cloud con esta firma.
                state.gmailNeedsSetup = code == CommonStatusCodes.DEVELOPER_ERROR
                state.emailStatus = "Error ${code ?: ""}: ${it.message}"
            }
    }

    private fun loadEmails(token: String) {
        state.gmailNeedsSetup = false
        state.emailStatus = "Buscando correos importantes…"
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { GmailRepository.importantUnread(token) } }
                .onSuccess {
                    state.emails = it
                    state.emailStatus = if (it.isEmpty()) "Bandeja limpia: no hay correos importantes sin leer." else ""
                }
                .onFailure { state.emailStatus = "Error al leer Gmail: ${it.message}" }
        }
    }

    // ---------- T1 ----------

    override fun refreshT1() {
        state.t1Status = "Sincronizando calendario…"
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val matches = T1Schedule.fetch()
                    // Si Leaguepedia falla, el calendario se muestra igual, solo sin MVP.
                    T1Mvp.attach(matches, runCatching { T1Mvp.fetch() }.getOrDefault(emptyMap()))
                }
            }
                .onSuccess {
                    state.matches = it
                    state.t1Status = if (it.isEmpty()) "No se encontraron partidos recientes de T1." else ""
                }
                .onFailure { state.t1Status = "Sin conexión con LoL Esports: ${it.message}" }
        }
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { T1News.fetch() } }.onSuccess { state.news = it }
        }
    }

    override fun addToCalendar(m: Match) {
        val start = m.start.toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + m.bestOf * 60 * 60 * 1000L)
            .putExtra(CalendarContract.Events.TITLE, "T1 vs ${m.opponent} (${m.league})")
            .putExtra(CalendarContract.Events.DESCRIPTION, "Ver en https://lolesports.com")
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "No hay app de calendario instalada", Toast.LENGTH_SHORT).show() }
    }

    // ---------- WhatsApp ----------

    override fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun openNotificationAccess() {
        val direct = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, WhatsAppListenerService::class.java).flattenToString(),
            ) else null
        runCatching { startActivity(direct ?: error("old")) }
            .onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    override fun saveFilter(keywords: String, vipChats: String, hideUnimportant: Boolean) {
        FilterConfig.save(this, keywords, vipChats, hideUnimportant)
        state.keywords = keywords
        state.vipChats = vipChats
        state.hideUnimportant = hideUnimportant
    }

    override fun clearMessages() = MessageStore.clear(this)

    override fun clearMail() = MailStore.clear(this)

    override fun openGmail() {
        packageManager.getLaunchIntentForPackage(WhatsAppListenerService.GMAIL)?.let { startActivity(it) }
            ?: openUrl("https://mail.google.com")
    }

    override fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /** El "modo asistente": lee en voz alta lo importante del momento. */
    override fun speakSummary() {
        val since = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val msgs = state.messages.filter { it.important && it.time > since }.take(3)
        val next = T1Schedule.next(state.matches)
        val text = buildString {
            val recentMail = state.mail.filter { it.time > since }.take(3)
            if (state.emails.isEmpty() && recentMail.isNotEmpty()) {
                append("Tienes ${recentMail.size} correos importantes recientes. ")
                recentMail.forEach { append("De ${it.chat}: ${it.text.lineSequence().first()}. ") }
            } else if (state.emails.isEmpty()) append("No tienes correos importantes nuevos. ")
            else {
                append("Tienes ${state.emails.size} correos importantes. ")
                state.emails.take(3).forEach { append("De ${it.fromName}: ${it.subject}. ") }
            }
            if (msgs.isEmpty()) append("No hay mensajes importantes de WhatsApp hoy. ")
            else {
                append("Mensajes importantes: ")
                msgs.forEach { append("En ${it.chat}: ${it.text}. ") }
            }
            if (next != null) append(
                "El próximo partido de T1 es contra ${next.opponent}, el ${T1Schedule.fechaMx(next.start)} " +
                    "a las ${T1Schedule.horaMx(next.start)}, hora de México."
            ) else append("T1 no tiene partidos programados por ahora. ")
            state.news.firstOrNull()?.let { append("Última noticia de T1: ${it.title}.") }
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "resumen")
    }
}
