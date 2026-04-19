package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun CommandGuardrail(
    analysis: SqlStatementAnalysis,
    strictSafetyEnabled: Boolean,
) {
    val cssClass = when {
        analysis.keyword == "SQL" -> "sql-guardrail sql-guardrail-neutral"
        strictSafetyEnabled && !analysis.readOnly -> "sql-guardrail sql-guardrail-danger"
        analysis.dangerous -> "sql-guardrail sql-guardrail-danger"
        !analysis.readOnly -> "sql-guardrail sql-guardrail-warning"
        else -> "sql-guardrail sql-guardrail-safe"
    }
    val text = when {
        analysis.keyword == "SQL" -> "Текущий запрос не определен. Введи SQL, чтобы UI показал тип команды и предупредил о потенциально опасных операциях."
        strictSafetyEnabled && !analysis.readOnly -> "Строгая защита включена. Команда ${analysis.keyword} будет заблокирована до отключения этого режима."
        analysis.dangerous -> "Команда ${analysis.keyword} считается потенциально опасной. Перед запуском перепроверь SQL и выбранные источники."
        !analysis.readOnly -> "Команда ${analysis.keyword} может изменить данные или структуру на выбранных источниках."
        else -> "Команда ${analysis.keyword} распознана как read-only."
    }
    Div({ classesFromString(cssClass) }) {
        Text(text)
    }
}

@Composable
internal fun SqlEditorIdeBlock(
    outlineItems: List<SqlScriptOutlineItem>,
    currentLine: Int,
    onJumpToLine: (Int) -> Unit,
) {
    Div({ classes("sql-query-library", "mb-3") }) {
        Div({ classes("sql-query-library-block") }) {
            Div({ classes("d-flex", "justify-content-between", "align-items-center", "gap-3", "mb-2") }) {
                SqlEditorMutedText("Script outline")
                SqlEditorMutedText("Statement-ов: ${outlineItems.size}")
            }
            if (outlineItems.isEmpty()) {
                SqlEditorMutedText("Введи SQL, чтобы получить карту statement-ов и быстро прыгать по строкам.")
            } else {
                Div({ classes("sql-script-outline") }) {
                    outlineItems.forEach { item ->
                        SqlEditorOutlineButton(
                            active = currentLine in item.startLine..item.endLine,
                            onClick = { onJumpToLine(item.startLine) },
                        ) {
                            Div({ classes("d-flex", "justify-content-between", "align-items-start", "gap-3", "w-100") }) {
                                Span({ classes("sql-script-outline-item-main") }) {
                                    Text("#${item.index} ${item.keyword} · строки ${item.startLine}-${item.endLine}")
                                }
                                StatementRiskBadge(item)
                            }
                            Span({ classes("sql-script-outline-item-preview") }) {
                                Text(item.preview)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatementRiskBadge(item: SqlScriptOutlineItem) {
    val cssClass = when {
        item.keyword == "SQL" -> "sql-statement-risk-badge sql-statement-risk-neutral"
        item.dangerous -> "sql-statement-risk-badge sql-statement-risk-danger"
        !item.readOnly -> "sql-statement-risk-badge sql-statement-risk-warning"
        else -> "sql-statement-risk-badge sql-statement-risk-safe"
    }
    val text = when {
        item.keyword == "SQL" -> "SQL"
        item.dangerous -> "Опасно"
        !item.readOnly -> "Меняет данные"
        else -> "Read-only"
    }
    Span({ classesFromString(cssClass) }) { Text(text) }
}

@Composable
internal fun StatementSelectionBlock(
    statementResults: List<SqlConsoleStatementResult>,
    selectedStatementIndex: Int,
    onSelectStatement: (Int) -> Unit,
) {
    if (statementResults.size <= 1) {
        return
    }

    Div({ classes("sql-query-library", "mb-3") }) {
        SqlEditorMutedText("Скрипт содержит ${statementResults.size} statement-ов. Выбери statement, для которого показывать данные, статусы и экспорт.", "mb-2")
        Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
            statementResults.forEachIndexed { index, statement ->
                SqlEditorStatementButton(
                    active = index == selectedStatementIndex,
                    onClick = { onSelectStatement(index) },
                ) {
                    Text("#${index + 1} ${statement.statementKeyword}")
                }
            }
        }
    }
}

@Composable
private fun SqlEditorMutedText(
    text: String,
    marginClass: String = "",
) {
    Div({
        classes("small", "text-secondary")
        if (marginClass.isNotBlank()) {
            classes(marginClass)
        }
    }) {
        Text(text)
    }
}

@Composable
private fun SqlEditorOutlineButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Button(attrs = {
        classes(
            "btn",
            "btn-sm",
            "sql-script-outline-item",
            if (active) "btn-dark" else "btn-outline-secondary",
        )
        attr("type", "button")
        onClick { onClick() }
    }) {
        content()
    }
}

@Composable
private fun SqlEditorStatementButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Button(attrs = {
        classes(
            "btn",
            "btn-sm",
            if (active) "btn-dark" else "btn-outline-secondary",
        )
        attr("type", "button")
        onClick { onClick() }
    }) {
        content()
    }
}
