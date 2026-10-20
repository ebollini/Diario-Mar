package sm.mar.diario

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import sm.mar.diario.work.SyncWorker

class DiarioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(100L * 1024 * 1024).build())
                .build()
        }
        SyncWorker.createChannel(this)
        SyncWorker.schedule(this)
    }
}
