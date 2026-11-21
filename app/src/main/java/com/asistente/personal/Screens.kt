package com.asistente.personal

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.text.Html
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asistente.personal.t1.Match
import com.asistente.personal.t1.T1Schedule
import com.asistente.personal.whatsapp.FilterConfig
import com.asistente.personal.whatsapp.MessageStore

private val LIST_PADDING = PaddingValues(16.dp)
private val GAP = Arrangement.spacedBy(8.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsistenteApp(app: MainActivity) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("📧" to "Correos", "💬" to "Mensajes", "🏆" to "T1")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asistente") },
                actions = { TextButton(onClick = app::speakSummary) { Text("🔊 Resumen") } },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(icon) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> EmailsScreen(app)
                1 -> MessagesScreen(app)
                else -> T1Screen(app)
            }
        }
    }
}

// ---------- Correos ----------

@Composable
fun EmailsScreen(app: MainActivity) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = LIST_PADDING, verticalArrangement = GAP) {
        item {
            Button(onClick = app::refreshEmails) {
                Text(if (app.emails.isEmpty()) "Conectar Gmail" else "Actualizar")
            }
        }
        if (app.emailStatus.isNotEmpty()) item { Text(app.emailStatus) }
        items(app.emails, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth().clickable { openUrl(ctx, e.webUrl) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(e.fromName, fontWeight = FontWeight.Bold)
                    Text(e.subject, style = MaterialTheme.typography.titleSmall)
                    Text(
                        Html.fromHtml(e.snippet, Html.FROM_HTML_MODE_LEGACY).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

// ---------- Mensajes de WhatsApp ----------

@Composable
fun MessagesScreen(app: MainActivity) {
    val ctx = LocalContext.current
    var messages by remember { mutableStateOf(MessageStore.load(ctx)) }
    // Se actualiza solo cuando el servicio guarda un mensaje nuevo.
    DisposableEffect(Unit) {
        val prefs = MessageStore.prefs(ctx)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> messages = MessageStore.load(ctx) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var onlyImportant by rememberSaveable { mutableStateOf(true) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val shown = if (onlyImportant) messages.filter { it.important } else messages

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LIST_PADDING, verticalArrangement = GAP) {
        if (!app.listenerEnabled) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = GAP) {
                    Text("Para leer tus mensajes de WhatsApp, activa el acceso a notificaciones para \"Asistente\".")
                    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text("Dar acceso")
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = GAP) {
                FilterChip(selected = onlyImportant, onClick = { onlyImportant = true }, label = { Text("Importantes") })
                FilterChip(selected = !onlyImportant, onClick = { onlyImportant = false }, label = { Text("Todos") })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showSettings = !showSettings }) { Text("⚙️ Filtro") }
            }
        }
        if (showSettings) item { FilterSettings(onDone = { showSettings = false }) }
        if (shown.isEmpty()) item {
            Text(if (onlyImportant) "Sin mensajes importantes por ahora." else "Aún no hay mensajes.")
        }
        items(shown) { m ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (m.important) "⭐ " else "") + m.chat,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            DateUtils.getRelativeTimeSpanString(m.time).toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(m.text)
                }
            }
        }
        if (messages.isNotEmpty()) item {
            TextButton(onClick = { MessageStore.clear(ctx) }) { Text("Borrar historial") }
        }
    }
}

@Composable
fun FilterSettings(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var keywords by remember { mutableStateOf(FilterConfig.keywords(ctx)) }
    var vip by remember { mutableStateOf(FilterConfig.vipChats(ctx)) }
    var hide by remember { mutableStateOf(FilterConfig.hideUnimportant(ctx)) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = GAP) {
            OutlinedTextField(
                value = keywords, onValueChange = { keywords = it },
                label = { Text("Palabras clave (separadas por coma)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vip, onValueChange = { vip = it },
                label = { Text("Chats o grupos siempre importantes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ocultar notificaciones de grupos que no sean importantes", Modifier.weight(1f))
                Switch(checked = hide, onCheckedChange = { hide = it })
            }
            Text(
                "Tip: agrega @TuNombre como palabra clave para detectar cuando te mencionan en un grupo.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { FilterConfig.save(ctx, keywords, vip, hide); onDone() }) { Text("Guardar") }
        }
    }
}

// ---------- T1 ----------

@Composable
fun T1Screen(app: MainActivity) {
    val next = T1Schedule.next(app.matches)
    val upcoming = app.matches.filter { !it.finished }.drop(1)
    val past = app.matches.filter { it.finished }.reversed()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LIST_PADDING, verticalArrangement = GAP) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Horarios en hora del centro de México 🇲🇽", Modifier.weight(1f))
                TextButton(onClick = app::refreshT1) { Text("Actualizar") }
            }
        }
        if (app.t1Status.isNotEmpty()) item { Text(app.t1Status) }
        if (next != null) item { MatchCard(next, highlight = true) }
        else if (app.matches.isNotEmpty()) item {
            Text("T1 no tiene partidos programados por ahora. Estos son sus últimos resultados:")
        }
        if (upcoming.isNotEmpty()) {
            item { Text("Próximos", style = MaterialTheme.typography.titleMedium) }
            items(upcoming, key = { it.id }) { MatchCard(it) }
        }
        if (past.isNotEmpty()) {
            item { Text("Resultados recientes", style = MaterialTheme.typography.titleMedium) }
            items(past, key = { it.id }) { MatchCard(it) }
        }
    }
}

@Composable
fun MatchCard(m: Match, highlight: Boolean = false) {
    val ctx = LocalContext.current
    val colors = if (highlight) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (highlight) Text(
                if (m.state == "inProgress") "🔴 EN VIVO" else "Próximo partido",
                style = MaterialTheme.typography.labelLarge,
            )
            Text("T1 vs ${m.opponent}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${m.league} · ${m.block} · Bo${m.bestOf}", style = MaterialTheme.typography.bodySmall)
            Text("🇲🇽 ${T1Schedule.fechaMx(m.start)}, ${T1Schedule.horaMx(m.start)}")
            if (m.finished) {
                val t1 = m.t1Wins ?: 0
                val rival = m.opponentWins ?: 0
                Text("${if (t1 > rival) "✅ Ganó" else "❌ Perdió"} $t1 - $rival")
            } else {
                Text("🇰🇷 En Corea: ${T1Schedule.horaCorea(m.start)}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { addToCalendar(ctx, m) }) { Text("📅 Agregar recordatorio al calendario") }
            }
        }
    }
}

private fun addToCalendar(ctx: Context, m: Match) {
    val start = m.start.toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + m.bestOf * 60 * 60 * 1000L)
        .putExtra(CalendarContract.Events.TITLE, "T1 vs ${m.opponent} (${m.league})")
        .putExtra(CalendarContract.Events.DESCRIPTION, "Ver en https://lolesports.com")
    runCatching { ctx.startActivity(intent) }
        .onFailure { Toast.makeText(ctx, "No hay app de calendario instalada", Toast.LENGTH_SHORT).show() }
}

private fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
