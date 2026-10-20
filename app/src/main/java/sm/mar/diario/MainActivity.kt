package sm.mar.diario

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import sm.mar.diario.data.Prefs
import sm.mar.diario.ui.AppRoot
import sm.mar.diario.ui.DiarioTheme
import sm.mar.diario.ui.LoginScreen
import sm.mar.diario.ui.UiState

class MainActivity : AppCompatActivity() {
    private var unlocked by mutableStateOf(false)
    private var lastPause = 0L
    private val ui = UiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        if (!lockAvailable()) unlocked = true
        setContent {
            DiarioTheme {
                Surface(color = MaterialTheme.colorScheme.background) { Root() }
            }
        }
    }

    @Composable
    private fun Root() {
        var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
        DisposableEffect(Unit) {
            val l = FirebaseAuth.AuthStateListener { user = it.currentUser }
            FirebaseAuth.getInstance().addAuthStateListener(l)
            onDispose { FirebaseAuth.getInstance().removeAuthStateListener(l) }
        }
        when {
            user == null -> LoginScreen()
            !unlocked -> LockScreen()
            else -> AppRoot(ui)
        }
    }

    @Composable
    private fun LockScreen() {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Text("Diario MAR", style = MaterialTheme.typography.headlineSmall)
            Text("Sblocca con impronta, volto o PIN del telefono.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { authenticate() }) { Text("Sblocca") }
        }
    }

    private fun lockAvailable(): Boolean = Prefs(this).lockEnabled &&
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticate() {
        if (!lockAvailable()) { unlocked = true; return }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Diario MAR")
                .setSubtitle("Sblocca il diario")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }

    override fun onResume() {
        super.onResume()
        if (lockAvailable() && lastPause > 0 && System.currentTimeMillis() - lastPause > 5 * 60_000) unlocked = false
        if (!unlocked && FirebaseAuth.getInstance().currentUser != null) authenticate()
    }

    override fun onPause() {
        super.onPause()
        lastPause = System.currentTimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        i?.getStringExtra(EXTRA_TAB)?.let { ui.tab = it }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
