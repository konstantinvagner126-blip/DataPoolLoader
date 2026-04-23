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

internal fun registerSqlConsoleEditorShortcuts(
    editor: dynamic,
    onRun: () -> Unit,
    onRunCurrent: () -> Unit,
    onFormat: () -> Unit,
    onStop: () -> Unit,
) {
    val monaco = window.asDynamic().monaco ?: return
    val ctrlEnter = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyCode.Enter as Int)
    val ctrlShiftEnter = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Shift as Int) or (monaco.KeyCode.Enter as Int)
    val shiftAltF = (monaco.KeyMod.Shift as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.KeyF as Int)
    val escape = monaco.KeyCode.Escape as Int
    editor.addCommand(ctrlEnter) { onRun() }
    editor.addCommand(ctrlShiftEnter) { onRunCurrent() }
    editor.addCommand(shiftAltF) { onFormat() }
    editor.addCommand(escape) { onStop() }
}
