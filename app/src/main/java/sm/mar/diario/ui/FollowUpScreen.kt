package sm.mar.diario.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import sm.mar.diario.data.Dates
import sm.mar.diario.data.FollowUp
import sm.mar.diario.data.Repo

@Composable
fun FollowUpScreen(ui: UiState, fus: List<FollowUp>, modifier: Modifier) {
    val today = Dates.today()
    val week = Dates.addDays(today, 7)
    val sorted = fus.sortedBy { it.due }
    val groups = listOf(
        "Scaduti" to sorted.filter { it.due < today },
        "Oggi" to sorted.filter { it.due == today },
        "Prossimi 7 giorni" to sorted.filter { it.due > today && it.due <= week },
        "Più avanti" to sorted.filter { it.due > week },
    ).filter { it.second.isNotEmpty() }
    var editing by remember { mutableStateOf<FollowUp?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) {
                    Label("${fus.size} aperti")
                    Text("Follow-up", style = MaterialTheme.typography.headlineSmall)
                }
            }
            if (groups.isEmpty()) item { EmptyState("Nessun follow-up aperto", "Quando registri un'attività scegli \"Registra + follow-up\" per ricordarti il passo successivo.") }
            groups.forEach { (title, list) ->
                item(key = "h-$title") {
                    Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = if (title == "Scaduti") LocalPalette.current.warn else MaterialTheme.colorScheme.onBackground)
                        Mono("  ${list.size}")
                    }
                }
                items(list, key = { it.id }) { f -> FollowUpRow(ui, f, today) { editing = f } }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = FollowUp(due = Dates.addWorkDays(today, 1)) },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nuovo") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    editing?.let { f -> FollowUpDialog(f) { editing = null } }
}

@Composable
private fun FollowUpRow(ui: UiState, f: FollowUp, today: String, onEdit: () -> Unit) {
    val p = LocalPalette.current
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = false,
                onCheckedChange = {
                    Repo.updateFollowUp(f.id, mapOf("done" to true, "doneAt" to System.currentTimeMillis()))
                    ui.toast("Follow-up completato", "Annulla") { Repo.updateFollowUp(f.id, mapOf("done" to false)) }
                },
                colors = CheckboxDefaults.colors(uncheckedColor = p.muted),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(f.title, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (f.due == today) "oggi" else Dates.short(f.due), if (f.due < today) p.warn else if (f.due == today) MaterialTheme.colorScheme.primary else p.muted)
                    if (f.company.isNotBlank()) Text(f.company, style = MaterialTheme.typography.bodySmall, color = p.muted)
                }
            }
            val base = if (f.due < today) today else f.due
            TextButton(onClick = { Repo.updateFollowUp(f.id, mapOf("due" to Dates.addWorkDays(base, 1))) }) { Text("+1 g") }
            TextButton(onClick = { Repo.updateFollowUp(f.id, mapOf("due" to Dates.addDays(base, 7))) }) { Text("+7") }
        }
        HorizontalDivider(color = p.line, modifier = Modifier.padding(start = 52.dp))
    }
}

@Composable
private fun FollowUpDialog(f: FollowUp, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(f.title) }
    var contact by remember { mutableStateOf(f.contact) }
    var company by remember { mutableStateOf(f.company) }
    var due by remember { mutableStateOf(f.due.ifEmpty { Dates.today() }) }
    val isNew = f.id.isEmpty()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Nuovo follow-up" else "Modifica follow-up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Cosa fare") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contact, { contact = it }, label = { Text("Contatto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(company, { company = it }, label = { Text("Azienda") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PickerField("Scadenza", Dates.long(due), Modifier.fillMaxWidth()) { pickDate(ctx, due) { due = it } }
                if (!isNew) TextButton(onClick = { Repo.deleteFollowUp(f.id); onClose() }) { Text("Elimina follow-up", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                if (isNew) Repo.addFollowUp(title.trim(), contact.trim(), company.trim(), due)
                else Repo.updateFollowUp(f.id, mapOf("title" to title.trim(), "contact" to contact.trim(), "company" to company.trim(), "due" to due))
                onClose()
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Annulla") } },
    )
}
