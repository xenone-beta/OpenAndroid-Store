package dev.openandroid.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.openandroid.store.R
import dev.openandroid.store.StoreViewModel
import dev.openandroid.store.core.InstallState
import dev.openandroid.store.core.StoreApp
import dev.openandroid.store.install.FailReason
import dev.openandroid.store.install.Transfer

/** App icon from the catalog, or a tinted letter tile while it loads or when there is none. */
@Composable
fun AppIcon(app: StoreApp, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            app.name.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        app.iconUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
fun failMessage(failure: Transfer.Failed): String = when (failure.reason) {
    FailReason.DOWNLOAD -> stringResource(R.string.error_download)
    FailReason.HASH -> stringResource(R.string.error_hash)
    FailReason.WRONG_PACKAGE -> stringResource(R.string.error_package)
    FailReason.WRONG_SIGNER -> stringResource(R.string.error_signer)
    FailReason.INSTALL -> stringResource(R.string.error_install)
    FailReason.NEEDS_PERMISSION -> stringResource(R.string.error_permission)
}

/** One catalog row: icon, texts, and a button that matches the app's state. */
@Composable
fun AppRow(app: StoreApp, vm: StoreViewModel, onOpen: () -> Unit, onNeedPermission: () -> Unit) {
    val installed by vm.installed.collectAsState()
    val transfers by vm.installs.transfers.collectAsState()
    val state = vm.stateOf(app, installed)
    val transfer = transfers[app.packageName]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(app, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.summary.ifEmpty { app.author },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (transfer) {
                is Transfer.Downloading -> ProgressLine(transfer.fraction)
                Transfer.Verifying, Transfer.Installing -> ProgressLine(-1f)
                is Transfer.Failed -> Text(
                    failMessage(transfer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
                null -> Unit
            }
        }
        ActionButton(app, state, transfer, vm, compact = true, onNeedPermission = onNeedPermission)
    }
}

@Composable
private fun ProgressLine(fraction: Float) {
    val modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    if (fraction < 0f) LinearProgressIndicator(modifier = modifier)
    else LinearProgressIndicator(progress = { fraction }, modifier = modifier)
}

/** Install, Update, Open or nothing, depending on the app's state. Shared by rows and the detail screen. */
@Composable
fun ActionButton(
    app: StoreApp,
    state: InstallState,
    transfer: Transfer?,
    vm: StoreViewModel,
    compact: Boolean,
    onNeedPermission: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val busy = transfer is Transfer.Downloading || transfer == Transfer.Verifying || transfer == Transfer.Installing
    if (busy) return
    val start = {
        if (!vm.installs.canInstall()) onNeedPermission() else { vm.installs.dismiss(app.packageName); vm.requestInstall(app) }
    }
    when (state) {
        InstallState.NOT_INSTALLED -> FilledTonalButton(onClick = start) { Text(stringResource(R.string.action_install)) }
        InstallState.UPDATE_AVAILABLE -> Button(onClick = start) { Text(stringResource(R.string.action_update)) }
        InstallState.INSTALLED -> if (vm.canOpen(app.packageName)) {
            OutlinedButton(onClick = {
                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { runCatching { context.startActivity(it) } }
            }) { Text(stringResource(R.string.action_open)) }
        } else if (compact) {
            Text(stringResource(R.string.state_installed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InstallState.SIGNATURE_MISMATCH -> if (compact) {
            Text(stringResource(R.string.state_signature), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        InstallState.INCOMPATIBLE -> if (compact) {
            Text(stringResource(R.string.state_incompatible), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Asks the user to allow installs from this app, then opens the system settings page for it. */
@Composable
fun InstallPermissionDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_title)) },
        text = { Text(stringResource(R.string.permission_body)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${context.packageName}"),
                )
                runCatching { context.startActivity(intent) }
            }) { Text(stringResource(R.string.permission_open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
