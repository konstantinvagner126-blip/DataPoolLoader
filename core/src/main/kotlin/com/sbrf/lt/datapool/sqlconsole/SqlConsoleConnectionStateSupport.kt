package com.sbrf.lt.datapool.sqlconsole

import java.sql.SQLException

internal fun classifyExecutionConnectionState(error: Throwable): SqlConsoleConnectionState? {
    val sqlError = error.findSqlException() ?: return null
    return if (sqlError.isConnectionFailure()) {
        SqlConsoleConnectionState.UNAVAILABLE
    } else {
        SqlConsoleConnectionState.AVAILABLE
    }
}

private fun Throwable.findSqlException(): SQLException? =
    generateSequence(this) { it.cause }
        .filterIsInstance<SQLException>()
        .firstOrNull()

private fun SQLException.isConnectionFailure(): Boolean {
    val normalizedState = sqlState?.uppercase().orEmpty()
    if (normalizedState.startsWith("08") || normalizedState == "28P01" || normalizedState == "28000") {
        return true
    }
    val normalizedMessage = message.orEmpty().lowercase()
    return normalizedMessage.contains("connection refused") ||
        normalizedMessage.contains("connection attempt failed") ||
        normalizedMessage.contains("connect timed out") ||
        normalizedMessage.contains("connection timed out") ||
        normalizedMessage.contains("socket timeout") ||
        normalizedMessage.contains("read timed out") ||
        normalizedMessage.contains("password authentication failed") ||
        normalizedMessage.contains("authentication failed") ||
        normalizedMessage.contains("communications link failure") ||
        normalizedMessage.contains("connection could not be made") ||
        normalizedMessage.contains("the connection attempt failed") ||
        normalizedMessage.contains("no pg_hba.conf entry") ||
        normalizedMessage.contains("unknown host") ||
        normalizedMessage.contains("could not connect") ||
        normalizedMessage.contains("connection is closed")
}
