package sm.mar.diario.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import sm.mar.diario.data.Repo
import sm.mar.diario.importers.ContactLookup
import sm.mar.diario.importers.ContactMatcher
import sm.mar.diario.importers.Match
import sm.mar.diario.importers.DiaryPerson
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

private val fieldLabel = mapOf("phone" to "telefono", "email" to "email", "company" to "azienda", "contact" to "nome")

/** Scrive i dati mancanti in ogni attività della persona; restituisce quante attività sono cambiate. */
private fun apply(m: Match): Int {
    val add = m.additions
    var changed = 0
    for (a in m.person.activities) {
        val f = mutableMapOf<String, Any>()
        add["phone"]?.let { if (a.phone.isBlank()) f["phone"] = it }
        add["email"]?.let { if (a.email.isBlank()) f["email"] = it }
        add["company"]?.let { if (a.company.isBlank()) f["company"] = it }
        add["contact"]?.let { if (a.contact.isBlank()) f["contact"] = it }
        if (f.isNotEmpty()) { Repo.updateActivityFields(a.id, f); changed++ }
    }
    return changed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrichScreen(ui: UiState) {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var autoPeople by remember { mutableIntStateOf(0) }
    var autoActs by remember { mutableIntStateOf(0) }
    var confirmedActs by remember { mutableIntStateOf(0) }
    var checked by remember { mutableIntStateOf(0) }
    val pending = remember { mutableStateListOf<Match>() }
    val missing = remember { mutableStateListOf<DiaryPerson>() }
    var editing by remember { mutableStateOf<DiaryPerson?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refreshMissing() {
        val people = runCatching { ContactMatcher.people(Repo.allActivities()) }.getOrDefault(emptyList())
        val pendingKeys = pending.map { it.person.key }.toSet()
        missing.clear()
        missing += people.filter { it.key !in pendingKeys }.sortedBy { it.name.ifBlank { it.email }.lowercase() }
    }

    LaunchedEffect(Unit) {
        if (!ContactLookup.allowed(ctx)) {
            error = "Per gli abbinamenti automatici serve l'accesso alla rubrica: attivalo in Impostazioni → Accesso ai dati del telefono."
            refreshMissing()
            loading = false
            return@LaunchedEffect
        }
        runCatching {
            val acts = Repo.allActivities()
            val book = ContactMatcher.loadBook(ctx)
            val people = ContactMatcher.people(acts)
            checked = people.size
            val matches = ContactMatcher.match(people, book)
            for (m in matches) {
                if (m.exact) {
                    val n = apply(m)
                    if (n > 0) { autoPeople++; autoActs += n }
                } else pending += m
            }
        }.onFailure { error = "Non sono riuscito a leggere i dati: ${it.message ?: "errore sconosciuto"}." }
        refreshMissing()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Completa anagrafiche") },
                navigationIcon = { IconButton(onClick = { ui.enrich = false }) { Icon(Icons.Filled.Close, "Chiudi") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                        Text("Confronto il diario con la rubrica…")
                    }
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    else -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("$checked persone con dati mancanti", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (autoPeople > 0) "Completate in automatico: $autoPeople persone, $autoActs attività (corrispondenza esatta di email, numero o nome)."
                                else "Nessuna corrispondenza esatta da completare in automatico.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (confirmedActs > 0) Text("Confermate da te: $confirmedActs attività aggiornate.", style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.ok)
                            Text(
                                if (pending.isEmpty()) "Nessun abbinamento da confermare." else "${pending.size} abbinamenti simili da confermare qui sotto.",
                                style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.muted,
                            )
                        }
                    }
                }
            }
            items(pending, key = { it.person.key }) { m ->
                ProposalCard(
                    m,
                    onConfirm = { confirmedActs += apply(m); pending.remove(m); scope.launch { refreshMissing() } },
                    onReject = { pending.remove(m); scope.launch { refreshMissing() } },
                )
            }
            if (!loading && missing.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 12.dp)) {
                        Text("Dati ancora mancanti", style = MaterialTheme.typography.titleLarge)
                        Text("${missing.size} persone. Tocca una persona per completarla a mano.", style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
                    }
                }
                items(missing, key = { "m-" + it.key }) { p -> MissingRow(p) { editing = p } }
            }
            if (!loading && error == null && pending.isEmpty()) {
                item {
                    Button(onClick = { ui.enrich = false }, modifier = Modifier.fillMaxWidth()) { Text("Fatto") }
                }
            }
        }
    }
    editing?.let { p ->
        ManualDialog(p, onClose = { editing = null }) { name, email, phone, company ->
            var n = 0
            for (a in p.activities) {
                val f = mutableMapOf<String, Any>()
                fun put(key: String, cur: String, old: String, new: String) {
                    if (new.isNotBlank() && cur != new && (cur.isBlank() || cur == old)) f[key] = new
                }
                put("contact", a.contact, p.name, name)
                put("email", a.email, p.email, email)
                put("phone", a.phone, p.phone, phone)
                put("company", a.company, p.company, company)
                if (f.isNotEmpty()) { Repo.updateActivityFields(a.id, f); n++ }
            }
            editing = null
            ui.toast(if (n > 0) "Aggiornate $n attività" else "Nessuna modifica")
            scope.launch { refreshMissing() }
        }
    }
}

@Composable
private fun ManualDialog(p: DiaryPerson, onClose: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var email by remember { mutableStateOf(p.email) }
    var phone by remember { mutableStateOf(p.phone) }
    var company by remember { mutableStateOf(p.company) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Completa anagrafica") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vale per tutte le ${p.activities.size} attività di questa persona.", style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
                OutlinedTextField(name, { name = it }, label = { Text("Nome e cognome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(company, { company = it }, label = { Text("Azienda") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Telefono") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), email.trim(), phone.trim(), company.trim()) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Annulla") } },
    )
}

@Composable
private fun ProposalCard(m: Match, onConfirm: () -> Unit, onReject: () -> Unit) {
    val p = LocalPalette.current
    val c = m.contact
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Nel diario", modifier = Modifier.weight(1f))
                Pill("somiglianza ${(m.score * 100).toInt()}%", if (m.score >= 0.85) p.ok else p.warn)
            }
            Text(m.person.name.ifBlank { m.person.email }, style = MaterialTheme.typography.titleMedium)
            val known = listOf(m.person.email, m.person.phone, m.person.company).filter { it.isNotBlank() }
            if (known.isNotEmpty()) Mono(known.joinToString(" · "))
            Text("${m.person.activities.size} attività", style = MaterialTheme.typography.bodySmall, color = p.muted)

            HorizontalDivider(color = p.line, modifier = Modifier.padding(vertical = 4.dp))

            Label("In rubrica · ${m.reason}")
            Text(c.name, style = MaterialTheme.typography.titleMedium)
            val details = listOf(c.company, c.bestPhone, c.emails.firstOrNull().orEmpty()).filter { it.isNotBlank() }
            if (details.isNotEmpty()) Mono(details.joinToString(" · "))

            val add = m.additions
            Text(
                "Da aggiungere: " + add.entries.joinToString(", ") { "${fieldLabel[it.key]} ${it.value}" },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Button(onClick = onConfirm) { Text("Conferma") }
                OutlinedButton(onClick = onReject) { Text("Non è lui") }
            }
        }
    }
}

@Composable
private fun MissingRow(p: DiaryPerson, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val lacks = buildList {
        if (p.phone.isBlank()) add("telefono")
        if (p.email.isBlank()) add("email")
        if (p.company.isBlank()) add("azienda")
    }
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name.ifBlank { p.email.ifBlank { p.phone } }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Mono("${p.activities.size} att.")
            }
            val known = listOf(p.company, p.email, p.phone).filter { it.isNotBlank() }
            if (known.isNotEmpty()) Mono(known.joinToString(" · "))
            Text("Manca: " + lacks.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = pal.warn)
        }
    }
}
