package sm.mar.diario.importers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import sm.mar.diario.data.Dates
import sm.mar.diario.data.Prefs
import sm.mar.diario.data.Repo
import sm.mar.diario.data.normPhone

/** Legge il registro chiamate del telefono e propone le telefonate di lavoro. */
object CallImporter {
    private val privateWords = listOf("mamma", "papà", "papa", "amore", "casa", "nonna", "nonno", "zia", "zio", "moglie", "marito")

    private data class Call(val number: String, val date: Long, val duration: Long, val type: Int, val name: String)

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    suspend fun run(ctx: Context): Int {
        if (!hasPermission(ctx) || Repo.uid == null) return 0
        val prefs = Prefs(ctx)
        val since = prefs.lastCall.takeIf { it > 0 } ?: Dates.startOfToday()
        val settings = Repo.getSettings()
        val ignored = settings.ignoreNumbers.map { normPhone(it) }.toSet()

        val calls = mutableListOf<Call>()
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME),
            "${CallLog.Calls.DATE} > ?", arrayOf(since.toString()), "${CallLog.Calls.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                calls += Call(
                    number = c.getString(0) ?: "",
                    date = c.getLong(1),
                    duration = c.getLong(2),
                    type = c.getInt(3),
                    name = c.getString(4) ?: "",
                )
            }
        }
        if (calls.isEmpty()) return 0

        var added = 0
        calls.forEachIndexed { i, call ->
            val dir = when (call.type) {
                CallLog.Calls.INCOMING_TYPE -> "in"
                CallLog.Calls.OUTGOING_TYPE -> "out"
                CallLog.Calls.MISSED_TYPE -> "missed"
                else -> return@forEachIndexed
            }
            val num = normPhone(call.number)
            if (num.isEmpty() || num in ignored) return@forEachIndexed
            if (dir != "missed" && call.duration < 10) return@forEachIndexed
            if (isPrivate(call.name)) return@forEachIndexed
            if (call.name.isBlank() && dir != "missed" && call.duration < 30) return@forEachIndexed
            // chiamata persa già richiamata dopo
            if (dir == "missed" && calls.drop(i + 1).any { it.type == CallLog.Calls.OUTGOING_TYPE && normPhone(it.number) == num }) return@forEachIndexed

            val info = lookupContact(ctx, call.number)
            val data = mapOf<String, Any>(
                "kind" to "call", "direction" to dir, "address" to num, "number" to num,
                "contact" to call.name.ifBlank { info.first },
                "company" to info.second, "email" to info.third,
                "duration" to call.duration,
                "date" to Dates.dateOf(call.date), "time" to Dates.timeOf(call.date), "ts" to call.date,
            )
            if (Repo.putInboxIfAbsent("call-${call.date}", data)) added++
            if (dir == "out") Repo.dismissMissed(num)
        }
        prefs.lastCall = calls.maxOf { it.date }
        return added
    }

    fun isPrivate(name: String): Boolean {
        if (name.isBlank()) return false
        val low = name.lowercase()
        if (privateWords.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(low) }) return true
        // nomi con cuori o emoji: in rubrica di solito sono contatti personali
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            val t = Character.getType(cp)
            if (cp >= 0x1F000 || t == Character.OTHER_SYMBOL.toInt() || cp in 0x2600..0x27BF) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** Restituisce (nome, azienda, email) dalla rubrica, se presenti. */
    fun lookupContact(ctx: Context, number: String): Triple<String, String, String> {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED || number.isBlank())
            return Triple("", "", "")
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            var id = ""
            var name = ""
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) { id = c.getString(0) ?: ""; name = c.getString(1) ?: "" }
            }
            if (id.isEmpty()) return@runCatching Triple("", "", "")
            var company = ""
            var email = ""
            ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?)",
                arrayOf(id, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val mt = c.getString(0)
                    val v = c.getString(1) ?: ""
                    if (mt == ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE && company.isEmpty()) company = v
                    if (mt == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE && email.isEmpty()) email = v
                }
            }
            Triple(name, company, email)
        }.getOrDefault(Triple("", "", ""))
    }
}
