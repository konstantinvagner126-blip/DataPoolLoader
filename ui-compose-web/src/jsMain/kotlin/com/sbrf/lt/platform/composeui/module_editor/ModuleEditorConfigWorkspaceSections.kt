package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane

@Composable
internal fun ConfigPreview(
    configText: String,
    onConfigChange: (String) -> Unit,
) {
    MonacoEditorPane(
        instanceKey = "module-editor-config",
        language = "yaml",
        value = configText,
        onValueChange = onConfigChange,
    )
}
