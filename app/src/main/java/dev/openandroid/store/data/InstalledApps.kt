package dev.openandroid.store.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dev.openandroid.store.core.Installed
import java.security.MessageDigest

/** Reads what is installed on the phone. Needs QUERY_ALL_PACKAGES on Android 11+. */
class InstalledApps(context: Context) {
    private val pm = context.applicationContext.packageManager

    fun get(packageName: String): Installed? = try {
        val info = pm.getPackageInfo(packageName, signatureFlag() or PackageManager.GET_PERMISSIONS)
        Installed(PackageInfoCompat.getLongVersionCode(info), info.versionName.orEmpty(), signerOf(info), info.requestedPermissions?.toSet().orEmpty())
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    fun canOpen(packageName: String): Boolean = pm.getLaunchIntentForPackage(packageName) != null

    /** Reads package name, version and signer from an APK file, for checking it before installing. */
    fun archive(path: String): Pair<String, Installed>? {
        val info = pm.getPackageArchiveInfo(path, signatureFlag()) ?: return null
        return info.packageName to Installed(PackageInfoCompat.getLongVersionCode(info), info.versionName.orEmpty(), signerOf(info))
    }

    @Suppress("DEPRECATION")
    private fun signatureFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    /** SHA-256 of the signing certificate, the same value apksigner prints. */
    @Suppress("DEPRECATION")
    private fun signerOf(info: PackageInfo): String? {
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return null
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
