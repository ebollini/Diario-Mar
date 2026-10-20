package sm.mar.diario.importers

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sm.mar.diario.data.Dates
import sm.mar.diario.data.Prefs
import sm.mar.diario.data.Repo

/**
 * Legge le notifiche dei messaggi WhatsApp in arrivo (WhatsApp non permette altro).
 * Raggruppa i messaggi in una voce per chat e per giorno.
 */
class WhatsAppListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val packages = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val noise = Regex("^(\\d+ (nuovi )?messaggi.*|\\d+ messages.*|controllo (dei )?nuovi messaggi.*|checking for new messages.*|backup.*|whatsapp web.*|chiamata.*|videochiamata.*|missed (voice|video) call.*|chiamata (vocale|video) persa.*)$", RegexOption.IGNORE_CASE)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in packages) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (Repo.uid == null) return

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val chat = (style?.conversationTitle?.toString() ?: title)
            .replace(Regex("\\s*\\(\\d+ messaggi?\\)$"), "").trim()
        if (chat.isEmpty() || chat.equals("WhatsApp", true) || chat.equals("WhatsApp Business", true)) return
        val isGroup = style?.isGroupConversation == true

        val msgs: List<Triple<String, String, Long>> = if (style != null && style.messages.isNotEmpty()) {
            style.messages.mapNotNull { m ->
                val text = m.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) null else Triple(m.person?.name?.toString() ?: chat, text, m.timestamp)
            }
        } else {
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (text.isEmpty()) emptyList() else listOf(Triple(chat, text, sbn.postTime))
        }.filterNot { noise.matches(it.second) }
        if (msgs.isEmpty()) return

        scope.launch { save(applicationContext, chat, isGroup, msgs) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, WhatsAppListener::class.java).flattenToString()
            return flat.split(':').any { it == me }
        }

        private fun keyOf(chat: String) = chat.lowercase().filter { it.isLetterOrDigit() }.take(40).ifEmpty { chat.hashCode().toString() }

        suspend fun save(ctx: Context, chat: String, isGroup: Boolean, msgs: List<Triple<String, String, Long>>) {
            val settings = Repo.getSettings()
            if (settings.ignoreChats.any { it.equals(chat, true) }) return
            if (CallImporter.isPrivate(chat)) return
            val prefs = Prefs(ctx)
            val today = Dates.today()
            val base = "wa-$today-${keyOf(chat)}"
            // se la voce di oggi è già stata registrata o ignorata, i nuovi messaggi aprono una voce nuova
            var id = prefs.waDoc(base) ?: base
            val st = Repo.inboxStatus(id)
            if (st != null && st != "new") {
                id = "$base-${System.currentTimeMillis()}"
            }
            prefs.setWaDoc(base, id)
            val last = msgs.maxOf { it.third }
            val first = msgs.minOf { it.third }
            val baseData = mapOf<String, Any>(
                "kind" to "whatsapp", "direction" to "in", "address" to chat, "contact" to chat,
                "group" to isGroup, "subject" to if (isGroup) "Gruppo WhatsApp" else "",
                "date" to today, "time" to Dates.timeOf(first), "ts" to first, "lastTs" to last,
                "snippet" to msgs.last().second.take(200),
            )
            val list = msgs.map { mapOf<String, Any>("from" to it.first, "text" to it.second.take(1000), "ts" to it.third) }
            runCatching { Repo.appendWhatsApp(id, baseData, list) }
        }
    }
}
