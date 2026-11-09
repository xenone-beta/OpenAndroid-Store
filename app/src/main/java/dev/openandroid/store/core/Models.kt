package dev.openandroid.store.core

/** One downloadable release of an app, as listed in the catalog. */
data class AppVersion(
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val apkUrl: String,
    val size: Long,
    /** Lower-case hex SHA-256 of the APK file. */
    val sha256: String,
    /** Lower-case hex SHA-256 of the signing certificate, empty when unknown. */
    val signer: String,
    val permissions: List<String>,
    val changelog: String,
    /** ISO-8601 date such as 2026-11-09T08:00:00Z, empty when unknown. */
    val publishedAt: String,
)

data class StoreApp(
    val packageName: String,
    val name: String,
    val summary: String,
    val description: String,
    val category: String,
    val repo: String,
    val license: String,
    val author: String,
    val iconUrl: String?,
    /** Newest first. */
    val versions: List<AppVersion>,
    val website: String? = null,
    /** Where to report problems; null when the app gives no link. */
    val issues: String? = null,
    val screenshots: List<String> = emptyList(),
) {
    /** The newest version that runs on [sdk], or null when none does. */
    fun latestFor(sdk: Int): AppVersion? = versions.filter { it.minSdk <= sdk }.maxByOrNull { it.versionCode }
}

data class StoreIndex(val generatedAt: Long, val apps: List<StoreApp>)

/** What is installed on this phone for a package. */
data class Installed(
    val versionCode: Long,
    val versionName: String,
    val signer: String?,
    /** Permissions the installed version asks for. */
    val permissions: Set<String> = emptySet(),
)

enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE, SIGNATURE_MISMATCH, INCOMPATIBLE }
