package com.sbrf.lt.platform.composeui.sql_console

import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.files.Blob
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
    val ownerTabInstanceId: String,
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

internal fun resolveSqlConsoleOwnerTabInstanceId(): String {
    val existing = runCatching { window.name }.getOrNull()
    if (!existing.isNullOrBlank() && existing.startsWith("sql-tab-")) {
        return existing
    }
    return generateSqlConsoleOwnerTabInstanceId().also { generated ->
        runCatching { window.name = generated }
    }
}

internal fun resolveSqlConsoleWorkspaceId(): String =
    resolveSqlConsoleWorkspaceIdFromLocation()
        ?: resolveSqlConsoleOwnerTabInstanceId().replace("sql-tab-", "sql-workspace-")

internal fun generateSqlConsoleWorkspaceId(): String =
    "sql-workspace-${Date.now().toLong().toString(36)}-${Random.nextLong().toString(36)}"

internal fun buildSqlConsoleWorkspaceHref(workspaceId: String): String =
    "/sql-console?workspaceId=${urlEncode(workspaceId)}"

internal fun openSqlConsoleWorkspaceInNewTab(workspaceId: String): Boolean =
    openHrefInNewTab(buildSqlConsoleWorkspaceHref(workspaceId))

internal fun openHrefInNewTab(href: String): Boolean =
    runCatching {
        val openedWindow = window.open("", "_blank") ?: return@runCatching false
        runCatching {
            val dynamicWindow = openedWindow.asDynamic()
            dynamicWindow.opener = null
            dynamicWindow.location.replace(href)
        }.getOrElse {
            openedWindow.location.href = href
        }
        true
    }.getOrDefault(false)

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

internal fun tryReleaseSqlConsoleExecutionOwnership(
    executionId: String,
    ownerSessionId: String,
    ownerToken: String,
) {
    val payload = sqlConsoleOwnerJson.encodeToString(
        SqlConsoleExecutionOwnerActionRequest.serializer(),
        SqlConsoleExecutionOwnerActionRequest(
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
        ),
    )
    val url = "/api/sql-console/query/$executionId/release"
    val beaconSent = runCatching {
        val blobOptions = js("{}")
        blobOptions.type = "application/json"
        window.navigator.asDynamic().sendBeacon(url, Blob(arrayOf(payload), blobOptions)) as Boolean
    }.getOrDefault(false)
    if (!beaconSent) {
        runCatching {
            val options = js("{}")
            options.method = "POST"
            options.keepalive = true
            options.headers = js("{\"Content-Type\":\"application/json\"}")
            options.body = payload
            window.fetch(url, options)
        }
    }
}

private fun generateSqlConsoleOwnerSessionId(): String =
    "sql-owner-${Date.now().toLong().toString(36)}-${Random.nextLong().toString(36)}"

private fun generateSqlConsoleOwnerTabInstanceId(): String =
    "sql-tab-${Date.now().toLong().toString(36)}-${Random.nextLong().toString(36)}"

private fun resolveSqlConsoleWorkspaceIdFromLocation(): String? =
    runCatching {
        js("new URLSearchParams(window.location.search).get('workspaceId')") as String?
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
