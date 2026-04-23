package com.sbrf.lt.platform.composeui

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.about.ComposeAboutPage
import com.sbrf.lt.platform.composeui.foundation.navigation.currentComposeRoute
import com.sbrf.lt.platform.composeui.home.ComposeHomePage
import com.sbrf.lt.platform.composeui.module_editor.ComposeModuleEditorPage
import com.sbrf.lt.platform.composeui.module_editor.parseModuleEditorRoute
import com.sbrf.lt.platform.composeui.module_sync.ComposeModuleSyncPage
import com.sbrf.lt.platform.composeui.module_runs.ComposeModuleRunsPage
import com.sbrf.lt.platform.composeui.module_runs.parseModuleRunsRoute
import com.sbrf.lt.platform.composeui.run_history_cleanup.ComposeRunHistoryCleanupPage
import com.sbrf.lt.platform.composeui.sql_console.ComposeSqlConsolePage
import com.sbrf.lt.platform.composeui.sql_console.ComposeSqlConsoleObjectsPage

@Composable
fun ComposeSpikeApp() {
    val route = currentComposeRoute()
    when (route.screen.lowercase()) {
        "module-editor" -> parseModuleEditorRoute(route.params)?.let {
            ComposeModuleEditorPage(it)
        } ?: ComposeHomePage()
        "module-sync" -> ComposeModuleSyncPage()
        "module-runs" -> parseModuleRunsRoute(route.params)?.let {
            ComposeModuleRunsPage(it)
        } ?: ComposeHomePage()
        "run-history-cleanup" -> ComposeRunHistoryCleanupPage()
        "about" -> ComposeAboutPage()
        "sql-console" -> ComposeSqlConsolePage()
        "sql-console-objects" -> ComposeSqlConsoleObjectsPage(route.params)
        else -> ComposeHomePage()
    }
}
