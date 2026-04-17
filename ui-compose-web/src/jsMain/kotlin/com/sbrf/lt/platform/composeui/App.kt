package com.sbrf.lt.platform.composeui

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.navigation.currentComposeRoute
import com.sbrf.lt.platform.composeui.home.ComposeHomePage
import com.sbrf.lt.platform.composeui.module_editor.ComposeModuleEditorPage
import com.sbrf.lt.platform.composeui.module_editor.parseModuleEditorRoute
import com.sbrf.lt.platform.composeui.module_runs.ComposeModuleRunsPage
import com.sbrf.lt.platform.composeui.module_runs.parseModuleRunsRoute

@Composable
fun ComposeSpikeApp() {
    val route = currentComposeRoute()
    when (route.screen.lowercase()) {
        "module-editor" -> parseModuleEditorRoute(route.params)?.let {
            ComposeModuleEditorPage(it)
        } ?: ComposeHomePage()
        "module-runs" -> parseModuleRunsRoute(route.params)?.let {
            ComposeModuleRunsPage(it)
        } ?: ComposeHomePage()
        else -> ComposeHomePage()
    }
}
