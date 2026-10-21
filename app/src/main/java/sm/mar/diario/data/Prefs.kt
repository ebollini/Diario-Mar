package sm.mar.diario.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Impostazioni locali del telefono. La password email è cifrata e non lascia mai il telefono. */
class Prefs(ctx: Context) {
    private val plain: SharedPreferences = ctx.getSharedPreferences("diario", Context.MODE_PRIVATE)
    private val secure: SharedPreferences = runCatching {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "diario_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { ctx.getSharedPreferences("diario_secure_fallback", Context.MODE_PRIVATE) }

    var imapHost: String
        get() = plain.getString("imapHost", "email.mar.sm") ?: "email.mar.sm"
        set(v) = plain.edit().putString("imapHost", v.trim()).apply()
    var imapPort: Int
        get() = plain.getInt("imapPort", 993)
        set(v) = plain.edit().putInt("imapPort", v).apply()
    var imapUser: String
        get() = plain.getString("imapUser", "e.bollini@mar.sm") ?: ""
        set(v) = plain.edit().putString("imapUser", v.trim()).apply()
    var imapPassword: String
        get() = secure.getString("imapPassword", "") ?: ""
        set(v) = secure.edit().putString("imapPassword", v).apply()
    var inboxFolder: String
        get() = plain.getString("inboxFolder", "INBOX") ?: "INBOX"
        set(v) = plain.edit().putString("inboxFolder", v.trim()).apply()
    var sentFolder: String
        get() = plain.getString("sentFolder", "Sent Items") ?: "Sent Items"
        set(v) = plain.edit().putString("sentFolder", v.trim()).apply()

    var lastMailIn: Long
        get() = plain.getLong("lastMailIn", 0)
        set(v) = plain.edit().putLong("lastMailIn", v).apply()
    var lastMailOut: Long
        get() = plain.getLong("lastMailOut", 0)
        set(v) = plain.edit().putLong("lastMailOut", v).apply()
    var lastCall: Long
        get() = plain.getLong("lastCall", 0)
        set(v) = plain.edit().putLong("lastCall", v).apply()
    var lastReminder: Long
        get() = plain.getLong("lastReminder", 0)
        set(v) = plain.edit().putLong("lastReminder", v).apply()
    var lastMailError: String
        get() = plain.getString("lastMailError", "") ?: ""
        set(v) = plain.edit().putString("lastMailError", v).apply()

    var aiEnabled: Boolean
        get() = plain.getBoolean("ai", true)
        set(v) = plain.edit().putBoolean("ai", v).apply()
    var lastAiError: String
        get() = plain.getString("lastAiError", "") ?: ""
        set(v) = plain.edit().putString("lastAiError", v).apply()

    var lockEnabled: Boolean
        get() = plain.getBoolean("lock", true)
        set(v) = plain.edit().putBoolean("lock", v).apply()

    fun waDoc(chatKey: String): String? = plain.getString("wa_$chatKey", null)
    fun setWaDoc(chatKey: String, id: String) = plain.edit().putString("wa_$chatKey", id).apply()

    val mailConfigured get() = imapHost.isNotBlank() && imapUser.isNotBlank() && imapPassword.isNotBlank()
}
