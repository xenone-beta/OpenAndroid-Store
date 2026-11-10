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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import dev.openandroid.store.core.Catalog
import dev.openandroid.store.core.StoreApp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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

    val browsing = query.isBlank() && category == null && !onlyFavorites
    val all = index?.apps.orEmpty()

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StoreLogo(48.dp)
            Spacer(Modifier.width(10.dp))
            TextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1) },
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
        }
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
                index == null && refreshing -> SkeletonGrid()
                index == null -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.error_index), style = MaterialTheme.typography.bodyLarge)
                    val detail by vm.error.collectAsState()
                    detail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                    TextButton(onClick = vm::refresh) { Text(stringResource(R.string.retry)) }
                }
                apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyMessage(stringResource(if (browsing) R.string.empty_catalog else R.string.empty_search))
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (browsing && all.size >= 2) {
                        val popular = Catalog.popular(all)
                        if (popular.isNotEmpty()) feed(R.string.feed_popular, popular, { stringResource(R.string.stars_count, it.stars) }, onOpenApp)
                        feed(R.string.feed_new, Catalog.newest(all), { it.latestFor(Int.MAX_VALUE)?.versionName.orEmpty() }, onOpenApp)
                        feed(R.string.feed_updated, Catalog.recentlyUpdated(all), { it.latestFor(Int.MAX_VALUE)?.versionName.orEmpty() }, onOpenApp)
                        item(span = { GridItemSpan(maxLineSpan) }, key = "title:all") {
                            SectionTitle(stringResource(R.string.all_apps), horizontal = 0.dp)
                        }
                    }
                    gridItemsIndexed(apps, key = { _, app -> app.packageName }) { i, app ->
                        AppCard(
                            app, vm,
                            onOpen = { onOpenApp(app.packageName) },
                            onNeedPermission = { permissionDialog = true },
                            modifier = Modifier.animateItem().appear("grid:" + app.packageName, i),
                        )
                    }
                }
            }
        }
    }
}

/** A titled horizontal row of small tiles that spans the full grid width. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.feed(
    title: Int,
    apps: List<StoreApp>,
    subtitle: @Composable (StoreApp) -> String,
    onOpenApp: (String) -> Unit,
) {
    if (apps.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }, key = "feed:$title") {
        Column {
            SectionTitle(stringResource(title), horizontal = 0.dp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { i, app ->
                    FeedCard(app, subtitle(app), onOpen = { onOpenApp(app.packageName) }, modifier = Modifier.appear("feed:$title:" + app.packageName, i))
                }
            }
        }
    }
}
