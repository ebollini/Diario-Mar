package sm.mar.diario.importers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dati anagrafici trovati in rubrica. */
data class ContactInfo(val name: String = "", val phone: String = "", val email: String = "", val company: String = "")

/** Cerca un contatto in rubrica per email, numero o nome e ne restituisce i dati. */
object ContactLookup {

    fun allowed(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun find(ctx: Context, email: String, phone: String, name: String): ContactInfo? = withContext(Dispatchers.IO) {
        if (!allowed(ctx)) return@withContext null
        runCatching {
            val id = (if (email.contains("@")) byEmail(ctx, email.trim()) else null)
                ?: (if (phone.count { it.isDigit() } >= 6) byPhone(ctx, phone.trim()) else null)
                ?: (if (name.trim().length >= 4) byName(ctx, name.trim()) else null)
                ?: return@runCatching null
            details(ctx, id)
        }.getOrNull()
    }

    private fun byEmail(ctx: Context, email: String): String? =
        ctx.contentResolver.query(
            ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.CONTACT_ID),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${Email.ADDRESS} = ? COLLATE NOCASE",
            arrayOf(Email.CONTENT_ITEM_TYPE, email), null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun byPhone(ctx: Context, phone: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        return ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    private fun byName(ctx: Context, name: String): String? {
        // prima il nome esatto, poi "cognome nome" invertito
        val parts = name.split(Regex("\\s+")).filter { it.isNotBlank() }
        val variants = buildList {
            add(name)
            if (parts.size == 2) add("${parts[1]} ${parts[0]}")
        }
        for (v in variants) {
            ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ? COLLATE NOCASE", arrayOf(v), null,
            )?.use { c -> if (c.count == 1 && c.moveToFirst()) return c.getString(0) }
        }
        return null
    }

    private fun details(ctx: Context, id: String): ContactInfo {
        var name = ""
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts._ID} = ?", arrayOf(id), null,
        )?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: "" }

        var mobile = ""
        var otherPhone = ""
        var email = ""
        var company = ""
        ctx.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)",
            arrayOf(id, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mt = c.getString(0)
                val v = c.getString(1)?.trim().orEmpty()
                if (v.isEmpty()) continue
                when (mt) {
                    Phone.CONTENT_ITEM_TYPE ->
                        if (c.getInt(2) == Phone.TYPE_MOBILE) { if (mobile.isEmpty()) mobile = v } else if (otherPhone.isEmpty()) otherPhone = v
                    Email.CONTENT_ITEM_TYPE -> if (email.isEmpty()) email = v
                    Organization.CONTENT_ITEM_TYPE -> if (company.isEmpty()) company = v
                }
            }
        }
        return ContactInfo(name, mobile.ifEmpty { otherPhone }, email, company)
    }
}
