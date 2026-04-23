package com.sbrf.lt.platform.composeui.run_history_cleanup

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.runtime.buildRuntimeModeFallbackMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.hasModeFallback
import org.jetbrains.compose.web.dom.Div

@Composable
internal fun RunHistoryCleanupPageContent(
    state: RunHistoryCleanupPageState,
    callbacks: RunHistoryCleanupPageCallbacks,
) {
    Div({ classes("run-history-cleanup-content-shell") }) {
        state.runtimeContext?.takeIf { it.hasModeFallback() }?.let { fallbackContext ->
            AlertBanner(
                buildRuntimeModeFallbackMessage(
                    fallbackContext,
                    suffix = "Экран показывает состояние выбранного режима, а операции для БД будут недоступны до восстановления подключения.",
                ),
                "warning",
            )
        }
        state.errorMessage?.let { AlertBanner(it, "warning") }
        state.successMessage?.let { AlertBanner(it, "success") }

        Div({ classes("run-history-cleanup-panels-shell") }) {
            CleanupSection(
                state = state,
                onToggleDisableSafeguard = callbacks.onToggleCleanupSafeguard,
                onRefreshPreview = callbacks.onRefreshCleanupPreview,
                onExecuteCleanup = callbacks.onExecuteCleanup,
            )

            OutputRetentionSection(
                state = state,
                onToggleDisableSafeguard = callbacks.onToggleOutputSafeguard,
                onRefreshPreview = callbacks.onRefreshOutputPreview,
                onExecuteCleanup = callbacks.onExecuteOutputCleanup,
            )
        }
    }
}
