package com.asistente.personal.ui

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asistente.personal.t1.Match
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.asistente.personal.voice.GeminiVoice
import com.asistente.personal.t1.T1News
import com.asistente.personal.t1.T1Schedule
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter

private val LIST_PADDING = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
private val GAP = Arrangement.spacedBy(12.dp)
private val RELOJ = DateTimeFormatter.ofPattern("HH:mm").withZone(T1Schedule.MEXICO)
private val FECHA_MSG = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(T1Schedule.MEXICO)

@Composable
fun AsistenteApp(state: AppState, actions: AppActions, initialTab: Int = 0, initialVoiceSettings: Boolean = false) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var voiceSettings by rememberSaveable { mutableStateOf(initialVoiceSettings) }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = Instant.now() }
    }
    val dayAgo = now.toEpochMilli() - 24 * 60 * 60 * 1000L
    val badges = listOf(
        state.emails.size + state.mail.count { it.time > dayAgo },
        state.messages.count { it.important && it.time > dayAgo },
        if (T1Schedule.next(state.matches) != null) 1 else 0,
    )

    Column(Modifier.fillMaxSize().cyberBackdrop().statusBarsPadding()) {
        Header(now, state.voiceStatus, actions::speakSummary, onSettings = { voiceSettings = !voiceSettings })
        Box(Modifier.weight(1f).imePadding()) {
            when {
                voiceSettings -> VoiceScreen(state, actions, onClose = { voiceSettings = false })
                tab == 0 -> EmailsScreen(state, actions)
                tab == 1 -> MessagesScreen(state, actions)
                else -> T1Screen(state, actions, now)
            }
        }
        BottomNav(if (voiceSettings) -1 else tab, { tab = it; voiceSettings = false }, badges)
    }
}

@Composable
private fun Header(now: Instant, voiceStatus: String, onSpeak: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            GlitchText("ASISTENTE", MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Blink(Neon.Green)
                Spacer(Modifier.width(6.dp))
                Text("SYS.ONLINE // CDMX ${RELOJ.format(now)}", style = MaterialTheme.typography.labelMedium, color = Neon.Cyan)
            }
        }
        CyberButton("▶ Voz", onSpeak, color = Neon.Magenta)
        Spacer(Modifier.width(8.dp))
        CyberButton("⚙", onSettings, color = Neon.Cyan, filled = false,
            modifier = Modifier.semantics { contentDescription = "Ajustes de voz" })
    }
    if (voiceStatus.isNotEmpty()) Text(
        "> $voiceStatus", style = MaterialTheme.typography.labelMedium, color = Neon.Magenta,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
    Box(
        Modifier.fillMaxWidth().height(2.dp)
            .background(Brush.horizontalGradient(listOf(Neon.Yellow, Neon.Magenta, Neon.Cyan)))
    )
}

@Composable
private fun BottomNav(tab: Int, onSelect: (Int) -> Unit, badges: List<Int>) {
    val labels = listOf("Correos", "Mensajes", "T1")
    Column(Modifier.background(Neon.Bg).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Cyan.copy(alpha = 0.5f)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { i, label ->
                val sel = i == tab
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp)
                        .clip(ButtonShape)
                        .then(
                            if (sel) Modifier.background(Neon.Yellow)
                            else Modifier.background(Neon.Panel).border(1.dp, Neon.Muted.copy(alpha = 0.35f), ButtonShape)
                        )
                        .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(i) })
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%02d".format(i + 1), style = MaterialTheme.typography.labelSmall,
                            color = if (sel) Color.Black.copy(alpha = 0.6f) else Neon.Cyan)
                        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge,
                            color = if (sel) Color.Black else Neon.Text)
                    }
                    if (badges[i] > 0) Text(
                        "${badges[i]}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .background(Neon.Magenta, ButtonShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color = Neon.Cyan) {
    Text("> $text", style = MaterialTheme.typography.labelLarge, color = color)
}

// ================= VOZ =================

@Composable
fun VoiceScreen(state: AppState, actions: AppActions, onClose: () -> Unit) {
    var key by remember { mutableStateOf(state.voiceKey) }
    var voice by remember { mutableStateOf(state.voiceName.ifEmpty { GeminiVoice.DEFAULT_VOICE }) }
    var showKey by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Neon.Magenta, unfocusedBorderColor = Neon.Muted.copy(alpha = 0.6f),
        focusedLabelColor = Neon.Magenta, unfocusedLabelColor = Neon.Muted, cursorColor = Neon.Magenta,
        focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text,
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = LIST_PADDING, verticalArrangement = GAP) {
        item { SectionHeader("Voz del asistente", Neon.Magenta, trailing = if (key.isBlank()) "Teléfono" else "Gemini") }
        item {
            CyberPanel(accent = Neon.Magenta) {
                Text("CLAVE DE GEMINI", style = MaterialTheme.typography.labelLarge, color = Neon.Magenta)
                Text("Con tu clave de Google AI Studio el resumen se lee con la voz natural de Gemini. " +
                    "La clave se guarda solo en este teléfono.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    key, { key = it }, Modifier.fillMaxWidth(),
                    label = { Text("Clave de API (AIza…)") }, singleLine = true, shape = ButtonShape, colors = fieldColors,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Text(if (showKey) "OCULTAR" else "VER", style = MaterialTheme.typography.labelSmall,
                            color = Neon.Cyan, modifier = Modifier.clickable { showKey = !showKey }.padding(12.dp))
                    },
                )
                CyberButton("Obtener clave en AI Studio", { actions.openUrl("https://aistudio.google.com/apikey") },
                    Modifier.fillMaxWidth(), color = Neon.Cyan, filled = false)
            }
        }
        item {
            CyberPanel(accent = Neon.Yellow) {
                Text("ELIGE LA VOZ", style = MaterialTheme.typography.labelLarge, color = Neon.Yellow)
                GeminiVoice.VOICES.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (name, desc) ->
                            CyberButton("$name · $desc", { voice = name }, Modifier.weight(1f),
                                color = Neon.Yellow, filled = voice == name)
                        }
                    }
                }
                Text("Sin clave se usa la mejor voz en español instalada en tu teléfono.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton("▶ Probar", { actions.testVoice(key, voice) }, Modifier.weight(1f),
                    color = Neon.Magenta, filled = false)
                CyberButton("Guardar", { actions.saveVoice(key, voice); onClose() }, Modifier.weight(1f))
            }
        }
    }
}

// ================= CORREOS =================

@Composable
fun EmailsScreen(state: AppState, actions: AppActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = LIST_PADDING, verticalArrangement = GAP) {
        if (!state.listenerEnabled) item { AccessPanel(actions) }
        item {
            SectionHeader("Correos importantes", Neon.Yellow,
                trailing = if (state.mail.isNotEmpty()) "${state.mail.size} recibidos" else null)
        }
        if (state.mail.isEmpty()) item {
            CyberPanel(accent = Neon.Yellow) {
                Text("> ESPERANDO CORREOS", style = MaterialTheme.typography.labelLarge, color = Neon.Yellow)
                Text("Aquí aparecen los correos en cuanto Gmail te avise. Para que solo avise de los importantes:",
                    style = MaterialTheme.typography.bodyMedium)
                Steps(
                    "Abre Gmail → menú ☰ → Configuración → tu cuenta.",
                    "Notificaciones → elige \"Solo alta prioridad\".",
                )
                CyberButton("Abrir Gmail", actions::openGmail, Modifier.fillMaxWidth(), filled = false)
            }
        }
        items(state.mail) { m ->
            val subject = m.text.lineSequence().first()
            val snippet = m.text.substringAfter('\n', "").trim()
            CyberPanel(accent = Neon.Yellow, onClick = actions::openGmail) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("Prioridad", Neon.Yellow)
                    Text(m.chat, style = MaterialTheme.typography.labelLarge, color = Neon.Cyan,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(FECHA_MSG.format(Instant.ofEpochMilli(m.time)),
                        style = MaterialTheme.typography.labelSmall, color = Neon.Muted)
                }
                Text(subject, style = MaterialTheme.typography.titleMedium)
                if (snippet.isNotEmpty()) Text(snippet, style = MaterialTheme.typography.bodySmall,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (state.mail.isNotEmpty()) item {
            CyberButton("Borrar historial", actions::clearMail, color = Neon.Red, filled = false)
        }

        // Opción avanzada: leer la bandeja con la API de Gmail (requiere registrar la app en Google Cloud).
        item { SectionHeader("Bandeja completa · avanzado", Neon.Muted) }
        item {
            CyberButton(if (state.emails.isEmpty()) "Conectar API de Gmail" else "Sincronizar",
                actions::refreshEmails, Modifier.fillMaxWidth(), color = Neon.Muted, filled = false)
        }
        if (state.gmailNeedsSetup) item { GmailSetupPanel(actions) }
        else if (state.emailStatus.isNotEmpty()) item {
            StatusLine(state.emailStatus, if (state.emailStatus.startsWith("Error")) Neon.Red else Neon.Cyan)
        }
        items(state.emails, key = { it.id }) { e ->
            CyberPanel(accent = Neon.Yellow, onClick = { actions.openUrl(e.webUrl) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("Prioridad", Neon.Yellow)
                    Text(e.fromName, style = MaterialTheme.typography.labelLarge, color = Neon.Cyan,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(e.subject, style = MaterialTheme.typography.titleMedium)
                Text(
                    Html.fromHtml(e.snippet, Html.FROM_HTML_MODE_LEGACY).toString(),
                    style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GmailSetupPanel(actions: AppActions) {
    CyberPanel(accent = Neon.Red, glow = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Blink(Neon.Red)
            Text("GMAIL SIN ENLAZAR", style = MaterialTheme.typography.titleMedium, color = Neon.Red)
        }
        Text("Google pide registrar la app una sola vez antes de dejarla leer tu correo.",
            style = MaterialTheme.typography.bodyMedium)
        Steps(
            "En console.cloud.google.com crea un proyecto y habilita Gmail API.",
            "En la pantalla de consentimiento agrega tu correo como usuario de prueba.",
            "Crea un ID de cliente OAuth tipo Android con el paquete com.asistente.personal y la SHA-1 de la guía.",
        )
        CyberButton("Abrir guía", { actions.openUrl(GMAIL_GUIDE_URL) }, Modifier.fillMaxWidth(),
            color = Neon.Red, filled = false)
    }
}

// ================= MENSAJES =================

@Composable
fun MessagesScreen(state: AppState, actions: AppActions) {
    var onlyImportant by rememberSaveable { mutableStateOf(true) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val shown = if (onlyImportant) state.messages.filter { it.important } else state.messages

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LIST_PADDING, verticalArrangement = GAP) {
        if (!state.listenerEnabled) item { AccessPanel(actions) }
        item {
            SectionHeader("Feed WhatsApp", Neon.Magenta,
                trailing = "${state.messages.count { it.important }} prioritarios")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton("Prioritarios", { onlyImportant = true }, Modifier.weight(1f),
                    color = Neon.Magenta, filled = onlyImportant)
                CyberButton("Todos", { onlyImportant = false }, Modifier.weight(1f),
                    color = Neon.Cyan, filled = !onlyImportant)
                CyberButton("Filtro", { showFilter = !showFilter }, color = Neon.Yellow, filled = showFilter)
            }
        }
        if (showFilter) item { FilterPanel(state, actions, onDone = { showFilter = false }) }
        if (shown.isEmpty()) item {
            CyberPanel(accent = Neon.Muted) {
                Text("> SIN SEÑAL", style = MaterialTheme.typography.labelLarge, color = Neon.Muted)
                Text(
                    if (onlyImportant) "Aún no llegan mensajes prioritarios." else "Aún no hay mensajes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(shown) { m ->
            CyberPanel(accent = if (m.important) Neon.Magenta else Neon.Muted.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.important) {
                        Tag("Prioritario", Neon.Magenta)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(m.chat, style = MaterialTheme.typography.titleSmall,
                        color = if (m.important) Neon.Text else Neon.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(FECHA_MSG.format(Instant.ofEpochMilli(m.time)),
                        style = MaterialTheme.typography.labelSmall, color = Neon.Muted)
                }
                Text(m.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.messages.isNotEmpty()) item {
            CyberButton("Borrar historial", actions::clearMessages, color = Neon.Red, filled = false)
        }
    }
}

@Composable
private fun AccessPanel(actions: AppActions) {
    CyberPanel(accent = Neon.Red, glow = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Blink(Neon.Red)
            Text("ACCESO BLOQUEADO", style = MaterialTheme.typography.titleMedium, color = Neon.Red)
        }
        Text("Para leer WhatsApp y Gmail la app necesita acceso a notificaciones. Android lo bloquea en apps " +
            "instaladas desde APK; se desbloquea una sola vez:", style = MaterialTheme.typography.bodyMedium)
        Steps(
            "Toca ACCESO A NOTIFICACIONES y toca el interruptor gris aunque esté bloqueado. " +
                "Saldrá el aviso \"Restricted setting\": dale OK.",
            "Toca AJUSTES DE LA APP. Ahora sí aparece ⋮ arriba a la derecha → \"Allow restricted settings\" " +
                "(Permitir ajustes restringidos). Confirma con tu huella o PIN.",
            "Vuelve a ACCESO A NOTIFICACIONES y activa el interruptor.",
        )
        CyberButton("01 · Acceso a notificaciones", actions::openNotificationAccess, Modifier.fillMaxWidth())
        CyberButton("02 · Ajustes de la app", actions::openAppSettings, Modifier.fillMaxWidth(),
            color = Neon.Cyan, filled = false)
    }
}

@Composable
private fun FilterPanel(state: AppState, actions: AppActions, onDone: () -> Unit) {
    var keywords by remember { mutableStateOf(state.keywords) }
    var vip by remember { mutableStateOf(state.vipChats) }
    var hide by remember { mutableStateOf(state.hideUnimportant) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Neon.Yellow, unfocusedBorderColor = Neon.Muted.copy(alpha = 0.6f),
        focusedLabelColor = Neon.Yellow, unfocusedLabelColor = Neon.Muted, cursorColor = Neon.Yellow,
        focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text,
    )
    CyberPanel(accent = Neon.Yellow) {
        Text("CONFIGURAR FILTRO", style = MaterialTheme.typography.labelLarge, color = Neon.Yellow)
        OutlinedTextField(keywords, { keywords = it }, Modifier.fillMaxWidth(),
            label = { Text("Palabras clave (separadas por coma)") }, shape = ButtonShape, colors = fieldColors)
        OutlinedTextField(vip, { vip = it }, Modifier.fillMaxWidth(),
            label = { Text("Chats o grupos siempre importantes") }, shape = ButtonShape, colors = fieldColors)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ocultar notificaciones de grupos que no sean importantes",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(hide, { hide = it }, colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = Neon.Magenta,
                uncheckedThumbColor = Neon.Muted, uncheckedTrackColor = Neon.Panel, uncheckedBorderColor = Neon.Muted,
            ))
        }
        Text("Tip: agrega @TuNombre como palabra clave para detectar cuando te mencionan.",
            style = MaterialTheme.typography.bodySmall)
        CyberButton("Guardar", { actions.saveFilter(keywords, vip, hide); onDone() }, Modifier.fillMaxWidth())
    }
}

// ================= T1 =================

@Composable
fun T1Screen(state: AppState, actions: AppActions, now: Instant) {
    val next = T1Schedule.next(state.matches)
    val upcoming = state.matches.filter { !it.finished }.drop(1)
    val past = state.matches.filter { it.finished }.reversed()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LIST_PADDING, verticalArrangement = GAP) {
        item { SectionHeader("T1 // Calendario", Neon.Red, trailing = "Hora CDMX") }
        if (state.t1Status.isNotEmpty()) item { StatusLine(state.t1Status) }
        if (next != null) item { NextMatchHero(next, now, actions) }
        else if (state.matches.isNotEmpty()) item {
            CyberPanel(accent = Neon.Violet) {
                Tag("Offseason", Neon.Violet)
                Text("SIN PARTIDOS PROGRAMADOS", style = MaterialTheme.typography.titleLarge)
                Text("T1 está en pretemporada. Su próximo partido aparecerá aquí en cuanto se publique.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionHeader("Próximos", Neon.Cyan) }
            items(upcoming, key = { it.id }) { UpcomingRow(it, actions) }
        }
        if (state.news.isNotEmpty()) {
            item { SectionHeader("Noticias", Neon.Violet, trailing = "Google News") }
            items(state.news) { n ->
                CyberPanel(accent = Neon.Violet, onClick = { actions.openUrl(n.link) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag(n.source.ifEmpty { "Noticia" }.take(24), Neon.Violet)
                        Text(T1News.hace(n.published, now).uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = Neon.Muted)
                    }
                    Text(n.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (past.isNotEmpty()) {
            item { SectionHeader("Resultados", Neon.Muted) }
            items(past, key = { it.id }) { ResultRow(it) }
        }
        item {
            CyberButton("Actualizar", actions::refreshT1, Modifier.fillMaxWidth(), color = Neon.Cyan, filled = false)
        }
    }
}

@Composable
private fun NextMatchHero(m: Match, now: Instant, actions: AppActions) {
    val live = m.state == "inProgress"
    CyberPanel(accent = Neon.Red, glow = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live) { Blink(Neon.Red); Tag("En vivo", Neon.Red) } else Tag("Próximo partido", Neon.Yellow)
            Text("${m.league} · Bo${m.bestOf}".uppercase(), style = MaterialTheme.typography.labelMedium, color = Neon.Muted)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GlitchText("T1", MaterialTheme.typography.displaySmall.copy(fontSize = 56.sp), color = Neon.Red)
            Text("VS", style = MaterialTheme.typography.headlineSmall, color = Neon.Muted)
            Text(m.opponentCode.ifEmpty { m.opponent }, style = MaterialTheme.typography.displaySmall.copy(fontSize = 56.sp))
        }
        Text("${m.opponent} · ${m.block}", style = MaterialTheme.typography.bodySmall)
        Column(
            Modifier.fillMaxWidth()
                .background(Neon.Bg.copy(alpha = 0.7f), ButtonShape)
                .border(1.dp, Neon.Yellow.copy(alpha = 0.4f), ButtonShape)
                .padding(12.dp)
        ) {
            Text("MÉXICO · CDMX", style = MaterialTheme.typography.labelSmall, color = Neon.Cyan)
            Text(T1Schedule.fechaMx(m.start), style = MaterialTheme.typography.titleMedium)
            Text(T1Schedule.horaMx(m.start), style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp),
                color = Neon.Yellow)
            Text("Corea: ${T1Schedule.horaCorea(m.start)}", style = MaterialTheme.typography.bodySmall)
        }
        if (!live) Text("T-MENOS ${T1Schedule.faltan(m.start, now)}",
            style = MaterialTheme.typography.labelLarge, color = Neon.Magenta)
        CyberButton("Agregar al calendario", { actions.addToCalendar(m) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun UpcomingRow(m: Match, actions: AppActions) {
    CyberPanel(accent = Neon.Cyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("T1 vs ${m.opponent}", style = MaterialTheme.typography.titleMedium)
                Text("${T1Schedule.fechaMx(m.start)} · ${T1Schedule.horaMx(m.start)}",
                    style = MaterialTheme.typography.bodyMedium, color = Neon.Yellow)
                Text("${m.league} · ${m.block}", style = MaterialTheme.typography.bodySmall)
            }
            CyberButton("+ Cal", { actions.addToCalendar(m) }, color = Neon.Cyan, filled = false)
        }
    }
}

@Composable
private fun ResultRow(m: Match) {
    val t1 = m.t1Wins ?: 0
    val rival = m.opponentWins ?: 0
    val won = t1 > rival
    CyberPanel(accent = if (won) Neon.Cyan else Neon.Red.copy(alpha = 0.7f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (won) "W" else "L", style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp),
                color = if (won) Neon.Cyan else Neon.Red, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text("T1 $t1 - $rival ${m.opponentCode}", style = MaterialTheme.typography.titleMedium)
                Text("${m.league} · ${m.block}", style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                m.mvp?.let {
                    Row(
                        Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Tag("MVP", Neon.Yellow)
                        Text(it, style = MaterialTheme.typography.labelLarge, color = Neon.Yellow)
                    }
                }
            }
            Text(T1Schedule.fechaCorta(m.start), style = MaterialTheme.typography.labelSmall, color = Neon.Muted)
        }
    }
}
