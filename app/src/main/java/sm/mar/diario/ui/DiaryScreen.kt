package sm.mar.diario.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sm.mar.diario.data.ActType
import sm.mar.diario.data.Activity
import sm.mar.diario.data.Dates
import sm.mar.diario.data.FollowUp
import sm.mar.diario.data.InboxItem
import sm.mar.diario.data.Repo

@Composable
fun DiaryScreen(ui: UiState, recent: List<Activity>, fus: List<FollowUp>, inbox: List<InboxItem>, modifier: Modifier) {
    val ctx = LocalContext.current
    val dayActs by remember(ui.day) { Repo.day(ui.day) }.collectAsState(initial = emptyList())
    var searching by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Activity?>(null) }
    val today = Dates.today()

    val list = if (searching && q.isNotBlank()) {
        val ql = q.trim().lowercase()
        recent.filter { listOf(it.contact, it.email, it.phone, it.company, it.subject, it.notes).joinToString(" ").lowercase().contains(ql) }
    } else dayActs.sortedBy { it.time }

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label("Diario MAR")
                            Text("Diario", style = MaterialTheme.typography.headlineSmall)
                        }
                        IconButton(onClick = { searching = !searching; if (!searching) q = "" }) {
                            Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Cerca")
                        }
                    }
                    if (searching) {
                        OutlinedTextField(
                            value = q, onValueChange = { q = it }, singleLine = true,
                            placeholder = { Text("Contatto, azienda, oggetto…") },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Label(if (q.isBlank()) "Cerca nelle ultime 400 attività" else "${list.size} risultati")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            IconButton(onClick = { ui.day = Dates.addDays(ui.day, -1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Giorno precedente") }
                            Text(
                                Dates.long(ui.day), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).clickable { pickDate(ctx, ui.day) { ui.day = it } }.padding(vertical = 8.dp),
                            )
                            if (ui.day != today) TextButton(onClick = { ui.day = today }) { Text("Oggi") }
                            IconButton(onClick = { ui.day = Dates.addDays(ui.day, 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Giorno successivo") }
                        }
                        if (ui.day == today) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp, top = 2.dp)) {
                                Stat(dayActs.count { it.type == "call" }, "telefonate")
                                Stat(dayActs.count { it.type.startsWith("email") }, "email")
                                Stat(dayActs.count { it.type == "whatsapp" }, "WhatsApp")
                                Stat(fus.count { it.due < today }, "scaduti", warn = true)
                            }
                            if (inbox.isNotEmpty()) {
                                Card(
                                    onClick = { ui.tab = "inbox" },
                                    colors = CardDefaults.cardColors(containerColor = LocalPalette.current.warnSoft),
                                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp),
                                ) {
                                    Text(
                                        "${inbox.size} elementi da registrare: tocca per fare il punto",
                                        color = LocalPalette.current.warn, style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        if (searching) "Nessun risultato" else "Nessuna attività in questo giorno",
                        if (searching) "Prova con un altro nome o un'altra parola." else "Tocca \"Nuova\" per registrare una telefonata, un'email o un incontro.",
                    )
                }
            }
            items(list, key = { it.id }) { a ->
                ActivityRow(a, showDate = searching, onClick = { ui.editor = EditorSeed(a, isEdit = true) }, onDelete = { toDelete = a })
            }
        }
        ExtendedFloatingActionButton(
            onClick = { ui.newActivity() },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nuova") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    toDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare l'attività?") },
            text = { Text("${a.time} · ${a.contact.ifBlank { a.company.ifBlank { ActType.of(a.type).label } }}") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.deleteActivity(a.id)
                    toDelete = null
                    ui.toast("Attività eliminata", "Annulla") { Repo.restoreActivity(a) }
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla") } },
        )
    }
}

@Composable
fun ActivityRow(a: Activity, showDate: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val t = ActType.of(a.type)
    val c = typeColor(t)
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.End) {
            Mono(a.time)
            if (showDate) Mono(Dates.short(a.date))
        }
        Box(Modifier.padding(start = 10.dp, end = 10.dp, top = 5.dp)) { Dot(c) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.contact.ifBlank { a.company.ifBlank { a.email.ifBlank { a.phone.ifBlank { "—" } } } },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Pill(t.label, c)
            }
            if (a.contact.isNotBlank() && a.company.isNotBlank()) Text(a.company, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
            val addr = listOf(a.email, a.phone).filter { it.isNotBlank() }.joinToString(" · ")
            if (addr.isNotBlank()) Mono(addr)
            if (a.subject.isNotBlank()) Text(a.subject, style = MaterialTheme.typography.bodyMedium)
            if (a.notes.isNotBlank()) Text(
                a.notes, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.muted,
                maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Elimina", tint = LocalPalette.current.muted) }
    }
}
