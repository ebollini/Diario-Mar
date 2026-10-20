package sm.mar.diario.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Accesso ai dati su Firestore: users/{uid}/activities, followups, inbox, settings/main. */
object Repo {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val uid: String? get() = auth.currentUser?.uid

    private fun col(name: String): CollectionReference =
        db.collection("users").document(uid ?: error("Non autenticato")).collection(name)

    private fun settingsDoc() = db.collection("users").document(uid ?: error("Non autenticato"))
        .collection("settings").document("main")

    private fun <T> listen(q: Query, map: (String, Map<String, Any?>) -> T): Flow<List<T>> = callbackFlow {
        val reg = q.addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
            if (snap != null) trySend(snap.documents.map { map(it.id, it.data.orEmpty()) })
        }
        awaitClose { reg.remove() }
    }

    fun day(date: String): Flow<List<Activity>> =
        listen(col("activities").whereEqualTo("date", date)) { id, m -> Activity.from(id, m) }

    fun recent(limit: Long = 400): Flow<List<Activity>> =
        listen(col("activities").orderBy("ts", Query.Direction.DESCENDING).limit(limit)) { id, m -> Activity.from(id, m) }

    fun openFollowUps(): Flow<List<FollowUp>> =
        listen(col("followups").whereEqualTo("done", false)) { id, m -> FollowUp.from(id, m) }

    fun pendingInbox(): Flow<List<InboxItem>> =
        listen(col("inbox").whereEqualTo("status", "new")) { id, m -> InboxItem.from(id, m) }

    fun settings(): Flow<Settings> = callbackFlow {
        val reg = settingsDoc().addSnapshotListener { snap, err ->
            if (err == null) trySend(Settings.from(snap?.data))
        }
        awaitClose { reg.remove() }
    }

    suspend fun getSettings(): Settings = runCatching { Settings.from(settingsDoc().get().await().data) }.getOrDefault(Settings())

    suspend fun updateSettings(fields: Map<String, Any>) {
        settingsDoc().set(fields, SetOptions.merge()).await()
    }

    suspend fun addToList(field: String, value: String) {
        settingsDoc().set(mapOf(field to FieldValue.arrayUnion(value)), SetOptions.merge()).await()
    }

    suspend fun removeFromList(field: String, value: String) {
        settingsDoc().set(mapOf(field to FieldValue.arrayRemove(value)), SetOptions.merge()).await()
    }

    // ---------- attività ----------
    /** Salva (crea o aggiorna) e restituisce l'id. Le scritture funzionano anche offline. */
    fun saveActivity(a: Activity): String {
        val ref = if (a.id.isEmpty()) col("activities").document() else col("activities").document(a.id)
        val data = a.copy(createdAt = if (a.createdAt == 0L) System.currentTimeMillis() else a.createdAt).toMap()
        ref.set(data, SetOptions.merge())
        return ref.id
    }

    fun deleteActivity(id: String) { col("activities").document(id).delete() }

    fun restoreActivity(a: Activity) { col("activities").document(a.id).set(a.toMap()) }

    // ---------- follow-up ----------
    fun addFollowUp(title: String, contact: String, company: String, due: String, activityId: String = "") {
        col("followups").add(
            mapOf(
                "title" to title, "contact" to contact, "company" to company, "due" to due,
                "done" to false, "activityId" to activityId, "createdAt" to System.currentTimeMillis(),
            )
        )
    }

    fun updateFollowUp(id: String, fields: Map<String, Any>) { col("followups").document(id).update(fields) }

    fun deleteFollowUp(id: String) { col("followups").document(id).delete() }

    // ---------- da registrare ----------
    fun setInboxStatus(id: String, status: String, activityId: String? = null) {
        val f = mutableMapOf<String, Any>("status" to status, "updatedAt" to System.currentTimeMillis())
        if (activityId != null) f["activityId"] = activityId
        col("inbox").document(id).update(f)
    }

    /** Crea l'elemento solo se non esiste già (usato dagli importatori). */
    suspend fun putInboxIfAbsent(id: String, data: Map<String, Any>): Boolean {
        val ref = col("inbox").document(id)
        val exists = runCatching { ref.get().await().exists() }.getOrDefault(false)
        if (exists) return false
        ref.set(data + mapOf("status" to "new", "createdAt" to System.currentTimeMillis())).await()
        return true
    }

    suspend fun inboxStatus(id: String): String? =
        runCatching { col("inbox").document(id).get().await().takeIf { it.exists() }?.getString("status") }.getOrNull()

    /** Aggiunge messaggi a una chat WhatsApp (senza duplicati). */
    suspend fun appendWhatsApp(id: String, base: Map<String, Any>, msgs: List<Map<String, Any>>) {
        val ref = col("inbox").document(id)
        val exists = runCatching { ref.get().await().exists() }.getOrDefault(false)
        val data = HashMap<String, Any>(base)
        if (!exists) {
            data["status"] = "new"
            data["createdAt"] = System.currentTimeMillis()
        } else {
            // l'ora della chat resta quella del primo messaggio
            data.remove("ts"); data.remove("time"); data.remove("date")
        }
        data["messages"] = FieldValue.arrayUnion(*msgs.toTypedArray())
        ref.set(data, SetOptions.merge()).await()
    }

    /** Quando richiamo un numero, le sue chiamate perse in attesa non servono più. */
    suspend fun dismissMissed(number: String) {
        val snap = runCatching {
            col("inbox").whereEqualTo("status", "new").whereEqualTo("kind", "call")
                .whereEqualTo("direction", "missed").whereEqualTo("address", number).get().await()
        }.getOrNull() ?: return
        snap.documents.forEach { it.reference.update("status", "ignored") }
    }

    /** Importazione dei dati del vecchio diario (JSON con activities/followups). */
    suspend fun importLegacy(acts: List<Map<String, Any?>>, fus: List<Map<String, Any?>>) {
        val batchSize = 400
        acts.chunked(batchSize).forEach { chunk ->
            val b = db.batch()
            chunk.forEach { m ->
                val id = m.str("id").ifEmpty { col("activities").document().id }
                b.set(col("activities").document(id), m.filterKeys { it != "id" }.filterValues { it != null }.mapValues { it.value!! })
            }
            b.commit().await()
        }
        fus.chunked(batchSize).forEach { chunk ->
            val b = db.batch()
            chunk.forEach { m ->
                val id = m.str("id").ifEmpty { col("followups").document().id }
                b.set(col("followups").document(id), m.filterKeys { it != "id" }.filterValues { it != null }.mapValues { it.value!! })
            }
            b.commit().await()
        }
    }
}
