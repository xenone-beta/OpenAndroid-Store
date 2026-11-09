@file:OptIn(ExperimentalMaterial3Api::class)

package dev.openandroid.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openandroid.store.R
import dev.openandroid.store.StoreViewModel

@Composable
fun CatalogScreen(vm: StoreViewModel, modifier: Modifier, onOpenApp: (String) -> Unit) {
    val apps by vm.visibleApps.collectAsState()
    val index by vm.index.collectAsState()
    val query by vm.query.collectAsState()
    val category by vm.category.collectAsState()
    val categories by vm.categories.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val offline by vm.offline.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val onlyFavorites by vm.onlyFavorites.collectAsState()
    var permissionDialog by remember { mutableStateOf(false) }

    if (permissionDialog) InstallPermissionDialog { permissionDialog = false }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        TextField(
            value = query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
                }
            },
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )
        if (categories.size > 1 || favorites.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = category == null && !onlyFavorites, onClick = { vm.setCategory(null); vm.setOnlyFavorites(false) }, label = { Text(stringResource(R.string.category_all)) })
                }
                if (favorites.isNotEmpty()) {
                    item {
                        FilterChip(
                            selected = onlyFavorites,
                            onClick = { vm.setOnlyFavorites(!onlyFavorites) },
                            leadingIcon = { Icon(Icons.Rounded.Star, null) },
                            label = { Text(stringResource(R.string.favorites)) },
                        )
                    }
                }
                items(categories) { name ->
                    FilterChip(selected = category == name, onClick = { vm.setCategory(if (category == name) null else name) }, label = { Text(name) })
                }
            }
        }
        if (offline && index != null) {
            Text(
                stringResource(R.string.offline_cached),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            when {
                index == null && refreshing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                index == null -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.error_index), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = vm::refresh) { Text(stringResource(R.string.retry)) }
                }
                apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyMessage(stringResource(if (query.isBlank() && category == null && !onlyFavorites) R.string.empty_catalog else R.string.empty_search))
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        AppRow(app, vm, onOpen = { onOpenApp(app.packageName) }, onNeedPermission = { permissionDialog = true })
                    }
                }
            }
        }
    }
}
