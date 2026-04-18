package com.sbrf.lt.platform.composeui.foundation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLScriptElement
import org.jetbrains.compose.web.dom.Div

private var monacoEditorCounter = 0
private const val MONACO_LOADER_URL = "/static/compose-spike/vendor/monaco-editor/min/vs/loader.js"
private const val MONACO_HELPER_URL = "/static/compose-spike/compose-monaco.js?v=20260418a"

@Composable
fun MonacoEditorPane(
    instanceKey: String,
    language: String,
    value: String,
    readOnly: Boolean = false,
    classNames: List<String> = listOf("editor-frame"),
    onValueChange: (String) -> Unit = {},
) {
    val elementId = remember(instanceKey) {
        monacoEditorCounter += 1
        "compose-monaco-$monacoEditorCounter"
    }
    var editorRef by remember(instanceKey) { mutableStateOf<dynamic>(null) }

    Div(attrs = {
        attr("id", elementId)
        classes(*classNames.toTypedArray())
    })

    DisposableEffect(elementId, language, readOnly) {
        var disposed = false
        ensureMonacoReady {
            val composeMonaco = window.asDynamic().ComposeMonaco
            if (composeMonaco == null || composeMonaco.withMonacoReady == undefined) {
                return@ensureMonacoReady
            }
            composeMonaco.withMonacoReady {
                if (!disposed) {
                    val options = js("{}")
                    options.value = value
                    options.language = language
                    options.readOnly = readOnly
                    val editor = composeMonaco.createMonacoEditor(elementId, options)
                    editor.onDidChangeModelContent {
                        onValueChange(editor.getValue() as String)
                    }
                    editorRef = editor
                }
            }
        }
        onDispose {
            disposed = true
            editorRef?.dispose()
            editorRef = null
        }
    }

    LaunchedEffect(editorRef, value) {
        val editor = editorRef ?: return@LaunchedEffect
        val currentValue = editor.getValue() as String
        if (currentValue != value) {
            editor.setValue(value)
        }
    }
}

private fun ensureMonacoReady(onReady: () -> Unit) {
    val global = window.asDynamic()
    if (global.monaco?.editor != undefined && global.ComposeMonaco?.withMonacoReady != undefined) {
        onReady()
        return
    }

    val callbacks = when (val existing = global.__composeMonacoCallbacks) {
        null, undefined -> {
            val created = js("[]")
            global.__composeMonacoCallbacks = created
            created
        }
        else -> existing
    }
    callbacks.push(onReady)
    if (global.__composeMonacoLoading == true) {
        return
    }
    global.__composeMonacoLoading = true

    loadScriptOnce(MONACO_LOADER_URL, "__composeMonacoLoaderLoaded") {
        loadScriptOnce(MONACO_HELPER_URL, "__composeMonacoHelperLoaded") {
            val composeMonaco = global.ComposeMonaco
            if (composeMonaco == null || composeMonaco.withMonacoReady == undefined) {
                global.__composeMonacoLoading = false
                return@loadScriptOnce
            }
            composeMonaco.withMonacoReady {
                global.__composeMonacoLoading = false
                val pending = global.__composeMonacoCallbacks
                global.__composeMonacoCallbacks = js("[]")
                val size = pending.length as Int
                for (index in 0 until size) {
                    val callback = pending[index] as () -> Unit
                    callback()
                }
            }
        }
    }
}

private fun loadScriptOnce(
    src: String,
    loadedFlag: String,
    onLoad: () -> Unit,
) {
    val global = window.asDynamic()
    if (global[loadedFlag] == true) {
        onLoad()
        return
    }
    val existing = document.querySelector("script[data-compose-src='$src']") as? HTMLScriptElement
    if (existing != null) {
        existing.addEventListener("load", { onLoad() })
        return
    }

    val script = document.createElement("script") as HTMLScriptElement
    script.src = src
    script.async = true
    script.setAttribute("data-compose-src", src)
    script.addEventListener("load", {
        global[loadedFlag] = true
        onLoad()
    })
    document.body?.appendChild(script)
}
