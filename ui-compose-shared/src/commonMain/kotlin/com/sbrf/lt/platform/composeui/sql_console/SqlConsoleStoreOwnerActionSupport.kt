package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleStoreOwnerActionSupport(
    private val api: SqlConsoleApi,
) {
    suspend fun heartbeatExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        val actionRequest = current.ownerActionRequest(ownerSessionId) ?: return current
        return runCatching {
            val snapshot = api.heartbeatExecution(executionId, actionRequest)
            current.applyExecutionSnapshot(snapshot).copy(
                actionInProgress = null,
                errorMessage = null,
            )
        }.getOrElse { error ->
            current.handleOwnershipFailure(
                error = error,
                fallbackMessage = "Не удалось подтвердить владение SQL execution session.",
            )
        }
    }

    suspend fun cancelExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        val actionRequest = current.ownerActionRequest(ownerSessionId)
            ?: return current.copy(
                actionInProgress = null,
                errorMessage = "Execution session больше не принадлежит этой вкладке.",
                successMessage = null,
            )
        return runCatching {
            val snapshot = api.cancelExecution(executionId, actionRequest)
            current.applyExecutionSnapshot(snapshot).copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Запрос помечен на остановку.",
            )
        }.getOrElse { error ->
            current.handleOwnershipFailure(error, "Не удалось остановить запрос.")
        }
    }

    suspend fun commitExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        val actionRequest = current.ownerActionRequest(ownerSessionId)
            ?: return current.copy(
                actionInProgress = null,
                errorMessage = "Execution session больше не принадлежит этой вкладке.",
                successMessage = null,
            )
        return runCatching {
            val snapshot = api.commitExecution(executionId, actionRequest)
            current.applyExecutionSnapshot(snapshot).copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Транзакция зафиксирована.",
            )
        }.getOrElse { error ->
            current.handleOwnershipFailure(error, "Не удалось выполнить commit.")
        }
    }

    suspend fun rollbackExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        val actionRequest = current.ownerActionRequest(ownerSessionId)
            ?: return current.copy(
                actionInProgress = null,
                errorMessage = "Execution session больше не принадлежит этой вкладке.",
                successMessage = null,
            )
        return runCatching {
            val snapshot = api.rollbackExecution(executionId, actionRequest)
            current.applyExecutionSnapshot(snapshot).copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Транзакция откатана.",
            )
        }.getOrElse { error ->
            current.handleOwnershipFailure(error, "Не удалось выполнить rollback.")
        }
    }
}
