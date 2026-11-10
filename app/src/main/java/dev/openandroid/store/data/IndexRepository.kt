package dev.openandroid.store.data

import android.content.Context
import dev.openandroid.store.BuildConfig
import dev.openandroid.store.core.IndexParser
import dev.openandroid.store.core.StoreIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the main catalog and the user's extra sources, and keeps the last good copy of each on disk,
 * so the store opens offline. When two sources list the same package, the earlier one wins (main first).
 */
class IndexRepository(context: Context, private val settings: StoreSettings) {
    private val dir = context.applicationContext.filesDir

    private class Target(val url: String, val main: Boolean) {
        val host: String? get() = if (main) null else runCatching { java.net.URI(url).host }.getOrNull() ?: url
    }

    private fun targets(): List<Target> =
        listOf(Target(settings.indexUrl.value, true)) + settings.sources.value.filter { it.enabled }.map { Target(it.url, false) }

    private fun cacheFile(t: Target): File {
        if (t.main) return File(dir, "index.json")
        val hash = MessageDigest.getInstance("SHA-256").digest(t.url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "index-${hash.take(12)}.json")
    }

    private fun parse(text: String, t: Target): StoreIndex {
        val index = IndexParser.parse(text, t.url)
        val host = t.host ?: return index
        return index.copy(apps = index.apps.map { it.copy(source = host) })
    }

    private fun merge(parts: List<StoreIndex>): StoreIndex {
        val seen = HashSet<String>()
        val apps = parts.flatMap { it.apps }.filter { seen.add(it.packageName) }
        return StoreIndex(parts.maxOfOrNull { it.generatedAt } ?: 0L, apps)
    }

    private fun readCache(t: Target): StoreIndex? {
        val file = cacheFile(t)
        return if (!file.isFile) null else runCatching { parse(file.readText(), t) }.getOrNull()
    }

    suspend fun cached(): StoreIndex? = withContext(Dispatchers.IO) {
        val parts = targets().mapNotNull { readCache(it) }
        if (parts.isEmpty()) null else merge(parts)
    }

    /** Fails only when no source could be downloaded; a broken extra source never hides the others. */
    suspend fun refresh(): Result<StoreIndex> = withContext(Dispatchers.IO) {
        val parts = ArrayList<StoreIndex>()
        var firstError: Throwable? = null
        var anyOk = false
        for (t in targets()) {
            val result = runCatching { download(t) }
            result.onSuccess { parts.add(it); anyOk = true }
            result.onFailure {
                if (firstError == null) firstError = it
                readCache(t)?.let { cached -> parts.add(cached) }
            }
        }
        if (!anyOk) Result.failure(firstError ?: IllegalStateException("No catalog"))
        else Result.success(merge(parts))
    }

    private fun download(t: Target): StoreIndex {
        val connection = (URL(t.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "OpenAndroidStore/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val index = parse(text, t)
            // Write to a temp file first so a dropped connection never destroys the cached copy.
            val file = cacheFile(t)
            val tmp = File(file.parentFile, file.name + ".part")
            tmp.writeText(text)
            tmp.renameTo(file)
            return index
        } finally {
            connection.disconnect()
        }
    }
}
