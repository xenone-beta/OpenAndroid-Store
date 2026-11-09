package dev.openandroid.store

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import dev.openandroid.store.ui.StoreRoot
import dev.openandroid.store.ui.StoreTheme

class MainActivity : ComponentActivity() {
    private val vm: StoreViewModel by viewModels()
    private val requestedTab = mutableIntStateOf(TAB_CATALOG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            val mode by vm.settings.themeMode.collectAsState()
            val dynamic by vm.settings.dynamicColor.collectAsState()
            StoreTheme(mode, dynamic) {
                StoreRoot(vm, requestedTab.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // Apps may have been installed or removed outside the store.
        vm.refreshInstalled()
    }

    private fun handle(intent: Intent?) {
        intent?.getIntExtra(EXTRA_TAB, -1)?.takeIf { it >= 0 }?.let { requestedTab.intValue = it }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_CATALOG = 0
        const val TAB_UPDATES = 1
        const val TAB_SETTINGS = 2
    }
}
