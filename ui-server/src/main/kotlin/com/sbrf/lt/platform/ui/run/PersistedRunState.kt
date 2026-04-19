package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import java.time.Instant

/**
 * Состояние run-history UI, сохраняемое на диск между перезапусками приложения.
 */
data class PersistedRunState(
    val history: List<UiRunSnapshot> = emptyList(),
) {
    fun withRecoveredInterruptedRuns(now: Instant = Instant.now()): PersistedRunState {
        val recovered = history.map { snapshot ->
            if (snapshot.status == ExecutionStatus.RUNNING) {
                snapshot.copy(
                    status = ExecutionStatus.FAILED,
                    finishedAt = snapshot.finishedAt ?: now,
                    errorMessage = snapshot.errorMessage ?: "UI был перезапущен до завершения запуска.",
                )
            } else {
                snapshot
            }
        }
        return copy(history = recovered)
    }
}
