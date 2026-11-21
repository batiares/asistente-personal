package com.asistente.personal

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.asistente.personal.gmail.Email
import com.asistente.personal.gmail.GmailRepository
import com.asistente.personal.t1.Match
import com.asistente.personal.t1.T1Schedule
import com.asistente.personal.whatsapp.MessageStore
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {

    var emails by mutableStateOf<List<Email>>(emptyList())
        private set
    var emailStatus by mutableStateOf("Toca \"Conectar Gmail\" para ver tus correos importantes.")
        private set
    var matches by mutableStateOf<List<Match>>(emptyList())
        private set
    var t1Status by mutableStateOf("")
        private set
    var listenerEnabled by mutableStateOf(false)
        private set

    private var tts: TextToSpeech? = null

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            val token = runCatching {
                Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(res.data).accessToken
            }.getOrNull()
            if (token != null) loadEmails(token) else emailStatus = "No se dio permiso para leer Gmail."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("es", "MX") }
        refreshT1()
        setContent { MaterialTheme { AsistenteApp(this) } }
    }

    override fun onResume() {
        super.onResume()
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    fun refreshEmails() {
        emailStatus = "Conectando con Gmail…"
        Identity.getAuthorizationClient(this).authorize(GmailRepository.authRequest())
            .addOnSuccessListener { r ->
                val pending = r.pendingIntent
                val token = r.accessToken
                when {
                    r.hasResolution() && pending != null ->
                        authLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    token != null -> loadEmails(token)
                    else -> emailStatus = "Gmail no devolvió acceso. Intenta de nuevo."
                }
            }
            .addOnFailureListener {
                emailStatus = "No se pudo conectar con Gmail: ${it.message}\n" +
                    "Revisa la configuración de Google Cloud descrita en el README."
            }
    }

    private fun loadEmails(token: String) {
        emailStatus = "Buscando correos importantes…"
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { GmailRepository.importantUnread(token) } }
                .onSuccess {
                    emails = it
                    emailStatus = if (it.isEmpty()) "No tienes correos importantes sin leer. 🎉" else ""
                }
                .onFailure { emailStatus = "Error al leer Gmail: ${it.message}" }
        }
    }

    fun refreshT1() {
        t1Status = "Cargando partidos de T1…"
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { T1Schedule.fetch() } }
                .onSuccess {
                    matches = it
                    t1Status = if (it.isEmpty()) "No se encontraron partidos recientes de T1." else ""
                }
                .onFailure { t1Status = "No se pudo cargar el calendario: ${it.message}" }
        }
    }

    /** El "modo asistente": lee en voz alta lo importante del momento. */
    fun speakSummary() {
        val since = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val msgs = MessageStore.load(this).filter { it.important && it.time > since }.take(3)
        val next = T1Schedule.next(matches)
        val text = buildString {
            if (emails.isEmpty()) append("No tienes correos importantes cargados. ")
            else {
                append("Tienes ${emails.size} correos importantes. ")
                emails.take(3).forEach { append("De ${it.fromName}: ${it.subject}. ") }
            }
            if (msgs.isEmpty()) append("No hay mensajes importantes de WhatsApp hoy. ")
            else {
                append("Mensajes importantes: ")
                msgs.forEach { append("En ${it.chat}: ${it.text}. ") }
            }
            if (next != null) append(
                "El próximo partido de T1 es contra ${next.opponent}, el ${T1Schedule.fechaMx(next.start)} " +
                    "a las ${T1Schedule.horaMx(next.start)}, hora de México."
            ) else append("T1 no tiene partidos programados por ahora.")
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "resumen")
    }
}
