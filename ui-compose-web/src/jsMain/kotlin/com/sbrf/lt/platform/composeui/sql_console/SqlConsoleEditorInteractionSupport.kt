package com.sbrf.lt.platform.composeui.sql_console

import kotlinx.browser.window

internal fun insertSqlText(
    editor: dynamic,
    text: String,
    currentValue: String,
    onFallback: (String) -> Unit,
) {
    if (editor == null || editor.executeEdits == undefined) {
        onFallback(appendSqlText(currentValue, text))
        return
    }
    val edit = js("{}")
    edit.range = editor.getSelection()
    edit.text = text
    edit.forceMoveMarkers = true
    editor.executeEdits("compose-sql-console-favorites", arrayOf(edit))
    editor.focus()
}

internal fun appendSqlText(
    currentValue: String,
    text: String,
): String =
    when {
        currentValue.isBlank() -> text
        currentValue.last().isWhitespace() -> currentValue + text
        else -> "$currentValue $text"
    }

internal data class SqlConsoleEditorSelectionSnapshot(
    val sql: String,
    val lineCount: Int,
)

internal fun readSqlEditorSelection(editor: dynamic): SqlConsoleEditorSelectionSnapshot {
    if (editor == null || editor.getSelection == undefined || editor.getModel == undefined) {
        return SqlConsoleEditorSelectionSnapshot(sql = "", lineCount = 0)
    }
    val selection = editor.getSelection() ?: return SqlConsoleEditorSelectionSnapshot(sql = "", lineCount = 0)
    val isEmptySelection = runCatching { selection.isEmpty() as Boolean }.getOrDefault(false)
    if (isEmptySelection) {
        return SqlConsoleEditorSelectionSnapshot(sql = "", lineCount = 0)
    }
    val selectedSql = runCatching { editor.getModel().getValueInRange(selection) as String }.getOrDefault("").trim()
    return SqlConsoleEditorSelectionSnapshot(
        sql = selectedSql,
        lineCount = selectedSql.lineSequence().count { it.isNotBlank() }.coerceAtLeast(if (selectedSql.isBlank()) 0 else 1),
    )
}

internal fun registerSqlConsoleEditorShortcuts(
    editor: dynamic,
    onRunCurrent: () -> Unit,
    onRunSelection: () -> Unit,
    onRunAll: () -> Unit,
    onFormat: () -> Unit,
    onStop: () -> Unit,
    onShowData: () -> Unit,
    onShowStatus: () -> Unit,
    onPreviousStatement: () -> Unit,
    onNextStatement: () -> Unit,
    onPreviousShard: () -> Unit,
    onNextShard: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    val monaco = window.asDynamic().monaco ?: return
    val ctrlEnter = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyCode.Enter as Int)
    val ctrlShiftEnter = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Shift as Int) or (monaco.KeyCode.Enter as Int)
    val ctrlAltEnter = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.Enter as Int)
    val shiftAltF = (monaco.KeyMod.Shift as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.KeyF as Int)
    val escape = monaco.KeyCode.Escape as Int
    val ctrlAltDigit1 = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.Digit1 as Int)
    val ctrlAltDigit2 = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.Digit2 as Int)
    val ctrlAltUp = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.UpArrow as Int)
    val ctrlAltDown = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.DownArrow as Int)
    val ctrlAltLeft = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.LeftArrow as Int)
    val ctrlAltRight = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.RightArrow as Int)
    val ctrlAltPageUp = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.PageUp as Int)
    val ctrlAltPageDown = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.PageDown as Int)
    editor.addCommand(ctrlEnter) { onRunCurrent() }
    editor.addCommand(ctrlShiftEnter) { onRunSelection() }
    editor.addCommand(ctrlAltEnter) { onRunAll() }
    editor.addCommand(shiftAltF) { onFormat() }
    editor.addCommand(escape) { onStop() }
    editor.addCommand(ctrlAltDigit1) { onShowData() }
    editor.addCommand(ctrlAltDigit2) { onShowStatus() }
    editor.addCommand(ctrlAltUp) { onPreviousStatement() }
    editor.addCommand(ctrlAltDown) { onNextStatement() }
    editor.addCommand(ctrlAltLeft) { onPreviousShard() }
    editor.addCommand(ctrlAltRight) { onNextShard() }
    editor.addCommand(ctrlAltPageUp) { onPreviousPage() }
    editor.addCommand(ctrlAltPageDown) { onNextPage() }
}
