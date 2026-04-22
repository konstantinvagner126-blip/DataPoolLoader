package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleHeroArt() {
    Div({ classes("sql-console-stage") }) {
        Div({ classes("sql-console-node", "sql-console-node-sources") }) { Text("SOURCES") }
        Div({ classes("sql-console-node", "sql-console-node-check") }) { Text("CHECK") }
        Div({ classes("sql-console-node", "sql-console-node-sql") }) { Text("SQL") }

        Div({ classes("sql-console-line", "sql-console-line-left-top") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-left-top") })
        }
        Div({ classes("sql-console-line", "sql-console-line-left-middle") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-left-middle") })
        }
        Div({ classes("sql-console-line", "sql-console-line-left-bottom") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-left-bottom") })
        }

        Div({ classes("sql-console-hub") }) {
            Div({ classes("merge-title") }) { Text("QUERY") }
            Div({ classes("merge-subtitle") }) { Text("RUNNER") }
        }

        Div({ classes("sql-console-line", "sql-console-line-right-top") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-right-top") })
        }
        Div({ classes("sql-console-line", "sql-console-line-right-bottom") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-right-bottom") })
        }

        Div({ classes("sql-console-node", "sql-console-node-results") }) { Text("RESULTS") }
        Div({ classes("sql-console-node", "sql-console-node-status") }) { Text("STATUS") }
    }
}
