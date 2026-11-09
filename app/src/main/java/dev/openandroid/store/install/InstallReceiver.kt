package dev.openandroid.store.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dev.openandroid.store.StoreApplication

/** Receives the system installer's answer: ask the user to confirm, succeeded, or failed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as StoreApplication
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val file = intent.getStringExtra(EXTRA_FILE)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { confirm ->
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> app.installManager.onInstalled(pkg, file)
            else -> app.installManager.onFailed(
                pkg,
                file,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty(),
            )
        }
    }

    companion object {
        const val ACTION = "dev.openandroid.store.INSTALL_RESULT"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_FILE = "file"
    }
}
