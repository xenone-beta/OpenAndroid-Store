package dev.openandroid.store.data

import android.content.Context
import dev.openandroid.store.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
    const val AMOLED = 3
}

/** Hours between background checks for updates; 0 turns them off. */
val CHECK_INTERVALS = listOf(0, 6, 12, 24)

/** An extra catalog the user added. */
data class Source(val url: String, val enabled: Boolean)

class StoreSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("store_settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(prefs.getInt(KEY_THEME, ThemeMode.SYSTEM))
    val themeMode: StateFlow<Int> = _theme.asStateFlow()

    private val _dynamic = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, true))
    val dynamicColor: StateFlow<Boolean> = _dynamic.asStateFlow()

    private val _interval = MutableStateFlow(prefs.getInt(KEY_INTERVAL, 12))
    val checkHours: StateFlow<Int> = _interval.asStateFlow()

    private val _indexUrl = MutableStateFlow(prefs.getString(KEY_INDEX, null) ?: BuildConfig.DEFAULT_INDEX_URL)
    val indexUrl: StateFlow<String> = _indexUrl.asStateFlow()

    private val _favorites = MutableStateFlow(prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _sources = MutableStateFlow(readSources())
    /** Extra catalogs added by the user, in the order they were added. */
    val sources: StateFlow<List<Source>> = _sources.asStateFlow()

    private fun readSources(): List<Source> =
        prefs.getString(KEY_SOURCES, "").orEmpty().lines().mapNotNull { line ->
            val enabled = line.startsWith("1 ")
            val url = line.drop(2).trim()
            if ((enabled || line.startsWith("0 ")) && isValidIndexUrl(url)) Source(url, enabled) else null
        }

    private fun saveSources(list: List<Source>) {
        prefs.edit().putString(KEY_SOURCES, list.joinToString("\n") { (if (it.enabled) "1 " else "0 ") + it.url }).apply()
        _sources.value = list
    }

    /** Returns false when [url] is not a usable https address or is already known. */
    fun addSource(url: String): Boolean {
        val clean = url.trim()
        if (!isValidIndexUrl(clean) || clean == _indexUrl.value || _sources.value.any { it.url == clean }) return false
        saveSources(_sources.value + Source(clean, true))
        return true
    }

    fun removeSource(url: String) = saveSources(_sources.value.filter { it.url != url })

    fun setSourceEnabled(url: String, enabled: Boolean) =
        saveSources(_sources.value.map { if (it.url == url) it.copy(enabled = enabled) else it })

    fun toggleFavorite(packageName: String) {
        val next = if (packageName in _favorites.value) _favorites.value - packageName else _favorites.value + packageName
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
        _favorites.value = next
    }

    fun setThemeMode(value: Int) { prefs.edit().putInt(KEY_THEME, value).apply(); _theme.value = value }
    fun setDynamicColor(value: Boolean) { prefs.edit().putBoolean(KEY_DYNAMIC, value).apply(); _dynamic.value = value }
    fun setCheckHours(value: Int) { prefs.edit().putInt(KEY_INTERVAL, value).apply(); _interval.value = value }

    /** Only https catalogs are accepted. Returns false when [url] is not usable. */
    fun setIndexUrl(url: String): Boolean {
        val clean = url.trim()
        if (!isValidIndexUrl(clean)) return false
        prefs.edit().putString(KEY_INDEX, clean).apply()
        _indexUrl.value = clean
        return true
    }

    fun resetIndexUrl() {
        prefs.edit().remove(KEY_INDEX).apply()
        _indexUrl.value = BuildConfig.DEFAULT_INDEX_URL
    }

    /** "package:versionCode" pairs the user was already told about, so each update notifies once. */
    var notified: Set<String>
        get() = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        set(value) { prefs.edit().putStringSet(KEY_NOTIFIED, value).apply() }

    companion object {
        fun isValidIndexUrl(url: String): Boolean =
            url.startsWith("https://") && url.length > "https://".length + 3 && !url.contains(' ')

        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC = "dynamic"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_INDEX = "index_url"
        private const val KEY_NOTIFIED = "notified"
        private const val KEY_SOURCES = "sources"
        private const val KEY_FAVORITES = "favorites"
    }
}
