package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeSqlConsoleObjectsPage(
    initialParams: Map<String, String> = emptyMap(),
    api: SqlConsoleApi = remember { SqlConsoleApiClient() },
) {
    val store = remember(api) { SqlConsoleObjectsStore(api) }
    var state by remember { mutableStateOf(SqlConsoleObjectsPageState()) }
    val initialQuery = initialParams["query"]?.trim().orEmpty()
        .ifBlank { initialParams["object"]?.trim().orEmpty() }
    val initialSource = initialParams["source"]?.trim().orEmpty()
    val navigationTarget = remember(initialParams) {
        initialParams["object"]?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { objectName ->
                SqlObjectNavigationTarget(
                    sourceName = initialParams["source"]?.trim().orEmpty(),
                    schemaName = initialParams["schema"]?.trim().orEmpty(),
                    objectName = objectName,
                    objectType = initialParams["type"]?.trim().orEmpty(),
                )
            }
    }
    val callbacks = sqlConsoleObjectsPageCallbacks(
        store = store,
        scope = rememberCoroutineScope(),
        currentState = { state },
        setState = { state = it },
    )

    SqlConsoleObjectsPageEffects(
        store = store,
        initialQuery = initialQuery,
        initialSource = initialSource,
        currentState = { state },
        setState = { state = it },
    )

    PageScaffold(
        eyebrow = "MLP Platform",
        title = "Объекты БД",
        subtitle = "Search-first просмотр таблиц, индексов и представлений по источникам SQL-консоли без полной загрузки большого каталога.",
        heroClassNames = listOf("hero-card-compact", "sql-console-hero"),
        heroCopyClassNames = listOf("sql-console-hero-copy"),
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                ObjectsNavActionButton("На главную", hrefValue = "/")
                ObjectsNavActionButton("SQL-консоль", hrefValue = "/sql-console")
                ObjectsNavActionButton("Объекты БД", active = true)
            }
        },
        content = {
            SqlConsoleObjectsPageContent(
                state = state,
                navigationTarget = navigationTarget,
                callbacks = callbacks,
            )
        },
    )
}
