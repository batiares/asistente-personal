package com.asistente.personal

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import com.asistente.personal.voice.GeminiVoice
import com.asistente.personal.voice.VoiceConfig
import com.asistente.personal.whatsapp.FilterConfig
import com.asistente.personal.whatsapp.MailStore
import com.asistente.personal.whatsapp.MessageStore
import com.asistente.personal.whatsapp.WhatsAppListenerService
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) { pickBestLocalVoice(); tts?.setOnUtteranceProgressListener(utteranceListener) } }
        state.voiceKey = VoiceConfig.apiKey(this)
        state.voiceName = VoiceConfig.voice(this)

        state.messages = MessageStore.load(this)
        state.keywords = FilterConfig.keywords(this)
        state.vipChats = FilterConfig.vipChats(this)
        state.hideUnimportant = FilterConfig.hideUnimportant(this)
        state.useAi = FilterConfig.useAi(this)
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
        stopSpeaking()
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

    override fun saveFilter(keywords: String, vipChats: String, hideUnimportant: Boolean, useAi: Boolean) {
        FilterConfig.save(this, keywords, vipChats, hideUnimportant, useAi)
        state.keywords = keywords
        state.vipChats = vipChats
        state.hideUnimportant = hideUnimportant
        state.useAi = useAi
    }

    override fun clearMessages() = MessageStore.clear(this)

    override fun clearMail() = MailStore.clear(this)

    override fun openGmail() {
        val gmail = WhatsAppListenerService.GMAIL
        packageManager.getLaunchIntentForPackage(gmail)?.let { return startActivity(it) }
        // Sin la app de Gmail no hay notificaciones que capturar: se manda a instalarla.
        Toast.makeText(this, "Instala la app de Gmail para recibir tus correos importantes", Toast.LENGTH_LONG).show()
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$gmail"))) }
            .onFailure { openUrl("https://play.google.com/store/apps/details?id=$gmail") }
    }

    override fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /** El "modo asistente": lee lo importante por secciones (correos, WhatsApp, T1) y cambia de pestaña. */
    override fun speakSummary() {
        val since = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val correos = buildString {
            append("Correos. ")
            val recentMail = state.mail.filter { it.time > since }.take(3)
            when {
                state.emails.isNotEmpty() -> {
                    append("Tienes ${state.emails.size} correos importantes. ")
                    state.emails.take(3).forEach { append("De ${it.fromName}: ${it.subject}. ") }
                }
                recentMail.isNotEmpty() -> {
                    append("Tienes ${recentMail.size} correos importantes recientes. ")
                    recentMail.forEach { append("De ${it.chat}: ${it.text.lineSequence().first()}. ") }
                }
                else -> append("No tienes correos importantes nuevos.")
            }
        }
        val msgs = state.messages.filter { it.important && it.time > since }.take(4)
        val whatsapp = buildString {
            append("WhatsApp. ")
            if (msgs.isEmpty()) append("No hay mensajes importantes hoy.")
            else {
                append("Tienes ${msgs.size} mensajes importantes. ")
                msgs.forEach { append("En ${it.chat}: ${it.text}. ") }
            }
        }
        val next = T1Schedule.next(state.matches)
        val last = state.matches.lastOrNull { it.finished }
        val t1 = buildString {
            append("T1. ")
            if (next != null) append(
                "El próximo partido es contra ${next.opponent}, el ${T1Schedule.fechaMx(next.start)} " +
                    "a las ${T1Schedule.horaMx(next.start)}, hora de México. "
            ) else append("No hay partidos programados por ahora. ")
            if (last != null) {
                val won = (last.t1Wins ?: 0) > (last.opponentWins ?: 0)
                append("En su último partido ${if (won) "le ganó" else "perdió"} ${last.t1Wins} a ${last.opponentWins} contra ${last.opponent}. ")
                last.mvp?.let { append("El MVP fue $it. ") }
            }
            state.news.firstOrNull()?.let { append("Última noticia: ${it.title}.") }
        }
        narrate(listOf(0 to correos, 1 to whatsapp, 2 to t1), state.voiceKey, state.voiceName)
    }

    // ---------- Voz ----------

    private var player: MediaPlayer? = null
    private var narration: Job? = null
    private var sectionDone: CompletableDeferred<Unit>? = null
    @Volatile private var localUtterance: String? = null

    override fun saveVoice(apiKey: String, voice: String) {
        VoiceConfig.save(this, apiKey, voice)
        state.voiceKey = apiKey.trim()
        state.voiceName = voice
        state.voiceStatus = if (apiKey.isBlank()) "Sin clave: se usará la voz del teléfono." else "Voz guardada: $voice (Gemini)."
    }

    override fun testVoice(apiKey: String, voice: String) = narrate(
        listOf(-1 to "Hola, soy tu asistente. Así va a sonar tu resumen de correos, mensajes y partidos de T1."),
        apiKey.trim(), voice,
    )

    override fun skipSection() {
        player?.runCatching { stop() }
        tts?.stop()
        sectionDone?.complete(Unit)
    }

    override fun stopSpeaking() {
        narration?.cancel()
        narration = null
        skipSection()
        state.speaking = false
        state.speakingSection = -1
    }

    /**
     * Lee cada sección por turnos (cambiando a su pestaña). Con clave, Gemini genera las secciones en paralelo
     * para que no haya silencio entre una y otra; si una falla, esa se lee con la voz del teléfono.
     */
    private fun narrate(sections: List<Pair<Int, String>>, apiKey: String, voice: String) {
        stopSpeaking()
        state.speaking = true
        narration = lifecycleScope.launch {
            try {
                val audio = if (apiKey.isBlank()) null else {
                    state.voiceStatus = "Generando voz con Gemini ($voice)…"
                    sections.map { (_, text) ->
                        async(Dispatchers.IO) { runCatching { GeminiVoice.synthesize(text, apiKey, voice) } }
                    }
                }
                sections.forEachIndexed { i, (tab, text) ->
                    state.speakingSection = tab
                    val result = audio?.get(i)?.await()
                    val wav = result?.getOrNull()
                    val played = wav != null && playWav(wav, i)
                    if (played) state.voiceStatus = ""
                    else {
                        result?.exceptionOrNull()?.let { state.voiceStatus = "Gemini: ${it.message} · Usando la voz del teléfono." }
                        speakLocalAwait(text)
                    }
                    delay(400)
                }
            } finally {
                // Solo si sigue siendo la narración actual (una nueva pudo haber empezado ya).
                if (narration === coroutineContext[Job]) {
                    state.speaking = false
                    state.speakingSection = -1
                }
            }
        }
    }

    /** Reproduce y espera a que termine (o a que se salte la sección). false si no se pudo reproducir. */
    private suspend fun playWav(wav: ByteArray, index: Int): Boolean {
        val done = CompletableDeferred<Unit>().also { sectionDone = it }
        val mp = runCatching {
            val file = File(cacheDir, "voz$index.wav").apply { writeBytes(wav) }
            MediaPlayer().apply {
                setDataSource(file.path)
                setOnCompletionListener { done.complete(Unit) }
                setOnErrorListener { _, _, _ -> done.complete(Unit); true }
                prepare()
                start()
            }
        }.getOrElse {
            state.voiceStatus = "No se pudo reproducir el audio de Gemini: ${it.message}"
            return false
        }
        player = mp
        try {
            done.await()
        } finally {
            mp.release()
            if (player === mp) player = null
        }
        return true
    }

    private suspend fun speakLocalAwait(text: String) {
        val t = tts ?: return
        val done = CompletableDeferred<Unit>().also { sectionDone = it }
        val id = "sec${System.nanoTime()}"
        localUtterance = id
        if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) return
        // Tope por si el motor de voz nunca avisa que terminó.
        withTimeoutOrNull(3 * 60_000L) { done.await() }
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) {}
        override fun onDone(id: String?) { if (id == localUtterance) sectionDone?.complete(Unit) }
        @Deprecated("API antigua") override fun onError(id: String?) { onDone(id) }
        override fun onStop(id: String?, interrupted: Boolean) { onDone(id) }
    }

    /** Prefiere voz es-MX, luego la de mayor calidad (las neuronales suelen marcar QUALITY_VERY_HIGH). */
    private fun pickBestLocalVoice() {
        val t = tts ?: return
        t.language = Locale("es", "MX")
        val best = runCatching { t.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == "es" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy({ it.locale.country != "MX" }, { -it.quality }, { it.isNetworkConnectionRequired }))
            .firstOrNull()
        if (best != null) t.voice = best
    }
}
