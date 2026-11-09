package dev.openandroid.store.core

import org.json.JSONArray
import org.json.JSONObject

/** Reads index.json. Unknown fields are ignored and broken entries are skipped, so one bad app cannot hide the rest. */
object IndexParser {
    const val SUPPORTED_FORMAT = 1

    /** [baseUrl] is the catalog address; relative icon paths in the index are resolved against it. */
    fun parse(text: String, baseUrl: String? = null): StoreIndex {
        val root = JSONObject(text)
        require(root.optInt("format", SUPPORTED_FORMAT) == SUPPORTED_FORMAT) { "Unsupported index format" }
        val apps = ArrayList<StoreApp>()
        val array = root.optJSONArray("apps") ?: JSONArray()
        for (i in 0 until array.length()) {
            runCatching { parseApp(array.getJSONObject(i), baseUrl) }.getOrNull()?.let { apps.add(it) }
        }
        return StoreIndex(root.optLong("generatedAt", 0L), apps)
    }

    private fun parseApp(o: JSONObject, baseUrl: String?): StoreApp {
        val versions = ArrayList<AppVersion>()
        val array = o.optJSONArray("versions") ?: JSONArray()
        for (i in 0 until array.length()) {
            runCatching { parseVersion(array.getJSONObject(i)) }.getOrNull()?.let { versions.add(it) }
        }
        val packageName = o.getString("package")
        return StoreApp(
            packageName = packageName,
            name = o.optString("name", packageName).ifBlank { packageName },
            summary = o.optString("summary"),
            description = o.optString("description"),
            category = o.optString("category").ifBlank { "Other" },
            repo = o.optString("repo"),
            license = o.optString("license"),
            author = o.optString("author"),
            iconUrl = o.optString("icon").takeIf { it.isNotBlank() }?.let { resolve(baseUrl, it) },
            versions = versions.sortedByDescending { it.versionCode },
            website = o.optString("website").takeIf { it.startsWith("https://") },
            issues = o.optString("issues").takeIf { it.startsWith("https://") },
            screenshots = o.optJSONArray("screenshots")?.let { list ->
                (0 until list.length()).mapNotNull { resolve(baseUrl, list.optString(it)) }
            }.orEmpty(),
        )
    }

    private fun parseVersion(o: JSONObject): AppVersion {
        val url = o.getString("apk")
        require(url.startsWith("https://")) { "APK links must use https" }
        val perms = ArrayList<String>()
        o.optJSONArray("permissions")?.let { for (i in 0 until it.length()) perms.add(it.getString(i)) }
        return AppVersion(
            versionName = o.getString("versionName"),
            versionCode = o.getLong("versionCode"),
            minSdk = o.optInt("minSdk", 1),
            apkUrl = url,
            size = o.optLong("size", 0L),
            sha256 = o.getString("sha256").lowercase(),
            signer = o.optString("signer").lowercase(),
            permissions = perms,
            changelog = o.optString("changelog"),
            publishedAt = o.optString("publishedAt"),
        )
    }

    private fun resolve(base: String?, path: String): String? {
        if (path.startsWith("https://")) return path
        if (base == null) return null
        return runCatching { java.net.URI(base).resolve(path).toString() }.getOrNull()?.takeIf { it.startsWith("https://") }
    }
}
