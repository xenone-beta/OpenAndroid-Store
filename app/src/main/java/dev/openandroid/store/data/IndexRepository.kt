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

/** Downloads the catalog and keeps the last good copy on disk, so the store opens offline. */
class IndexRepository(context: Context, private val settings: StoreSettings) {
    private val cacheFile = File(context.applicationContext.filesDir, "index.json")

    suspend fun cached(): StoreIndex? = withContext(Dispatchers.IO) {
        if (!cacheFile.isFile) null else runCatching { IndexParser.parse(cacheFile.readText(), settings.indexUrl.value) }.getOrNull()
    }

    suspend fun refresh(): Result<StoreIndex> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(settings.indexUrl.value)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "OpenAndroidStore/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
                val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                val index = IndexParser.parse(text, settings.indexUrl.value)
                // Write to a temp file first so a dropped connection never destroys the cached copy.
                val tmp = File(cacheFile.parentFile, "index.json.part")
                tmp.writeText(text)
                tmp.renameTo(cacheFile)
                index
            } finally {
                connection.disconnect()
            }
        }
    }
}
