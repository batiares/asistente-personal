package com.asistente.personal.whatsapp

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.asistente.personal.ai.GeminiFilter
import com.asistente.personal.voice.VoiceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lee las notificaciones de WhatsApp y Gmail (el usuario debe dar "Acceso a notificaciones").
 * WhatsApp: clasifica cada mensaje y, si se activa la opción, quita de la barra los de grupos que no son importantes.
 * Gmail: guarda remitente y asunto (el nombre de la clase se conserva para no perder el permiso ya otorgado).
 */
class WhatsAppListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == GMAIL) return onGmail(sbn)
        if (sbn.packageName !in WHATSAPP) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        if (ImportanceFilter.isSystemNotice(text)) return

        val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) || conversation != null
        val chat = conversation ?: title

        val keywords = ImportanceFilter.parseList(FilterConfig.keywords(this))
        val vip = ImportanceFilter.isImportant("$chat $title", "", emptyList(), ImportanceFilter.parseList(FilterConfig.vipChats(this)))
        val keywordHit = ImportanceFilter.isImportant("", text, keywords, emptyList())
        // En grupos el remitente suele venir en el título ("Grupo: Juan").
        val shown = if (isGroup && title != chat) "${title.substringAfter(": ")}: $text" else text

        // Primero se guarda con el filtro de palabras (instantáneo); luego Gemini lo corrige si está activo.
        val msg = WaMessage(sbn.postTime, chat, shown, vip || keywordHit,
            when { vip -> "Chat VIP"; keywordHit -> "Palabra clave"; else -> "" })
        if (!MessageStore.add(this, msg)) return

        val apiKey = VoiceConfig.apiKey(this)
        val hide = isGroup && FilterConfig.hideUnimportant(this)
        val askAi = !vip && apiKey.isNotBlank() && FilterConfig.useAi(this) && GeminiFilter.allowCall()
        if (!askAi) {
            if (hide && !msg.important) cancelNotification(sbn.key)
            return
        }
        scope.launch {
            val context = MessageStore.load(this@WhatsAppListenerService)
                .filter { it.chat == chat && it != msg }.take(5).reversed().map { it.text }
            val verdict = runCatching { GeminiFilter.classify(apiKey, chat, shown, context, keywords) }.getOrNull()
            if (verdict != null) MessageStore.update(this@WhatsAppListenerService, msg) {
                it.copy(important = verdict.important, reason = "IA: ${verdict.reason}")
            }
            if (hide && !(verdict?.important ?: msg.important)) cancelNotification(sbn.key)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Gmail pone el remitente en el título y "Asunto\nfragmento" en el texto grande. */
    private fun onGmail(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        if (ImportanceFilter.isSystemNotice(sender) || ImportanceFilter.isSystemNotice(body)) return
        MailStore.add(this, WaMessage(sbn.postTime, sender, body.trim(), important = true))
    }

    companion object {
        val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
        const val GMAIL = "com.google.android.gm"
    }
}
