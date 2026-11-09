package dev.openandroid.store.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import dev.openandroid.store.core.Catalog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.openandroid.store.MainActivity
import dev.openandroid.store.R
import dev.openandroid.store.StoreViewModel

@Composable
fun StoreRoot(vm: StoreViewModel, requestedTab: Int) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val prompt by vm.permissionPrompt.collectAsState()
    prompt?.let { p ->
        AlertDialog(
            onDismissRequest = vm::dismissPrompt,
            title = { Text(stringResource(R.string.permission_new_title, p.app.name)) },
            text = {
                Column {
                    Text(stringResource(R.string.permission_new_body))
                    Text(
                        p.added.joinToString("\n") { "• " + Catalog.permissionLabel(it) },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmPrompt) { Text(stringResource(R.string.action_update)) } },
            dismissButton = { TextButton(onClick = vm::dismissPrompt) { Text(stringResource(R.string.cancel)) } },
        )
    }
    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { slideInHorizontally(tween(280)) { it / 8 } + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(280)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 8 } + fadeOut(tween(240)) },
    ) {
        composable("home") {
            HomeScreen(vm, requestedTab, onOpenApp = { nav.navigate("app/$it") }, onOpenAbout = { nav.navigate("about") })
        }
        composable(
            "app/{pkg}",
            arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
        ) { entry ->
            DetailScreen(entry.arguments?.getString("pkg").orEmpty(), vm, onBack = back)
        }
        composable("about") { AboutScreen(onBack = back) }
    }
}

@Composable
private fun HomeScreen(vm: StoreViewModel, requestedTab: Int, onOpenApp: (String) -> Unit, onOpenAbout: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(MainActivity.TAB_CATALOG) }
    LaunchedEffect(requestedTab) { tab = requestedTab }
    val updates by vm.updates.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainActivity.TAB_CATALOG,
                    onClick = { tab = MainActivity.TAB_CATALOG },
                    icon = { Icon(Icons.Rounded.Apps, null) },
                    label = { Text(stringResource(R.string.tab_catalog)) },
                )
                NavigationBarItem(
                    selected = tab == MainActivity.TAB_UPDATES,
                    onClick = { tab = MainActivity.TAB_UPDATES },
                    icon = {
                        BadgedBox(badge = { if (updates.isNotEmpty()) Badge { Text(updates.size.toString()) } }) {
                            Icon(Icons.Rounded.SystemUpdate, null)
                        }
                    },
                    label = { Text(stringResource(R.string.tab_updates)) },
                )
                NavigationBarItem(
                    selected = tab == MainActivity.TAB_SETTINGS,
                    onClick = { tab = MainActivity.TAB_SETTINGS },
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            MainActivity.TAB_CATALOG -> CatalogScreen(vm, content, onOpenApp)
            MainActivity.TAB_UPDATES -> UpdatesScreen(vm, content, onOpenApp)
            else -> SettingsScreen(vm, content, onOpenAbout)
        }
    }
}
