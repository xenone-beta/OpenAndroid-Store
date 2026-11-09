package dev.openandroid.store

import android.app.Application
import dev.openandroid.store.data.IndexRepository
import dev.openandroid.store.data.InstalledApps
import dev.openandroid.store.data.StoreSettings
import dev.openandroid.store.install.InstallManager
import dev.openandroid.store.work.UpdateScheduler
import dev.openandroid.store.work.createNotificationChannel

/** Hand-rolled dependency container shared by the UI, the installer receiver and the background worker. */
class StoreApplication : Application() {
    val settings: StoreSettings by lazy { StoreSettings(this) }
    val repository: IndexRepository by lazy { IndexRepository(this, settings) }
    val installedApps: InstalledApps by lazy { InstalledApps(this) }
    val installManager: InstallManager by lazy { InstallManager(this, installedApps) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        UpdateScheduler.apply(this, settings.checkHours.value)
    }
}
