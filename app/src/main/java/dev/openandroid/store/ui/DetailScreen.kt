@file:OptIn(ExperimentalMaterial3Api::class)

package dev.openandroid.store.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openandroid.store.R
import dev.openandroid.store.StoreViewModel
import dev.openandroid.store.core.AppVersion
import dev.openandroid.store.core.Catalog
import dev.openandroid.store.core.InstallState
import dev.openandroid.store.core.StoreApp
import dev.openandroid.store.install.Transfer

@Composable
fun DetailScreen(packageName: String, vm: StoreViewModel, onBack: () -> Unit) {
    val index by vm.index.collectAsState()
    val installed by vm.installed.collectAsState()
    val transfers by vm.installs.transfers.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val app = remember(index, packageName) { index?.apps?.firstOrNull { it.packageName == packageName } }
    var permissionDialog by remember { mutableStateOf(false) }
    if (permissionDialog) InstallPermissionDialog { permissionDialog = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    val favorite = packageName in favorites
                    IconButton(onClick = { vm.toggleFavorite(packageName) }) {
                        Icon(
                            if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (app == null) {
            EmptyMessage(stringResource(R.string.app_not_found), Modifier.padding(padding))
            return@Scaffold
        }
        val state = vm.stateOf(app, installed)
        val transfer = transfers[app.packageName]
        val latest = app.latestFor(Build.VERSION.SDK_INT) ?: app.versions.firstOrNull()

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon(app, 80.dp)
                Column {
                    Text(app.name, style = MaterialTheme.typography.headlineSmall)
                    if (app.author.isNotEmpty()) {
                        Text(app.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        listOf(app.category, app.license).filter { it.isNotEmpty() }.joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (latest != null && latest.signer.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                VerifiedBadge(matchesInstalled = installed[app.packageName]?.signer.equals(latest.signer, ignoreCase = true))
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(app, state, transfer, vm, compact = false, onNeedPermission = { permissionDialog = true })
                if (state != InstallState.NOT_INSTALLED && state != InstallState.INCOMPATIBLE) UninstallButton(app)
            }
            when (transfer) {
                is Transfer.Downloading -> {
                    Progress(stringResource(R.string.status_downloading), transfer.fraction)
                    androidx.compose.material3.TextButton(onClick = { vm.installs.cancel(app.packageName) }) { Text(stringResource(R.string.cancel)) }
                }
                Transfer.Verifying -> {
                    Progress(stringResource(R.string.status_verifying), -1f)
                    androidx.compose.material3.TextButton(onClick = { vm.installs.cancel(app.packageName) }) { Text(stringResource(R.string.cancel)) }
                }
                Transfer.Installing -> Progress(stringResource(R.string.status_installing), -1f)
                is Transfer.Failed -> Text(
                    failMessage(transfer) + if (transfer.detail.isNotBlank()) " (${transfer.detail})" else "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                null -> Unit
            }
            when (state) {
                InstallState.SIGNATURE_MISMATCH -> Note(stringResource(R.string.note_signature), error = true)
                InstallState.INCOMPATIBLE -> Note(stringResource(R.string.note_incompatible), error = true)
                else -> Unit
            }

            if (app.screenshots.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(app.screenshots) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = stringResource(R.string.screenshot),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(260.dp)
                                .aspectRatio(0.5f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
            }

            if (app.summary.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(app.summary, style = MaterialTheme.typography.titleMedium)
            }
            if (app.description.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(app.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (latest != null) {
                Spacer(Modifier.height(16.dp))
                if (latest.changelog.isNotBlank()) {
                    InfoCard(stringResource(R.string.whats_new, latest.versionName)) {
                        Text(latest.changelog.trim(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                InfoCard(stringResource(R.string.information)) {
                    InfoRow(stringResource(R.string.info_version), "${latest.versionName} (${latest.versionCode})")
                    Catalog.formatSize(latest.size).takeIf { it.isNotEmpty() }?.let { InfoRow(stringResource(R.string.info_size), it) }
                    InfoRow(stringResource(R.string.info_min_android), "API ${latest.minSdk}")
                    if (latest.publishedAt.length >= 10) InfoRow(stringResource(R.string.info_updated), latest.publishedAt.take(10))
                    if (latest.signer.isNotEmpty()) InfoRow(stringResource(R.string.info_signer), Catalog.shortDigest(latest.signer))
                    InfoRow(stringResource(R.string.info_package), app.packageName)
                }
                InfoCard(stringResource(R.string.permissions)) { Permissions(latest) }
                if (app.versions.size > 1) VersionsCard(app)
            }

            val uriHandler = LocalUriHandler.current
            val issuesUrl = app.issues ?: app.repo.takeIf { it.startsWith("https://github.com/") }?.let { "$it/issues" }
            if (app.repo.isNotEmpty()) LinkButton(Icons.Rounded.Code, stringResource(R.string.source_code)) { uriHandler.openUri(app.repo) }
            app.website?.let { LinkButton(Icons.Rounded.Language, stringResource(R.string.website)) { uriHandler.openUri(it) } }
            issuesUrl?.let { LinkButton(Icons.Rounded.BugReport, stringResource(R.string.report_problem)) { uriHandler.openUri(it) } }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LinkButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Icon(icon, null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Shown when the catalog has checked the APK's signature; says so when it also matches the installed copy. */
@Composable
private fun VerifiedBadge(matchesInstalled: Boolean) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Text(
                stringResource(if (matchesInstalled) R.string.verified_installed else R.string.verified),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun UninstallButton(app: StoreApp) {
    val context = LocalContext.current
    TextButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))) }
    }) { Text(stringResource(R.string.action_uninstall)) }
}

@Composable
private fun Progress(label: String, fraction: Float) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        if (fraction < 0f) LinearProgressIndicator(modifier = modifier) else LinearProgressIndicator(progress = { fraction }, modifier = modifier)
    }
}

@Composable
private fun Note(text: String, error: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(horizontal = 8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Permissions(version: AppVersion) {
    if (version.permissions.isEmpty()) {
        Text(stringResource(R.string.permissions_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            version.permissions.forEach { AssistChip(onClick = {}, label = { Text(Catalog.permissionLabel(it)) }) }
        }
    }
}

@Composable
private fun VersionsCard(app: StoreApp) {
    var showAll by remember { mutableStateOf(false) }
    val older = app.versions.drop(1)
    InfoCard(stringResource(R.string.previous_versions)) {
        older.take(if (showAll) older.size else 3).forEach { v ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(
                    "${v.versionName} • ${v.publishedAt.take(10)}".trimEnd(' ', '•'),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (v.changelog.isNotBlank()) {
                    Text(v.changelog.trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6)
                }
            }
        }
        if (older.size > 3) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(stringResource(if (showAll) R.string.show_less else R.string.show_all))
            }
        }
    }
}
