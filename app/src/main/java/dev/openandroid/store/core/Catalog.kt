package dev.openandroid.store.core

import java.text.Normalizer
import java.util.Locale

/** Pure catalog logic: install state, search, categories. No Android types, so it is unit tested. */
object Catalog {

    fun stateOf(app: StoreApp, installed: Installed?, sdk: Int): InstallState {
        val latest = app.latestFor(sdk)
        if (installed == null) return if (latest == null) InstallState.INCOMPATIBLE else InstallState.NOT_INSTALLED
        if (latest == null) return InstallState.INSTALLED
        // Android refuses an update signed with another key, so say so instead of failing at the end.
        if (installed.signer != null && latest.signer.isNotEmpty() && !installed.signer.equals(latest.signer, ignoreCase = true)) {
            return InstallState.SIGNATURE_MISMATCH
        }
        return if (latest.versionCode > installed.versionCode) InstallState.UPDATE_AVAILABLE else InstallState.INSTALLED
    }

    /** Permissions [version] asks for that the installed copy does not. Empty for a first install. */
    fun newPermissions(installed: Installed?, version: AppVersion): List<String> =
        if (installed == null) emptyList() else version.permissions.filter { it !in installed.permissions }.sorted()

    fun search(apps: List<StoreApp>, query: String, category: String?): List<StoreApp> {
        val q = normalize(query)
        return apps
            .filter { category == null || it.category == category }
            .filter { q.isEmpty() || normalize("${it.name} ${it.summary} ${it.author} ${it.packageName}").contains(q) }
            .sortedBy { normalize(it.name) }
    }

    /** Most starred first; apps without stars are left out. */
    fun popular(apps: List<StoreApp>, limit: Int = 10): List<StoreApp> =
        apps.filter { it.stars > 0 }.sortedWith(compareByDescending<StoreApp> { it.stars }.thenBy { normalize(it.name) }).take(limit)

    /** Apps whose first release is the most recent. */
    fun newest(apps: List<StoreApp>, limit: Int = 10): List<StoreApp> =
        apps.filter { it.firstPublished.isNotEmpty() }.sortedByDescending { it.firstPublished }.take(limit)

    /** Apps with the most recent release. */
    fun recentlyUpdated(apps: List<StoreApp>, limit: Int = 10): List<StoreApp> =
        apps.filter { it.lastPublished.isNotEmpty() }.sortedByDescending { it.lastPublished }.take(limit)

    fun categories(apps: List<StoreApp>): List<String> =
        apps.map { it.category }.distinct().sortedBy { it.lowercase(Locale.ROOT) }

    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    }

    /** "ABCD…" style short form of a hex digest, for display. */
    fun shortDigest(hex: String): String =
        if (hex.length <= 16) hex.uppercase(Locale.ROOT) else hex.take(16).uppercase(Locale.ROOT) + "…"

    /** Last part of a permission name: android.permission.CAMERA becomes CAMERA. */
    fun permissionLabel(name: String): String = name.substringAfterLast('.')

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .trim()
}
