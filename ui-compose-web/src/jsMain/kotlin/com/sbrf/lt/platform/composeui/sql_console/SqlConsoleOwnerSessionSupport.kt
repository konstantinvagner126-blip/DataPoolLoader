package com.sbrf.lt.platform.composeui.sql_console

import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.Date
import kotlin.random.Random

private const val SQL_CONSOLE_OWNER_SESSION_KEY = "sql-console.owner-session-id"
private const val SQL_CONSOLE_EXECUTION_OWNER_KEY = "sql-console.execution-owner-state"

private val sqlConsoleOwnerJson = Json {
    ignoreUnknownKeys = true
}

@Serializable
internal data class SqlConsoleExecutionOwnerState(
    val executionId: String,
    val ownerSessionId: String,
    val ownerToken: String,
)

internal fun resolveSqlConsoleOwnerSessionId(): String {
    val existing = runCatching { window.sessionStorage.getItem(SQL_CONSOLE_OWNER_SESSION_KEY) }.getOrNull()
    if (!existing.isNullOrBlank()) {
        return existing
    }
    return generateSqlConsoleOwnerSessionId().also { generated ->
        runCatching { window.sessionStorage.setItem(SQL_CONSOLE_OWNER_SESSION_KEY, generated) }
    }
}

internal fun loadSqlConsoleExecutionOwnerState(): SqlConsoleExecutionOwnerState? =
    runCatching { window.sessionStorage.getItem(SQL_CONSOLE_EXECUTION_OWNER_KEY) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.let { raw ->
            runCatching { sqlConsoleOwnerJson.decodeFromString(SqlConsoleExecutionOwnerState.serializer(), raw) }.getOrNull()
        }

internal fun saveSqlConsoleExecutionOwnerState(state: SqlConsoleExecutionOwnerState) {
    runCatching {
        window.sessionStorage.setItem(
            SQL_CONSOLE_EXECUTION_OWNER_KEY,
            sqlConsoleOwnerJson.encodeToString(SqlConsoleExecutionOwnerState.serializer(), state),
        )
    }
}

internal fun clearSqlConsoleExecutionOwnerState() {
    runCatching { window.sessionStorage.removeItem(SQL_CONSOLE_EXECUTION_OWNER_KEY) }
}

private fun generateSqlConsoleOwnerSessionId(): String =
    "sql-owner-${Date.now().toLong().toString(36)}-${Random.nextLong().toString(36)}"
