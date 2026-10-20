package sm.mar.diario.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import sm.mar.diario.MainActivity
import sm.mar.diario.R
import sm.mar.diario.data.Dates
import sm.mar.diario.data.Prefs
import sm.mar.diario.data.Repo
import sm.mar.diario.importers.CallImporter
import sm.mar.diario.importers.MailImporter
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Ogni 30 minuti: importa telefonate ed email; in orario di lavoro ricorda di fare il punto. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (Repo.uid == null) return Result.success()
        val ctx = applicationContext
        val calls = runCatching { CallImporter.run(ctx) }.getOrDefault(0)
        val mail = runCatching { MailImporter.run(ctx).added }.getOrDefault(0)
        if (!inputData.getBoolean(KEY_MANUAL, false)) maybeRemind(ctx)
        return Result.success(workDataOf("calls" to calls, "mail" to mail))
    }

    private suspend fun maybeRemind(ctx: Context) {
        val now = LocalDateTime.now(Dates.zone)
        if (now.dayOfWeek.value > 5 || now.hour < 8 || now.hour >= 20) return
        val s = Repo.getSettings()
        val prefs = Prefs(ctx)
        val gapMs = s.checkinHours * 3_600_000L
        val ref = maxOf(s.lastCheckin, prefs.lastReminder)
        if (System.currentTimeMillis() - ref < gapMs) return
        val pending = runCatching { Repo.pendingInbox().first().size }.getOrDefault(0)
        notifyCheckin(ctx, pending)
        prefs.lastReminder = System.currentTimeMillis()
    }

    companion object {
        const val CHANNEL = "checkin"
        const val KEY_MANUAL = "manual"
        private const val PERIODIC = "sync-periodic"
        private const val NOW = "sync-now"

        fun createChannel(ctx: Context) {
            val ch = NotificationChannel(CHANNEL, "Fai il punto", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Promemoria per aggiornare il diario"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_MANUAL to true))
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }

        fun notifyCheckin(ctx: Context, pending: Int) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_TAB, "inbox")
            }
            val pi = PendingIntent.getActivity(ctx, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val text = if (pending > 0) "$pending elementi da registrare: telefonate, email e WhatsApp delle ultime ore."
            else "Telefonate, email e incontri delle ultime ore: aggiorna il diario."
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("È ora di fare il punto")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(42, n)
        }
    }
}
