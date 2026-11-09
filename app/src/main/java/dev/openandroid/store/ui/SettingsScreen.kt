package dev.openandroid.store.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.openandroid.store.R
import dev.openandroid.store.StoreViewModel
import dev.openandroid.store.data.CHECK_INTERVALS
import dev.openandroid.store.data.StoreSettings
import dev.openandroid.store.data.ThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: StoreViewModel, modifier: Modifier, onOpenAbout: () -> Unit) {
    val settings = vm.settings
    val context = LocalContext.current
    val theme by settings.themeMode.collectAsState()
    val dynamic by settings.dynamicColor.collectAsState()
    val hours by settings.checkHours.collectAsState()
    val indexUrl by settings.indexUrl.collectAsState()
    var urlDialog by remember { mutableStateOf(false) }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    if (urlDialog) {
        var text by remember { mutableStateOf(indexUrl) }
        val valid = StoreSettings.isValidIndexUrl(text.trim())
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text(stringResource(R.string.settings_index)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        isError = !valid,
                        supportingText = { Text(stringResource(R.string.settings_index_hint)) },
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = { settings.setIndexUrl(text); urlDialog = false; vm.refresh() }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { settings.resetIndexUrl(); urlDialog = false; vm.refresh() }) { Text(stringResource(R.string.reset)) }
                    TextButton(onClick = { urlDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

    Column(modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))

        SectionTitle(stringResource(R.string.settings_appearance))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeMode.SYSTEM to R.string.theme_system,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.DARK to R.string.theme_dark,
                ThemeMode.AMOLED to R.string.theme_amoled,
            ).forEach { (mode, label) ->
                FilterChip(selected = theme == mode, onClick = { settings.setThemeMode(mode) }, label = { Text(stringResource(label)) })
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListItem(
                modifier = Modifier.clickable { settings.setDynamicColor(!dynamic) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_dynamic)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_hint)) },
                trailingContent = { Switch(checked = dynamic, onCheckedChange = settings::setDynamicColor) },
            )
        }

        SectionTitle(stringResource(R.string.settings_updates))
        Text(
            stringResource(R.string.settings_check_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CHECK_INTERVALS.forEach { h ->
                FilterChip(
                    selected = hours == h,
                    onClick = {
                        vm.setCheckHours(h)
                        val needsAsk = h > 0 && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsAsk) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    label = { Text(if (h == 0) stringResource(R.string.interval_off) else stringResource(R.string.interval_hours, h)) },
                )
            }
        }

        SectionTitle(stringResource(R.string.settings_catalog))
        ListItem(
            modifier = Modifier.clickable { urlDialog = true },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(R.string.settings_index)) },
            supportingContent = { Text(indexUrl, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        )

        SectionTitle(stringResource(R.string.settings_about))
        ListItem(
            modifier = Modifier.clickable(onClick = onOpenAbout),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(R.string.settings_about_row)) },
            supportingContent = { Text(stringResource(R.string.settings_about_desc)) },
        )
    }
}
