package sm.mar.diario.importers

import android.content.Context
import com.sun.mail.imap.IMAPFolder
import sm.mar.diario.data.Dates
import sm.mar.diario.data.Prefs
import sm.mar.diario.data.Repo
import java.util.Date
import java.util.Properties
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm

/** Legge la casella di lavoro direttamente dal server (IMAP): posta in arrivo e posta inviata. */
object MailImporter {
    private val autoSender = Regex("(no-?reply|do-?not-?reply|notification|notifiche|mailer-daemon|newsletter|postmaster|bounce)", RegexOption.IGNORE_CASE)
    private val freeMail = setOf("gmail", "googlemail", "libero", "hotmail", "outlook", "live", "yahoo", "icloud", "me", "tin", "alice", "virgilio", "tiscali", "fastwebnet", "email", "msn", "aol", "pec")

    class Result(val added: Int, val error: String?)

    private fun session(p: Prefs): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", p.imapHost)
            put("mail.imaps.port", p.imapPort.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "30000")
            put("mail.imaps.partialfetch", "false")
        }
        return Session.getInstance(props)
    }

    /** Prova la connessione e restituisce l'elenco delle cartelle, o un errore leggibile. */
    fun test(p: Prefs): Pair<List<String>, String?> = runCatching {
        val store = session(p).getStore("imaps")
        store.connect(p.imapHost, p.imapPort, p.imapUser, p.imapPassword)
        val names = store.defaultFolder.list("*").map { it.fullName }
        store.close()
        names to null
    }.getOrElse { emptyList<String>() to friendly(it) }

    fun friendly(t: Throwable): String {
        val m = (t.message ?: t.javaClass.simpleName)
        return when {
            m.contains("auth", true) || m.contains("login", true) || m.contains("credenz", true) -> "Utente o password email non corretti."
            m.contains("UnknownHost", true) || t is java.net.UnknownHostException -> "Server email non raggiungibile: controlla la connessione."
            m.contains("timed out", true) || m.contains("timeout", true) -> "Il server email non risponde. Riprovo più tardi."
            m.contains("certificate", true) || m.contains("SSL", true) -> "Problema con il certificato di sicurezza del server email."
            else -> "Errore email: $m"
        }
    }

    suspend fun run(ctx: Context): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runBlockingIo(ctx) }

    private suspend fun runBlockingIo(ctx: Context): Result {
        val p = Prefs(ctx)
        if (!p.mailConfigured || Repo.uid == null) return Result(0, null)
        var added = 0
        return try {
            val store = session(p).getStore("imaps")
            store.connect(p.imapHost, p.imapPort, p.imapUser, p.imapPassword)
            val ignore = Repo.getSettings().ignoreAddresses.map { it.lowercase() }.toSet()
            for ((folderName, dir) in listOf(p.inboxFolder to "in", p.sentFolder to "out")) {
                val folder = store.getFolder(folderName)
                if (!folder.exists()) continue
                folder.open(Folder.READ_ONLY)
                val since = (if (dir == "in") p.lastMailIn else p.lastMailOut).takeIf { it > 0 } ?: Dates.startOfToday()
                // la ricerca IMAP per data è a giorni: filtro poi al millisecondo
                val msgs = folder.search(ReceivedDateTerm(ComparisonTerm.GE, Date(since - 86_400_000L)))
                val fp = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(UIDFolder.FetchProfileItem.UID)
                    add("List-Unsubscribe"); add("Auto-Submitted"); add("Precedence"); add("X-Auto-Response-Suppress")
                }
                folder.fetch(msgs, fp)
                var newest = since
                val uidFolder = folder as UIDFolder
                val validity = (folder as? IMAPFolder)?.uidValidity ?: 0L
                for (m in msgs) {
                    val received = (m.receivedDate ?: m.sentDate)?.time ?: continue
                    if (received <= since) continue
                    if (received > newest) newest = received
                    if (isAutomatic(m)) continue
                    val other: InternetAddress = (if (dir == "in") (m.from?.firstOrNull() as? InternetAddress)
                    else (m.getRecipients(Message.RecipientType.TO)?.firstOrNull() as? InternetAddress)) ?: continue
                    val addr = other.address?.lowercase() ?: continue
                    if (addr in ignore) continue
                    if (dir == "in" && autoSender.containsMatchIn(addr)) continue
                    val allTo = m.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { addrOf(it) } ?: ""
                    val data = mapOf<String, Any>(
                        "kind" to "mail", "direction" to dir,
                        "address" to addr,
                        "from" to (m.from?.firstOrNull()?.let { addrOf(it) } ?: ""),
                        "to" to allTo,
                        "contact" to (other.personal?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() } ?: nameFromAddress(addr)),
                        "company" to companyOf(addr),
                        "subject" to (m.subject ?: ""),
                        "snippet" to snippet(m),
                        "date" to Dates.dateOf(received), "time" to Dates.timeOf(received), "ts" to received,
                    )
                    val id = "mail-$dir-$validity-${uidFolder.getUID(m)}"
                    if (Repo.putInboxIfAbsent(id, data)) added++
                }
                if (dir == "in") p.lastMailIn = newest else p.lastMailOut = newest
                folder.close(false)
            }
            store.close()
            p.lastMailError = ""
            Result(added, null)
        } catch (t: Throwable) {
            val e = friendly(t)
            p.lastMailError = e
            Result(added, e)
        }
    }

    private fun addrOf(a: Address): String = (a as? InternetAddress)?.address ?: a.toString()

    private fun isAutomatic(m: Message): Boolean {
        fun h(n: String) = runCatching { m.getHeader(n)?.firstOrNull() }.getOrNull()?.lowercase()
        val auto = h("Auto-Submitted")
        if (auto != null && auto != "no") return true
        if (h("Precedence") in setOf("bulk", "list", "junk")) return true
        if (h("List-Unsubscribe") != null) return true
        return false
    }

    fun nameFromAddress(addr: String): String {
        val local = addr.substringBefore("@")
        if (local.length < 3 || local in setOf("info", "sales", "service", "commerciale", "ufficio", "amministrazione", "direzione", "acquisti")) return addr
        return local.split('.', '_', '-').filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    fun companyOf(addr: String): String {
        val domain = addr.substringAfter("@", "").lowercase()
        if (domain.isEmpty()) return ""
        if (domain == "mar.sm") return "MAR srl"
        val parts = domain.split('.')
        val main = if (parts.size >= 2) parts[parts.size - 2] else parts[0]
        if (main in freeMail) return ""
        return main.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun snippet(m: Message): String = runCatching {
        val text = textOf(m) ?: return@runCatching ""
        val lines = text.lines().map { it.trim() }
        val out = StringBuilder()
        for (l in lines) {
            if (l.startsWith(">")) continue
            if (Regex("^(il giorno .* ha scritto|on .* wrote|-----original message|da: |from: |inviato: |sent: |-- ?$|cordiali saluti|distinti saluti|best regards|kind regards)", RegexOption.IGNORE_CASE).containsMatchIn(l)) break
            if (l.isEmpty()) continue
            if (out.isNotEmpty()) out.append(' ')
            out.append(l)
            if (out.length > 280) break
        }
        out.toString().take(280)
    }.getOrDefault("")

    private fun textOf(p: Part): String? {
        if (p.isMimeType("text/plain")) return p.content as? String
        if (p.isMimeType("text/html")) return (p.content as? String)?.let { htmlToText(it) }
        if (p.isMimeType("multipart/alternative")) {
            val mp = p.content as Multipart
            var html: String? = null
            for (i in 0 until mp.count) {
                val bp = mp.getBodyPart(i)
                if (bp.isMimeType("text/plain")) return bp.content as? String
                if (bp.isMimeType("text/html")) html = (bp.content as? String)?.let { htmlToText(it) }
            }
            return html
        }
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as Multipart
            for (i in 0 until mp.count) {
                val bp = mp.getBodyPart(i)
                if (Part.ATTACHMENT.equals(bp.disposition, true)) continue
                textOf(bp)?.let { return it }
            }
        }
        return null
    }

    private fun htmlToText(h: String): String = h
        .replace(Regex("(?is)<(style|script)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</tr>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&egrave;", "è").replace("&agrave;", "à")
        .replace(Regex("[ \\t]+"), " ")
}
