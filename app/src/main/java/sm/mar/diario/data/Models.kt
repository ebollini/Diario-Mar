package sm.mar.diario.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tipi di attività: le chiavi sono condivise con il diario per PC. */
enum class ActType(val key: String, val label: String) {
    CALL("call", "Telefonata"),
    EMAIL_OUT("email_out", "Email inviata"),
    EMAIL_IN("email_in", "Email ricevuta"),
    WHATSAPP("whatsapp", "WhatsApp"),
    MEETING("meeting", "Incontro"),
    NOTE("note", "Nota");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: NOTE
    }
}

data class Activity(
    val id: String = "",
    val type: String = "call",
    val contact: String = "",
    val email: String = "",
    val phone: String = "",
    val company: String = "",
    val subject: String = "",
    val notes: String = "",
    val date: String = "",
    val time: String = "",
    val ts: Long = 0,
    val source: String = "manual",
    val importId: String = "",
    val createdAt: Long = 0,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "type" to type, "contact" to contact, "email" to email, "phone" to phone,
        "company" to company, "subject" to subject, "notes" to notes,
        "date" to date, "time" to time, "ts" to ts, "source" to source,
        "importId" to importId, "createdAt" to createdAt, "updatedAt" to System.currentTimeMillis(),
    )

    companion object {
        fun from(id: String, m: Map<String, Any?>) = Activity(
            id = id,
            type = m.str("type").ifEmpty { "note" },
            contact = m.str("contact"), email = m.str("email"), phone = m.str("phone"),
            company = m.str("company"), subject = m.str("subject"), notes = m.str("notes"),
            date = m.str("date"), time = m.str("time"), ts = m.long("ts"),
            source = m.str("source"), importId = m.str("importId"), createdAt = m.long("createdAt"),
        )
    }
}

data class FollowUp(
    val id: String = "",
    val title: String = "",
    val contact: String = "",
    val company: String = "",
    val due: String = "",
    val done: Boolean = false,
    val activityId: String = "",
) {
    companion object {
        fun from(id: String, m: Map<String, Any?>) = FollowUp(
            id = id, title = m.str("title"), contact = m.str("contact"), company = m.str("company"),
            due = m.str("due"), done = m["done"] == true, activityId = m.str("activityId"),
        )
    }
}

data class WaMessage(val from: String, val text: String, val ts: Long)

/** Elemento da registrare: telefonata, email o chat WhatsApp. */
data class InboxItem(
    val id: String = "",
    val kind: String = "call", // call | mail | whatsapp
    val status: String = "new",
    val direction: String = "in", // in | out | missed
    val contact: String = "",
    val company: String = "",
    val address: String = "", // numero, email o nome chat
    val email: String = "",
    val subject: String = "",
    val snippet: String = "",
    val duration: Long = 0,
    val date: String = "",
    val time: String = "",
    val ts: Long = 0,
    val messages: List<WaMessage> = emptyList(),
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(id: String, m: Map<String, Any?>): InboxItem {
            val msgs = (m["messages"] as? List<Map<String, Any?>>).orEmpty()
                .map { WaMessage(it.str("from"), it.str("text"), it.long("ts")) }
                .sortedBy { it.ts }
            return InboxItem(
                id = id, kind = m.str("kind").ifEmpty { "mail" }, status = m.str("status"),
                direction = m.str("direction").ifEmpty { "in" }, contact = m.str("contact"),
                company = m.str("company"),
                address = m.str("address").ifEmpty { m.str("number").ifEmpty { if (m.str("direction") == "out") m.str("to") else m.str("from") } },
                email = m.str("email"), subject = m.str("subject"), snippet = m.str("snippet"),
                duration = m.long("duration"), date = m.str("date"), time = m.str("time"), ts = m.long("ts"),
                messages = msgs,
            )
        }
    }
}

data class Settings(
    val checkinHours: Int = 3,
    val lastCheckin: Long = 0,
    val ignoreNumbers: List<String> = emptyList(),
    val ignoreChats: List<String> = emptyList(),
    val ignoreAddresses: List<String> = emptyList(),
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(m: Map<String, Any?>?) = if (m == null) Settings() else Settings(
            checkinHours = m.long("checkinHours").toInt().takeIf { it in 1..8 } ?: 3,
            lastCheckin = m.long("lastCheckin"),
            ignoreNumbers = (m["ignoreNumbers"] as? List<String>).orEmpty(),
            ignoreChats = (m["ignoreChats"] as? List<String>).orEmpty(),
            ignoreAddresses = (m["ignoreAddresses"] as? List<String>).orEmpty(),
        )
    }
}

fun Map<String, Any?>.str(k: String): String = (this[k] as? String) ?: (this[k]?.toString() ?: "")
fun Map<String, Any?>.long(k: String): Long = when (val v = this[k]) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull() ?: 0
    else -> 0
}

object Dates {
    val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val loc = Locale.ITALIAN
    private val dayLong = DateTimeFormatter.ofPattern("EEEE d MMMM", loc)
    private val dayShort = DateTimeFormatter.ofPattern("d MMM", loc)
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    fun today(): String = LocalDate.now(zone).toString()
    fun now(): String = LocalTime.now(zone).format(hm)
    fun dateOf(ms: Long): String = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), zone).toLocalDate().toString()
    fun timeOf(ms: Long): String = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), zone).format(hm)
    fun tsOf(date: String, time: String): Long = runCatching {
        LocalDate.parse(date).atTime(LocalTime.parse(time.ifEmpty { "09:00" })).atZone(zone).toInstant().toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())

    fun long(d: String): String = runCatching { LocalDate.parse(d).format(dayLong).replaceFirstChar { c -> c.uppercase() } }.getOrDefault(d)
    fun short(d: String): String = runCatching { LocalDate.parse(d).format(dayShort) }.getOrDefault(d)
    fun addDays(d: String, n: Long): String = LocalDate.parse(d).plusDays(n).toString()
    fun addWorkDays(d: String, n: Int): String {
        var x = LocalDate.parse(d)
        var left = n
        while (left > 0) {
            x = x.plusDays(1)
            if (x.dayOfWeek.value < 6) left--
        }
        return x.toString()
    }
    fun startOfToday(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
}

fun fmtDuration(s: Long): String = if (s < 60) "$s s" else {
    val m = s / 60
    val r = s % 60
    if (r == 0L) "$m min" else "$m min $r s"
}

fun normPhone(p: String): String {
    var x = p.filter { it.isDigit() || it == '+' }
    if (x.startsWith("00")) x = "+" + x.drop(2)
    if (!x.startsWith("+") && x.length in 9..11 && x.startsWith("3")) x = "+39$x"
    return x
}
