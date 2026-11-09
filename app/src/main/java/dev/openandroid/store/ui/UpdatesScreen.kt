package dev.openandroid.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openandroid.store.R
import dev.openandroid.store.StoreViewModel
import dev.openandroid.store.install.Transfer

@Composable
fun UpdatesScreen(vm: StoreViewModel, modifier: Modifier, onOpenApp: (String) -> Unit) {
    val updates by vm.updates.collectAsState()
    val upToDate by vm.upToDate.collectAsState()
    val index by vm.index.collectAsState()
    val transfers by vm.installs.transfers.collectAsState()
    val active = index?.apps.orEmpty().filter {
        val t = transfers[it.packageName]
        t is Transfer.Downloading || t == Transfer.Verifying || t == Transfer.Installing
    }
    var permissionDialog by remember { mutableStateOf(false) }
    if (permissionDialog) InstallPermissionDialog { permissionDialog = false }

    LazyColumn(modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.tab_updates), style = MaterialTheme.typography.headlineSmall)
                if (updates.size > 1) {
                    Button(onClick = { if (vm.installs.canInstall()) vm.updateAll() else permissionDialog = true }) {
                        Text(stringResource(R.string.action_update_all))
                    }
                }
            }
        }
        if (active.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.downloads_title)) }
            items(active, key = { "d:" + it.packageName }) { app ->
                AppRow(app, vm, onOpen = { onOpenApp(app.packageName) }, onNeedPermission = { permissionDialog = true })
            }
        }
        if (updates.isEmpty()) {
            item { EmptyMessage(stringResource(R.string.updates_none)) }
        } else {
            item { SectionTitle(pluralStringResource(R.plurals.updates_available, updates.size, updates.size)) }
            items(updates, key = { "u:" + it.packageName }) { app ->
                AppRow(app, vm, onOpen = { onOpenApp(app.packageName) }, onNeedPermission = { permissionDialog = true })
            }
        }
        if (upToDate.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.updates_installed)) }
            items(upToDate, key = { "i:" + it.packageName }) { app ->
                AppRow(app, vm, onOpen = { onOpenApp(app.packageName) }, onNeedPermission = { permissionDialog = true })
            }
        }
    }
}
