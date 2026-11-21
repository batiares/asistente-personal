package com.asistente.personal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.asistente.personal.gmail.Email
import com.asistente.personal.t1.Match
import com.asistente.personal.t1.NewsItem
import com.asistente.personal.whatsapp.WaMessage

/** Todo lo que muestran las pantallas. La Activity lo llena; las capturas de prueba usan datos de ejemplo. */
class AppState {
    var emails by mutableStateOf<List<Email>>(emptyList())
    var emailStatus by mutableStateOf("")
    var gmailNeedsSetup by mutableStateOf(false)
    /** Correos capturados de las notificaciones de Gmail. */
    var mail by mutableStateOf<List<WaMessage>>(emptyList())

    var messages by mutableStateOf<List<WaMessage>>(emptyList())
    var listenerEnabled by mutableStateOf(false)
    var keywords by mutableStateOf("")
    var vipChats by mutableStateOf("")
    var hideUnimportant by mutableStateOf(false)

    var matches by mutableStateOf<List<Match>>(emptyList())
    var t1Status by mutableStateOf("")
    var news by mutableStateOf<List<NewsItem>>(emptyList())

    var voiceKey by mutableStateOf("")
    var voiceName by mutableStateOf("")
    var voiceStatus by mutableStateOf("")
    var speaking by mutableStateOf(false)
    /** Pestaña que se está leyendo en voz alta (-1 = ninguna). */
    var speakingSection by mutableStateOf(-1)
    var useAi by mutableStateOf(true)
}

interface AppActions {
    fun refreshEmails()
    fun refreshT1()
    fun speakSummary()
    fun openAppSettings()
    fun openNotificationAccess()
    fun openUrl(url: String)
    fun addToCalendar(m: Match)
    fun saveFilter(keywords: String, vipChats: String, hideUnimportant: Boolean, useAi: Boolean)
    fun clearMessages()
    fun openGmail()
    fun clearMail()
    fun saveVoice(apiKey: String, voice: String)
    fun testVoice(apiKey: String, voice: String)
    fun skipSection()
    fun stopSpeaking()
}

const val GMAIL_GUIDE_URL = "https://github.com/batiares/asistente-personal#configurar-gmail-una-sola-vez"
