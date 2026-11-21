package com.asistente.personal.whatsapp

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

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

        val important = ImportanceFilter.isImportant(
            "$chat $title", text,
            ImportanceFilter.parseList(FilterConfig.keywords(this)),
            ImportanceFilter.parseList(FilterConfig.vipChats(this)),
        )
        // En grupos el remitente suele venir en el título ("Grupo: Juan").
        val shown = if (isGroup && title != chat) "${title.substringAfter(": ")}: $text" else text
        MessageStore.add(this, WaMessage(sbn.postTime, chat, shown, important))

        if (isGroup && !important && FilterConfig.hideUnimportant(this)) cancelNotification(sbn.key)
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
