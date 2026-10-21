package sm.mar.diario.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sm.mar.diario.data.Activity
import sm.mar.diario.data.Dates
import sm.mar.diario.data.InboxItem
import sm.mar.diario.data.Repo
import sm.mar.diario.data.Settings
import sm.mar.diario.data.fmtDuration
import sm.mar.diario.importers.CallImporter
import sm.mar.diario.importers.WhatsAppListener
import sm.mar.diario.work.SyncWorker

private data class Section(val title: String, val icon: ImageVector, val items: List<InboxItem>, val empty: String)

@Composable
fun InboxScreen(ui: UiState, inbox: List<InboxItem>, settings: Settings, modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    var tab by remember { mutableIntStateOf(0) }
    val sorted = inbox.sortedByDescending { it.ts }
    val sections = listOf(
        Section("Telefonate", Icons.Filled.Call, sorted.filter { it.kind == "call" }, "Nessuna telefonata da registrare."),
        Section("In arrivo", Icons.Filled.Email, sorted.filter { it.kind == "mail" && it.direction != "out" }, "Nessuna email ricevuta da registrare."),
        Section("Inviate", Icons.AutoMirrored.Filled.Send, sorted.filter { it.kind == "mail" && it.direction == "out" }, "Nessuna email inviata da registrare."),
        Section("WhatsApp", Icons.Filled.Face, sorted.filter { it.kind == "whatsapp" }, "Nessuna chat WhatsApp da registrare."),
    )
    val colors = listOf(p.call, p.mailIn, p.mailOut, p.wa)

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("${inbox.size} in attesa")
                Text("Da registrare", style = MaterialTheme.typography.headlineSmall)
            }
            FilledTonalButton(onClick = {
                SyncWorker.runNow(ctx)
                scope.launch { runCatching { Repo.updateSettings(mapOf("lastCheckin" to System.currentTimeMillis())) } }
                ui.toast("Cerco nuove telefonate ed email…")
            }) {
                Icon(Icons.Filled.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Fai il punto")
            }
        }
        val missing = buildList {
            if (!CallImporter.hasPermission(ctx)) add("registro chiamate")
            if (!WhatsAppListener.isEnabled(ctx)) add("notifiche WhatsApp")
        }
        if (missing.isNotEmpty()) {
            Card(
                onClick = { ui.tab = "settings" },
                colors = CardDefaults.cardColors(containerColor = p.warnSoft),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    "Per importare in automatico attiva l'accesso a: ${missing.joinToString(", ")}. Tocca per aprire le impostazioni.",
                    color = p.warn, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
                )
            }
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
            sections.forEachIndexed { i, s ->
                Tab(
                    selected = tab == i, onClick = { tab = i },
                    text = { Text(if (s.items.isEmpty()) s.title else "${s.title} ${s.items.size}") },
                    icon = { Icon(s.icon, null, tint = if (tab == i) colors[i] else p.muted) },
                )
            }
        }
        val s = sections[tab]
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.items.isEmpty()) item { EmptyState("Tutto in ordine", s.empty) }
            items(s.items, key = { it.id }) { item ->
                InboxCard(
                    item, colors[tab],
                    onImport = { ui.editor = EditorSeed(seedFrom(item), import = item) },
                    onIgnore = {
                        Repo.setInboxStatus(item.id, "ignored")
                        ui.toast("Ignorato", "Annulla") { Repo.setInboxStatus(item.id, "new") }
                    },
                    onBlacklist = {
                        scope.launch {
                            val ids = runCatching { Repo.blacklist(item, inbox) }.getOrDefault(emptyList())
                            val what = when (item.kind) { "call" -> "questo numero"; "whatsapp" -> "questa chat"; else -> "questo indirizzo" }
                            ui.toast("Non ti verrà più proposto $what", "Annulla") { scope.launch { runCatching { Repo.unBlacklist(item, ids) } } }
                        }
                    },
                )
            }
        }
    }
}

fun seedFrom(m: InboxItem): Activity {
    val type = when {
        m.kind == "call" -> "call"
        m.kind == "whatsapp" -> "whatsapp"
        m.direction == "out" -> "email_out"
        else -> "email_in"
    }
    val notes = when (m.kind) {
        "call" -> if (m.direction == "missed") "Chiamata persa" else "Telefonata ${if (m.direction == "out") "in uscita" else "in entrata"}, ${fmtDuration(m.duration)}"
        "whatsapp" -> m.messages.joinToString("\n") { (if (it.from != m.contact) it.from + ": " else "") + it.text }.ifBlank { m.snippet }
        else -> m.snippet
    }
    return Activity(
        type = type, contact = m.contact, company = m.company,
        email = if (m.kind == "mail") m.address else m.email,
        phone = if (m.kind == "call") m.address else "",
        subject = if (m.kind == "mail") m.subject else "",
        notes = notes, date = m.date.ifEmpty { Dates.today() }, time = m.time,
        source = "import", importId = m.id,
    )
}

@Composable
private fun InboxCard(m: InboxItem, color: Color, onImport: () -> Unit, onIgnore: () -> Unit, onBlacklist: () -> Unit) {
    val p = LocalPalette.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono("${Dates.short(m.date)} ${m.time}", modifier = Modifier.weight(1f))
                when (m.kind) {
                    "call" -> Pill(
                        when (m.direction) { "missed" -> "persa"; "out" -> "in uscita · ${fmtDuration(m.duration)}"; else -> "in entrata · ${fmtDuration(m.duration)}" },
                        if (m.direction == "missed") p.warn else color,
                    )
                    "whatsapp" -> Pill("${m.messages.size.coerceAtLeast(1)} messaggi", color)
                    else -> {}
                }
            }
            Text(
                m.contact.ifBlank { m.address }, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (m.company.isNotBlank()) Text(m.company, style = MaterialTheme.typography.bodySmall, color = p.muted)
            if (m.kind != "whatsapp" && m.address.isNotBlank()) Mono((if (m.kind == "mail") (if (m.direction == "out") "a " else "da ") else "") + m.address)
            if (m.kind == "mail") Text(m.subject.ifBlank { "(senza oggetto)" }, style = MaterialTheme.typography.bodyMedium)
            if (m.kind == "whatsapp") {
                m.messages.takeLast(3).forEach {
                    Text(it.text, style = MaterialTheme.typography.bodyMedium, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else if (m.snippet.isNotBlank()) {
                Text(m.snippet, style = MaterialTheme.typography.bodyMedium, color = p.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = onImport) { Text("Importa") }
                TextButton(onClick = onIgnore) { Text("Ignora") }
                TextButton(onClick = onBlacklist) { Text("Blacklist", color = LocalPalette.current.muted) }
            }
        }
    }
}
