package sm.mar.diario.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sm.mar.diario.data.ActType
import sm.mar.diario.data.Activity
import sm.mar.diario.data.Dates
import sm.mar.diario.data.Repo
import sm.mar.diario.data.normPhone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(ui: UiState, seed: EditorSeed, recent: List<Activity>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val a0 = seed.activity
    var type by remember { mutableStateOf(a0.type) }
    var email by remember { mutableStateOf(a0.email) }
    var phone by remember { mutableStateOf(a0.phone) }
    var contact by remember { mutableStateOf(a0.contact) }
    var company by remember { mutableStateOf(a0.company) }
    var subject by remember { mutableStateOf(a0.subject) }
    var notes by remember { mutableStateOf(a0.notes) }
    var date by remember { mutableStateOf(a0.date.ifEmpty { Dates.today() }) }
    var time by remember { mutableStateOf(a0.time.ifEmpty { Dates.now() }) }
    var withFu by remember { mutableStateOf(false) }
    var fuText by remember { mutableStateOf("") }
    var fuDate by remember { mutableStateOf("") }
    var fuTouched by remember { mutableStateOf(false) }
    var askIgnore by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val imp = seed.import

    // completa nome e azienda da attività precedenti con la stessa email o lo stesso numero
    LaunchedEffect(Unit) {
        if (contact.isBlank() || company.isBlank()) {
            val match = recent.firstOrNull {
                (email.isNotBlank() && it.email.equals(email, true)) || (phone.isNotBlank() && it.phone.isNotBlank() && normPhone(it.phone) == normPhone(phone))
            }
            if (match != null) {
                if (contact.isBlank()) contact = match.contact
                if (company.isBlank()) company = match.company
            }
        }
    }

    fun defaultFu(): String {
        val who = contact.ifBlank { company.ifBlank { email.ifBlank { phone } } }
        val missed = type == "call" && notes.startsWith("Chiamata persa")
        val verb = when (type) {
            "call" -> if (missed) "Richiamare" else "Ricontattare"
            "email_out" -> "Verificare risposta di"
            "email_in", "whatsapp" -> "Rispondere a"
            "meeting" -> "Inviare riepilogo a"
            else -> "Ricontattare"
        }
        return "$verb $who".trim()
    }

    fun defaultFuDate(): String {
        val missed = type == "call" && notes.startsWith("Chiamata persa")
        return if (missed) Dates.today() else Dates.addWorkDays(Dates.today(), if (type == "email_in" || type == "whatsapp") 1 else 3)
    }

    fun save(forceFu: Boolean) {
        if (listOf(contact, company, subject, notes, email, phone).all { it.isBlank() }) {
            ui.toast("Scrivi almeno un contatto, un'azienda o un oggetto.")
            return
        }
        val act = a0.copy(
            type = type, contact = contact.trim(), email = email.trim(), phone = phone.trim(), company = company.trim(),
            subject = subject.trim(), notes = notes.trim(), date = date, time = time, ts = Dates.tsOf(date, time),
            source = if (imp != null) "import" else a0.source.ifEmpty { "manual" }, importId = imp?.id ?: a0.importId,
        )
        val id = Repo.saveActivity(act)
        if (imp != null) Repo.setInboxStatus(imp.id, "logged", id)
        val fu = withFu || forceFu
        if (fu) Repo.addFollowUp(fuText.ifBlank { defaultFu() }, act.contact, act.company, fuDate.ifBlank { defaultFuDate() }, id)
        ui.day = date
        ui.editor = null
        ui.toast((if (seed.isEdit) "Attività aggiornata" else "Attività registrata") + if (fu) " con follow-up" else "")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            imp?.kind == "call" -> "Telefonata importata"
                            imp?.kind == "whatsapp" -> "Chat WhatsApp importata"
                            imp != null -> "Email importata"
                            seed.isEdit -> "Modifica attività"
                            else -> "Registra attività"
                        }
                    )
                },
                navigationIcon = { IconButton(onClick = { ui.editor = null }) { Icon(Icons.Filled.Close, "Chiudi") } },
                actions = {
                    if (imp != null) TextButton(onClick = { askIgnore = true }) { Text("Ignora") }
                    else if (seed.isEdit) TextButton(onClick = { askDelete = true }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    HorizontalDivider(color = LocalPalette.current.line)
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            seed.isEdit -> {
                                OutlinedButton(onClick = { save(true) }) { Text("Salva + follow-up") }
                                Button(onClick = { save(false) }) { Text("Salva") }
                            }
                            else -> {
                                OutlinedButton(onClick = { save(true) }) { Text("Registra + follow-up") }
                                Button(onClick = { save(false) }) { Text("Registra") }
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActType.entries.forEach { t ->
                    val c = typeColor(t)
                    FilterChip(
                        selected = type == t.key, onClick = { type = t.key },
                        label = { Text(t.label) },
                        leadingIcon = { Dot(c) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.16f)),
                    )
                }
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it }, label = { Text("Telefono") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = contact, onValueChange = { contact = it }, label = { Text("Nome e cognome") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = company, onValueChange = { company = it }, label = { Text("Azienda") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subject, onValueChange = { subject = it }, label = { Text("Oggetto") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Descrizione") },
                placeholder = { Text("Cosa è successo, esito, prossimi passi") }, minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField("Data", Dates.short(date) + " " + date.take(4), Modifier.weight(1f)) { pickDate(ctx, date) { date = it } }
                PickerField("Ora", time, Modifier.weight(1f)) { pickTime(ctx, time) { time = it } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Crea follow-up", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = withFu, onCheckedChange = {
                    withFu = it
                    if (it && !fuTouched) { fuText = defaultFu(); fuDate = defaultFuDate() }
                })
            }
            if (withFu) {
                OutlinedTextField(
                    value = fuText, onValueChange = { fuText = it; fuTouched = true }, label = { Text("Cosa fare") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                PickerField("Scadenza", if (fuDate.isEmpty()) "" else Dates.long(fuDate), Modifier.fillMaxWidth()) {
                    pickDate(ctx, fuDate.ifEmpty { defaultFuDate() }) { fuDate = it }
                }
            }
        }
    }

    if (askIgnore && imp != null) {
        val what = when (imp.kind) { "call" -> "questo numero"; "whatsapp" -> "questa chat"; else -> "questo indirizzo" }
        AlertDialog(
            onDismissRequest = { askIgnore = false },
            title = { Text("Ignorare?") },
            text = { Text("Puoi ignorare solo questo elemento oppure non vedere più nulla da $what.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.setInboxStatus(imp.id, "ignored")
                    ui.editor = null
                    ui.toast("Ignorato")
                }) { Text("Solo questa volta") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val field = when (imp.kind) { "call" -> "ignoreNumbers"; "whatsapp" -> "ignoreChats"; else -> "ignoreAddresses" }
                    scope.launch { runCatching { Repo.addToList(field, imp.address) } }
                    Repo.setInboxStatus(imp.id, "ignored")
                    ui.editor = null
                    ui.toast("Non vedrai più nulla da $what")
                }) { Text("Sempre") }
            },
        )
    }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Eliminare l'attività?") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.deleteActivity(a0.id)
                    ui.editor = null
                    ui.toast("Attività eliminata", "Annulla") { Repo.restoreActivity(a0) }
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Annulla") } },
        )
    }
}

@Composable
fun PickerField(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(interactionSource = src, indication = null, onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Label(label)
            Text(value.ifBlank { "Scegli" }, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
