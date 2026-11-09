package dev.openandroid.store.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.openandroid.store.MainActivity
import dev.openandroid.store.R
import dev.openandroid.store.StoreApplication
import dev.openandroid.store.core.Catalog
import dev.openandroid.store.core.InstallState
import java.util.concurrent.TimeUnit

const val UPDATES_CHANNEL = "updates"

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(UPDATES_CHANNEL, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** Checks the catalog in the background and notifies once per new update. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StoreApplication
        val index = app.repository.refresh().getOrNull()
            ?: return if (runAttemptCount < 3) Result.retry() else Result.success()

        val pending = index.apps.filter {
            Catalog.stateOf(it, app.installedApps.get(it.packageName), Build.VERSION.SDK_INT) == InstallState.UPDATE_AVAILABLE
        }
        val already = app.settings.notified
        val fresh = pending.filter { a ->
            val code = a.latestFor(Build.VERSION.SDK_INT)?.versionCode ?: return@filter false
            "${a.packageName}:$code" !in already
        }
        if (fresh.isNotEmpty()) {
            notify(fresh.map { it.name })
            app.settings.notified = already + fresh.mapNotNull { a ->
                a.latestFor(Build.VERSION.SDK_INT)?.let { "${a.packageName}:${it.versionCode}" }
            }
        }
        return Result.success()
    }

    private fun notify(names: List<String>) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createNotificationChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_UPDATES)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.resources.getQuantityString(R.plurals.notification_updates, names.size, names.size)
        val notification = NotificationCompat.Builder(context, UPDATES_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_store)
            .setContentTitle(title)
            .setContentText(names.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString(", ")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val NOTIFICATION_ID = 1
    }
}

object UpdateScheduler {
    private const val NAME = "update-check"

    /** Applies the chosen interval; 0 cancels the checks. */
    fun apply(context: Context, hours: Int) {
        val work = WorkManager.getInstance(context)
        if (hours <= 0) {
            work.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
