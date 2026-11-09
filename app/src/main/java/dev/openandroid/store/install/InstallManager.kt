package dev.openandroid.store.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dev.openandroid.store.BuildConfig
import dev.openandroid.store.core.AppVersion
import dev.openandroid.store.core.StoreApp
import dev.openandroid.store.data.InstalledApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Why an install stopped. The UI turns these into messages. */
enum class FailReason { DOWNLOAD, HASH, WRONG_PACKAGE, WRONG_SIGNER, INSTALL, NEEDS_PERMISSION }

/** Where an app is in the download and install process. Absent from the map means idle. */
sealed interface Transfer {
    /** [fraction] is 0..1, or -1 when the size is unknown. */
    data class Downloading(val fraction: Float) : Transfer
    data object Verifying : Transfer
    data object Installing : Transfer
    data class Failed(val reason: FailReason, val detail: String = "") : Transfer
}

/**
 * Downloads an APK, checks it against the catalog, and hands it to the system installer.
 * Checks, in order: SHA-256 equals the catalog, the package name matches, the signing key matches.
 */
class InstallManager(private val context: Context, private val installed: InstalledApps) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir get() = File(context.cacheDir, "apks").apply { mkdirs() }

    private val jobs = HashMap<String, Job>()

    private val _transfers = MutableStateFlow<Map<String, Transfer>>(emptyMap())
    val transfers: StateFlow<Map<String, Transfer>> = _transfers.asStateFlow()

    private val _installedTick = MutableStateFlow(0)
    /** Bumped whenever an install finishes, so screens re-read what is installed. */
    val installedTick: StateFlow<Int> = _installedTick.asStateFlow()

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun start(app: StoreApp, version: AppVersion) {
        val pkg = app.packageName
        if (_transfers.value[pkg].let { it is Transfer.Downloading || it is Transfer.Verifying || it is Transfer.Installing }) return
        if (!canInstall()) {
            set(pkg, Transfer.Failed(FailReason.NEEDS_PERMISSION))
            return
        }
        set(pkg, Transfer.Downloading(0f))
        val job = scope.launch {
            val file = File(dir, "$pkg-${version.versionCode}.apk")
            try {
                download(pkg, version, file)
                set(pkg, Transfer.Verifying)
                verify(app, version, file)?.let { set(pkg, it); file.delete(); return@launch }
                set(pkg, Transfer.Installing)
                commit(pkg, file)
            } catch (e: CancellationException) {
                file.delete()
                _transfers.update { it - pkg }
                throw e
            } catch (e: HashMismatch) {
                file.delete()
                set(pkg, Transfer.Failed(FailReason.HASH))
            } catch (e: Exception) {
                file.delete()
                set(pkg, Transfer.Failed(FailReason.DOWNLOAD, e.message.orEmpty()))
            }
        }
        synchronized(jobs) { jobs[pkg] = job }
        job.invokeOnCompletion { synchronized(jobs) { if (jobs[pkg] === job) jobs.remove(pkg) } }
    }

    /** Stops a download or verification in progress. Once the system installer has the file it cannot be stopped. */
    fun cancel(pkg: String) {
        if (_transfers.value[pkg] is Transfer.Installing) return
        synchronized(jobs) { jobs[pkg] }?.cancel()
        _transfers.update { it - pkg }
    }

    fun dismiss(packageName: String) = _transfers.update { it - packageName }

    private fun set(pkg: String, value: Transfer) = _transfers.update { it + (pkg to value) }

    private suspend fun download(pkg: String, version: AppVersion, target: File) = withContext(Dispatchers.IO) {
        val part = File(target.path + ".part")
        val connection = (URL(version.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "OpenAndroidStore/${BuildConfig.VERSION_NAME}")
        }
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val total = if (connection.contentLengthLong > 0) connection.contentLengthLong else version.size
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastReported = -1f
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                        if (fraction < 0 || fraction - lastReported >= 0.01f) {
                            lastReported = fraction
                            set(pkg, Transfer.Downloading(fraction))
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(version.sha256, ignoreCase = true)) {
                part.delete()
                throw HashMismatch()
            }
            target.delete()
            check(part.renameTo(target)) { "Could not save the file" }
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private class HashMismatch : Exception("Checksum mismatch")

    /** The file was already hashed while downloading; here the APK itself is checked against the catalog. */
    private fun verify(app: StoreApp, version: AppVersion, file: File): Transfer.Failed? {
        val (pkg, info) = installed.archive(file.path) ?: return Transfer.Failed(FailReason.WRONG_PACKAGE, "Unreadable APK")
        if (pkg != app.packageName) return Transfer.Failed(FailReason.WRONG_PACKAGE, pkg)
        if (version.signer.isNotEmpty() && !version.signer.equals(info.signer, ignoreCase = true)) {
            return Transfer.Failed(FailReason.WRONG_SIGNER)
        }
        return null
    }

    private fun commit(pkg: String, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg)
            // Updates of apps this store installed can go through without a confirmation screen on Android 12+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallReceiver::class.java)
                .setAction(InstallReceiver.ACTION)
                .putExtra(InstallReceiver.EXTRA_PACKAGE, pkg)
                .putExtra(InstallReceiver.EXTRA_FILE, file.path)
            // The installer fills in the status, so the intent has to be mutable.
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }

    // ---- Called by the receiver ------------------------------------------------------------

    fun onInstalled(pkg: String, path: String?) {
        path?.let { File(it).delete() }
        _transfers.update { it - pkg }
        _installedTick.update { it + 1 }
    }

    fun onFailed(pkg: String, path: String?, message: String) {
        path?.let { File(it).delete() }
        set(pkg, Transfer.Failed(FailReason.INSTALL, message))
    }
}
