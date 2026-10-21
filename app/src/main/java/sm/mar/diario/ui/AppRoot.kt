package sm.mar.diario.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sm.mar.diario.data.Activity
import sm.mar.diario.data.Dates
import sm.mar.diario.data.InboxItem
import sm.mar.diario.data.Repo

/** Cosa apre il modulo "Registra attività". */
data class EditorSeed(
    val activity: Activity,
    val import: InboxItem? = null,
    val isEdit: Boolean = false,
)

class UiState {
    var tab by mutableStateOf("diary")
    var editor by mutableStateOf<EditorSeed?>(null)
    var enrich by mutableStateOf(false)
    var day by mutableStateOf(Dates.today())
    val snackbar = SnackbarHostState()
    var scope: CoroutineScope? = null

    fun toast(msg: String, undoLabel: String? = null, onUndo: (() -> Unit)? = null) {
        scope?.launch {
            val r = snackbar.showSnackbar(msg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onUndo?.invoke()
        }
    }

    fun newActivity() {
        editor = EditorSeed(Activity(type = "call", date = day, time = if (day == Dates.today()) Dates.now() else "09:00"))
    }
}

@Composable
fun AppRoot(ui: UiState) {
    ui.scope = rememberCoroutineScope()
    val inbox by remember { Repo.pendingInbox() }.collectAsState(initial = emptyList())
    val fus by remember { Repo.openFollowUps() }.collectAsState(initial = emptyList())
    val recent by remember { Repo.recent() }.collectAsState(initial = emptyList())
    val settings by remember { Repo.settings() }.collectAsState(initial = sm.mar.diario.data.Settings())
    val today = Dates.today()
    val dueFus = fus.count { it.due <= today }

    if (ui.enrich) {
        BackHandler { ui.enrich = false }
        EnrichScreen(ui)
        return
    }
    val seed = ui.editor
    if (seed != null) {
        BackHandler { ui.editor = null }
        EditorScreen(ui, seed, recent)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(ui.snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = ui.tab == "diary", onClick = { ui.tab = "diary" },
                    icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text("Diario") },
                )
                NavigationBarItem(
                    selected = ui.tab == "inbox", onClick = { ui.tab = "inbox" },
                    icon = {
                        BadgedBox(badge = { if (inbox.isNotEmpty()) Badge { Text(inbox.size.toString()) } }) {
                            Icon(Icons.Filled.Notifications, null)
                        }
                    },
                    label = { Text("Da registrare") },
                )
                NavigationBarItem(
                    selected = ui.tab == "fu", onClick = { ui.tab = "fu" },
                    icon = {
                        BadgedBox(badge = { if (dueFus > 0) Badge { Text(dueFus.toString()) } }) {
                            Icon(Icons.Filled.CheckCircle, null)
                        }
                    },
                    label = { Text("Follow-up") },
                )
                NavigationBarItem(
                    selected = ui.tab == "settings", onClick = { ui.tab = "settings" },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Impostazioni") },
                )
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (ui.tab) {
            "inbox" -> InboxScreen(ui, inbox, settings, m)
            "fu" -> FollowUpScreen(ui, fus, m)
            "settings" -> SettingsScreen(ui, settings, m)
            else -> DiaryScreen(ui, recent, fus, inbox, m)
        }
    }
}
