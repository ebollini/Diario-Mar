package sm.mar.diario.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sm.mar.diario.BuildConfigInfo
import sm.mar.diario.data.Prefs
import sm.mar.diario.data.Repo
import sm.mar.diario.data.Settings
import sm.mar.diario.importers.MailImporter
import sm.mar.diario.importers.WhatsAppListener
import sm.mar.diario.work.SyncWorker

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun SettingsScreen(ui: UiState, settings: Settings, modifier: Modifier) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }

    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        SyncWorker.runNow(ctx)
    }

    var host by remember { mutableStateOf(prefs.imapHost) }
    var port by remember { mutableStateOf(prefs.imapPort.toString()) }
    var user by remember { mutableStateOf(prefs.imapUser) }
    var pass by remember { mutableStateOf(prefs.imapPassword) }
    var inF by remember { mutableStateOf(prefs.inboxFolder) }
    var outF by remember { mutableStateOf(prefs.sentFolder) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var lock by remember { mutableStateOf(prefs.lockEnabled) }
    var ai by remember { mutableStateOf(prefs.aiEnabled) }
    var newAddr by remember { mutableStateOf("") }

    fun saveMail() {
        prefs.imapHost = host; prefs.imapPort = port.toIntOrNull() ?: 993; prefs.imapUser = user
        prefs.imapPassword = pass; prefs.inboxFolder = inF; prefs.sentFolder = outF
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Label("Diario MAR")
            Text("Impostazioni", style = MaterialTheme.typography.headlineSmall)
        }

        // forza la rilettura dei permessi dopo il ritorno dalle impostazioni di sistema
        LaunchedEffect(refresh) { }
        Section("Accesso ai dati del telefono") {
            PermRow("Registro chiamate", "Importa telefonate in entrata, in uscita e perse.", granted(Manifest.permission.READ_CALL_LOG)) {
                permLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS))
            }
            PermRow("Rubrica", "Nomi, aziende ed email dei contatti.", granted(Manifest.permission.READ_CONTACTS)) {
                permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            }
            if (Build.VERSION.SDK_INT >= 33) {
                PermRow("Notifiche", "Promemoria \"Fai il punto\".", granted(Manifest.permission.POST_NOTIFICATIONS)) {
                    permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
            PermRow("Messaggi WhatsApp", "Legge le notifiche dei messaggi ricevuti. I messaggi inviati non sono leggibili.", WhatsAppListener.isEnabled(ctx)) {
                ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            TextButton(onClick = { refresh++ }) { Text("Aggiorna stato") }
        }

        Section("Email di lavoro") {
            Text("Il telefono si collega direttamente al server di posta. La password resta cifrata sul telefono.", style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
            OutlinedTextField(host, { host = it }, label = { Text("Server IMAP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(user, { user = it }, label = { Text("Utente") }, singleLine = true, modifier = Modifier.weight(2f))
                OutlinedTextField(port, { port = it.filter { c -> c.isDigit() } }, label = { Text("Porta") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            OutlinedTextField(pass, { pass = it }, label = { Text("Password email") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(inF, { inF = it }, label = { Text("Cartella arrivo") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(outF, { outF = it }, label = { Text("Cartella inviata") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(enabled = !testing, onClick = {
                    saveMail(); testing = true; testMsg = null
                    scope.launch {
                        val (folders, e) = withContext(Dispatchers.IO) { MailImporter.test(prefs) }
                        testMsg = e ?: "Connessione riuscita. ${folders.size} cartelle trovate${if (folders.none { it.equals(outF, true) }) ": la cartella \"$outF\" non c'è, controlla il nome (${folders.take(8).joinToString(", ")})" else "."}"
                        testing = false
                        if (e == null) SyncWorker.runNow(ctx)
                    }
                }) { Text(if (testing) "Provo…" else "Salva e prova") }
            }
            testMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (prefs.lastMailError.isNotBlank() && testMsg == null) Text("Ultimo errore: ${prefs.lastMailError}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Section("Riassunti delle email") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scrivi nella descrizione un riassunto breve dell'email, fatto con Gemini", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(ai, { ai = it; prefs.aiEnabled = it })
            }
            if (prefs.lastAiError.isNotBlank()) Text(prefs.lastAiError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Section("Promemoria \"Fai il punto\"") {
            Text("Notifica dal lunedì al venerdì, dalle 8 alle 20.", style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4).forEach { h ->
                    FilterChip(selected = settings.checkinHours == h, onClick = {
                        scope.launch { runCatching { Repo.updateSettings(mapOf("checkinHours" to h)) } }
                    }, label = { Text("ogni $h ore") })
                }
            }
        }

        Section("Blocco app") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chiedi impronta o PIN del telefono all'apertura", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(lock, { lock = it; prefs.lockEnabled = it })
            }
        }

        Section("Anagrafiche") {
            Text("Completa numeri, email e aziende mancanti nelle attività già registrate usando la rubrica del telefono. Gli abbinamenti non certi ti vengono proposti da confermare.", style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
            FilledTonalButton(onClick = { ui.enrich = true }) { Text("Completa anagrafiche") }
        }

        Section("Esclusi dall'importazione") {
            ExcludedList("Numeri", settings.ignoreNumbers) { scope.launch { runCatching { Repo.removeFromList("ignoreNumbers", it) } } }
            ExcludedList("Chat WhatsApp", settings.ignoreChats) { scope.launch { runCatching { Repo.removeFromList("ignoreChats", it) } } }
            ExcludedList("Indirizzi email", settings.ignoreAddresses) { scope.launch { runCatching { Repo.removeFromList("ignoreAddresses", it) } } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newAddr, { newAddr = it }, label = { Text("Escludi indirizzo email") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedButton(enabled = newAddr.contains("@"), onClick = {
                    val a = newAddr.trim().lowercase(); newAddr = ""
                    scope.launch { runCatching { Repo.addToList("ignoreAddresses", a) } }
                }) { Text("Aggiungi") }
            }
        }

        Section("Account") {
            Text(Repo.auth.currentUser?.email ?: "", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { Repo.auth.signOut() }) { Text("Esci") }
            Mono("Versione ${BuildConfigInfo.version(ctx)}")
        }
    }
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean, onEnable: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
        }
        if (ok) Pill("attivo", LocalPalette.current.ok) else FilledTonalButton(onClick = onEnable) { Text("Attiva") }
    }
}

@Composable
private fun ExcludedList(title: String, values: List<String>, onRemove: (String) -> Unit) {
    if (values.isEmpty()) return
    Label(title)
    values.forEach { v ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono(v, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = { onRemove(v) }) { Icon(Icons.Filled.Close, "Rimuovi") }
        }
    }
}
