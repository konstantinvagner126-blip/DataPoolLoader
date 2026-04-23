package com.sbrf.lt.platform.composeui.sql_console

internal fun SqlConsolePageState.ownerActionRequest(ownerSessionId: String): SqlConsoleExecutionOwnerActionRequest? {
    val ownerToken = currentExecution?.ownerToken ?: return null
    return SqlConsoleExecutionOwnerActionRequest(
        ownerSessionId = ownerSessionId,
        ownerToken = ownerToken,
    )
}

private fun SqlConsoleExecutionResponse.withOwnerToken(ownerToken: String?): SqlConsoleExecutionResponse =
    copy(
        ownerToken = when {
            this.ownerToken != null -> this.ownerToken
            status == "RUNNING" || transactionState == "PENDING_COMMIT" -> ownerToken
            else -> null
        },
    )

internal fun SqlConsolePageState.applyExecutionSnapshot(
    snapshot: SqlConsoleExecutionResponse,
): SqlConsolePageState {
    val execution = snapshot.withOwnerToken(currentExecution?.ownerToken)
    return copy(
        currentExecution = execution,
        currentExecutionId = execution.id,
        sourceStatuses = mergeSourceConnectionStatuses(
            current = sourceStatuses,
            observed = observedExecutionSourceStatuses(execution.result),
        ),
    )
}

internal fun SqlConsolePageState.handleOwnershipFailure(
    error: Throwable,
    fallbackMessage: String,
): SqlConsolePageState {
    val message = error.message ?: fallbackMessage
    return if (message.indicatesOwnershipLoss()) {
        clearExecutionOwnership(message)
    } else {
        copy(
            actionInProgress = null,
            errorMessage = message,
            successMessage = null,
        )
    }
}

internal fun SqlConsolePageState.clearExecutionOwnership(message: String): SqlConsolePageState =
    copy(
        actionInProgress = null,
        errorMessage = message,
        successMessage = null,
        currentExecution = currentExecution?.copy(ownerToken = null),
    )

private fun String.indicatesOwnershipLoss(): Boolean =
    contains("не принадлежит", ignoreCase = true) ||
        contains("control-path", ignoreCase = true) ||
        contains("потеряла владельца", ignoreCase = true) ||
        contains("владелец", ignoreCase = true) && contains("потерян", ignoreCase = true)
