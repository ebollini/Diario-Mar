package sm.mar.diario.importers

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sm.mar.diario.data.Activity
import sm.mar.diario.data.normPhone
import java.text.Normalizer

/** Un contatto della rubrica con tutti i suoi recapiti. */
data class BookContact(
    val id: String,
    val name: String,
    val phones: List<String>,
    val mobile: String,
    val emails: List<String>,
    val company: String,
) {
    val bestPhone get() = mobile.ifEmpty { phones.firstOrNull().orEmpty() }
}

/** Una persona del diario (tutte le attività con lo stesso nome o recapito). */
data class DiaryPerson(
    val key: String,
    val name: String,
    val email: String,
    val phone: String,
    val company: String,
    val activities: List<Activity>,
)

/** Proposta di abbinamento tra una persona del diario e un contatto della rubrica. */
data class Match(
    val person: DiaryPerson,
    val contact: BookContact,
    val score: Double,
    val exact: Boolean,
    val reason: String,
) {
    /** Campi che verrebbero aggiunti (solo quelli mancanti). */
    val additions: Map<String, String>
        get() = buildMap {
            if (person.phone.isBlank() && contact.bestPhone.isNotBlank()) put("phone", contact.bestPhone)
            if (person.email.isBlank() && contact.emails.isNotEmpty()) put("email", contact.emails.first())
            if (person.company.isBlank() && contact.company.isNotBlank()) put("company", contact.company)
            if (person.name.isBlank() && contact.name.isNotBlank()) put("contact", contact.name)
        }
}

object ContactMatcher {

    /** Legge tutta la rubrica in una sola passata. */
    suspend fun loadBook(ctx: Context): List<BookContact> = withContext(Dispatchers.IO) {
        if (!ContactLookup.allowed(ctx)) return@withContext emptyList()
        data class Acc(var name: String = "", val phones: MutableList<String> = mutableListOf(), var mobile: String = "",
                       val emails: MutableList<String> = mutableListOf(), var company: String = "")
        val map = LinkedHashMap<String, Acc>()
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2),
                "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)",
                arrayOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val a = map.getOrPut(id) { Acc() }
                    if (a.name.isEmpty()) a.name = c.getString(1) ?: ""
                    val v = c.getString(3)?.trim().orEmpty()
                    if (v.isEmpty()) continue
                    when (c.getString(2)) {
                        Phone.CONTENT_ITEM_TYPE -> {
                            if (a.phones.none { normPhone(it) == normPhone(v) }) a.phones += v
                            if (c.getInt(4) == Phone.TYPE_MOBILE && a.mobile.isEmpty()) a.mobile = v
                        }
                        Email.CONTENT_ITEM_TYPE -> if (a.emails.none { it.equals(v, true) }) a.emails += v.lowercase()
                        Organization.CONTENT_ITEM_TYPE -> if (a.company.isEmpty()) a.company = v
                    }
                }
            }
        }
        map.map { (id, a) -> BookContact(id, a.name, a.phones, a.mobile, a.emails, a.company) }
            .filter { it.name.isNotBlank() }
    }

    /** Raggruppa le attività per persona e tiene solo quelle a cui manca qualcosa. */
    fun people(acts: List<Activity>): List<DiaryPerson> {
        val groups = LinkedHashMap<String, MutableList<Activity>>()
        for (a in acts) {
            if (a.contact.isBlank() && a.email.isBlank() && a.phone.isBlank()) continue
            val key = when {
                a.email.isNotBlank() -> "e:" + a.email.lowercase().trim()
                a.phone.isNotBlank() -> "p:" + normPhone(a.phone)
                else -> "n:" + norm(a.contact)
            }
            groups.getOrPut(key) { mutableListOf() } += a
        }
        // unisce i gruppi per nome (stessa persona registrata una volta con email e una con telefono)
        val byName = LinkedHashMap<String, MutableList<Activity>>()
        for ((k, list) in groups) {
            val nameKey = list.firstOrNull { it.contact.isNotBlank() }?.let { "n:" + norm(it.contact) } ?: k
            byName.getOrPut(nameKey) { mutableListOf() } += list
        }
        return byName.map { (k, list) ->
            DiaryPerson(
                key = k,
                name = list.firstNotNullOfOrNull { it.contact.takeIf { s -> s.isNotBlank() } }.orEmpty(),
                email = list.firstNotNullOfOrNull { it.email.takeIf { s -> s.isNotBlank() } }.orEmpty(),
                phone = list.firstNotNullOfOrNull { it.phone.takeIf { s -> s.isNotBlank() } }.orEmpty(),
                company = list.firstNotNullOfOrNull { it.company.takeIf { s -> s.isNotBlank() } }.orEmpty(),
                activities = list,
            )
        }.filter { it.phone.isBlank() || it.email.isBlank() || it.company.isBlank() }
    }

    /** Trova per ogni persona il contatto più somigliante. */
    fun match(people: List<DiaryPerson>, book: List<BookContact>): List<Match> {
        val out = mutableListOf<Match>()
        for (p in people) {
            var best: Match? = null
            for (c in book) {
                val m = score(p, c) ?: continue
                if (best == null || m.score > best.score) best = m
            }
            val b = best ?: continue
            if (b.additions.isEmpty()) continue
            if (b.exact || b.score >= 0.72) out += b
        }
        return out.sortedWith(compareByDescending<Match> { it.exact }.thenByDescending { it.score })
    }

    private fun score(p: DiaryPerson, c: BookContact): Match? {
        if (p.email.isNotBlank() && c.emails.any { it.equals(p.email.trim(), true) })
            return Match(p, c, 1.0, true, "stessa email")
        if (p.phone.isNotBlank() && c.phones.any { normPhone(it) == normPhone(p.phone) })
            return Match(p, c, 1.0, true, "stesso numero")
        val pn = tokens(p.name)
        val cn = tokens(c.name)
        if (pn.isNotEmpty() && cn.isNotEmpty() && pn.sorted() == cn.sorted())
            return Match(p, c, 1.0, true, "stesso nome")

        var s = 0.0
        var reason = ""
        if (pn.isNotEmpty() && cn.isNotEmpty()) {
            s = nameSimilarity(pn, cn)
            reason = "nome simile"
        }
        // email del diario somigliante al nome in rubrica (m.rossi@ ↔ Mario Rossi)
        if (p.email.isNotBlank() && cn.isNotEmpty()) {
            val local = tokens(p.email.substringBefore("@").replace('.', ' ').replace('_', ' ').replace('-', ' '))
            val e = emailNameSimilarity(local, cn)
            if (e > s) { s = e; reason = "email simile al nome" }
            // stesso dominio aziendale di una email in rubrica
            val dom = p.email.substringAfter("@", "").lowercase()
            if (dom.isNotEmpty() && dom !in freeDomains && c.emails.any { it.substringAfter("@") == dom } && s > 0.5) {
                s = minOf(0.99, s + 0.15); reason += ", stessa azienda"
            }
        }
        // azienda uguale aiuta
        if (p.company.isNotBlank() && c.company.isNotBlank() && norm(p.company) == norm(c.company) && s > 0.5) {
            s = minOf(0.99, s + 0.1); reason += ", stessa azienda"
        }
        return if (s > 0) Match(p, c, s, false, reason) else null
    }

    private val freeDomains = setOf("gmail.com", "libero.it", "hotmail.com", "hotmail.it", "outlook.com", "outlook.it", "yahoo.com", "yahoo.it", "icloud.com", "tin.it", "alice.it", "virgilio.it", "live.com", "live.it")

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private val stop = setOf("ing", "dott", "dr", "sig", "sig.ra", "geom", "avv", "arch", "srl", "spa", "snc", "sas", "mar", "di", "de", "del", "la")

    fun tokens(s: String): List<String> = norm(s).split(' ').filter { it.length >= 2 && it !in stop }

    /** Ogni parola del nome più corto deve trovare una parola simile nell'altro. */
    private fun nameSimilarity(a: List<String>, b: List<String>): Double {
        val (short, long) = if (a.size <= b.size) a to b else b to a
        val sims = short.map { t -> long.maxOf { u -> wordSim(t, u) } }
        val avg = sims.average()
        // penalità leggera se mancano parole (es. solo cognome)
        val coverage = short.size.toDouble() / long.size
        return avg * (0.85 + 0.15 * coverage)
    }

    private fun emailNameSimilarity(local: List<String>, name: List<String>): Double {
        if (local.isEmpty()) return 0.0
        // m rossi / mrossi / mario rossi
        val joined = local.joinToString("")
        var best = 0.0
        for (surname in name) {
            if (surname.length < 3) continue
            val initials = name.filter { it != surname }.map { it.first() }
            val variants = buildList {
                add(surname)
                initials.forEach { i -> add("$i$surname"); add("$surname$i") }
                name.filter { it != surname }.forEach { n -> add("$n$surname"); add("$surname$n") }
            }
            for (v in variants) best = maxOf(best, wordSim(joined, v))
        }
        return if (best >= 0.85) best * 0.92 else best * 0.7
    }

    private fun wordSim(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length == 1 || b.length == 1) return if (a.first() == b.first()) 0.8 else 0.0
        if (a.startsWith(b) || b.startsWith(a)) return 0.85 // Bartolo ↔ Bartolomeo
        val d = levenshtein(a, b)
        return 1.0 - d.toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }
}
