package dev.openandroid.store

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openandroid.store.core.Catalog
import dev.openandroid.store.core.InstallState
import dev.openandroid.store.core.Installed
import dev.openandroid.store.core.StoreApp
import dev.openandroid.store.core.StoreIndex
import dev.openandroid.store.install.InstallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StoreViewModel(app: Application) : AndroidViewModel(app) {
    private val store = app as StoreApplication
    val settings = store.settings
    val installs: InstallManager = store.installManager

    private val _index = MutableStateFlow<StoreIndex?>(null)
    val index: StateFlow<StoreIndex?> = _index.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** True when the last refresh failed. The cached catalog, if any, is still shown. */
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    private val _installed = MutableStateFlow<Map<String, Installed>>(emptyMap())
    val installed: StateFlow<Map<String, Installed>> = _installed.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category.asStateFlow()

    val favorites: StateFlow<Set<String>> = settings.favorites
    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites.asStateFlow()
    fun setOnlyFavorites(value: Boolean) { _onlyFavorites.value = value }

    /** An update that asks for permissions the installed version does not have, waiting for the user's answer. */
    data class PermissionPrompt(val app: StoreApp, val added: List<String>)
    private val _prompt = MutableStateFlow<PermissionPrompt?>(null)
    val permissionPrompt: StateFlow<PermissionPrompt?> = _prompt.asStateFlow()

    fun setQuery(text: String) { _query.value = text }
    fun setCategory(value: String?) { _category.value = value }

    val categories: StateFlow<List<String>> = _index
        .map { Catalog.categories(it?.apps.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibleApps: StateFlow<List<StoreApp>> = combine(_index, _query, _category, _onlyFavorites, settings.favorites) { index, q, c, only, favs ->
        Catalog.search(index?.apps.orEmpty(), q, c).filter { !only || it.packageName in favs }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Apps with a newer compatible version than the installed one. */
    val updates: StateFlow<List<StoreApp>> = combine(_index, _installed) { index, installed ->
        index?.apps.orEmpty()
            .filter { stateOf(it, installed) == InstallState.UPDATE_AVAILABLE }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Installed apps from the catalog that are up to date (or cannot be updated). */
    val upToDate: StateFlow<List<StoreApp>> = combine(_index, _installed) { index, installed ->
        index?.apps.orEmpty()
            .filter { it.packageName in installed && stateOf(it, installed) != InstallState.UPDATE_AVAILABLE }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            store.repository.cached()?.let {
                _index.value = it
                refreshInstalled()
            }
            refresh()
        }
        // Re-read what is installed whenever an install finishes.
        viewModelScope.launch { installs.installedTick.collect { refreshInstalled() } }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val result = store.repository.refresh()
            result.onSuccess { _index.value = it; _offline.value = false }
            result.onFailure { _offline.value = true }
            refreshInstalled()
            _refreshing.value = false
        }
    }

    fun refreshInstalled() {
        val apps = _index.value?.apps ?: return
        viewModelScope.launch {
            val map = withContext(Dispatchers.IO) {
                buildMap { apps.forEach { a -> store.installedApps.get(a.packageName)?.let { put(a.packageName, it) } } }
            }
            _installed.value = map
        }
    }

    fun stateOf(app: StoreApp, installed: Map<String, Installed> = _installed.value): InstallState =
        Catalog.stateOf(app, installed[app.packageName], Build.VERSION.SDK_INT)

    fun appByPackage(pkg: String): StoreApp? = _index.value?.apps?.firstOrNull { it.packageName == pkg }

    fun canOpen(pkg: String) = store.installedApps.canOpen(pkg)

    fun install(app: StoreApp) {
        val version = app.latestFor(Build.VERSION.SDK_INT) ?: return
        installs.start(app, version)
    }

    /** Installs [app]; an update that adds permissions first asks the user. */
    fun requestInstall(app: StoreApp) {
        val latest = app.latestFor(Build.VERSION.SDK_INT) ?: return
        val added = Catalog.newPermissions(_installed.value[app.packageName], latest)
        if (added.isEmpty()) install(app) else _prompt.value = PermissionPrompt(app, added)
    }

    fun confirmPrompt() {
        _prompt.value?.let { install(it.app) }
        _prompt.value = null
    }

    fun dismissPrompt() { _prompt.value = null }

    fun toggleFavorite(packageName: String) = settings.toggleFavorite(packageName)

    fun updateAll() {
        val (needAsk, safe) = updates.value.partition {
            val latest = it.latestFor(Build.VERSION.SDK_INT)
            latest != null && Catalog.newPermissions(_installed.value[it.packageName], latest).isNotEmpty()
        }
        safe.forEach { install(it) }
        // Updates that add permissions are never installed without a yes; ask about the first one.
        needAsk.firstOrNull()?.let { requestInstall(it) }
    }

    fun setCheckHours(hours: Int) {
        settings.setCheckHours(hours)
        dev.openandroid.store.work.UpdateScheduler.apply(getApplication(), hours)
    }
}
