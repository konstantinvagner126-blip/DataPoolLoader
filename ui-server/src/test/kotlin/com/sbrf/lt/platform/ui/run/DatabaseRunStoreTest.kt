package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.RuntimeModuleSnapshot
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.MergeMode
import com.sbrf.lt.datapool.model.SourceConfig
import com.sbrf.lt.datapool.model.TargetConfig
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseRunStoreTest {

    @Test
    fun `database run store writes instants as sql timestamps`() {
        val timestampCalls = mutableListOf<Timestamp>()
        val store = DatabaseRunStore(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(timestampCalls)
            },
        )
        val requestedAt = Instant.parse("2026-04-17T10:00:00Z")
        val startedAt = Instant.parse("2026-04-17T10:00:05Z")
        val finishedAt = Instant.parse("2026-04-17T10:01:00Z")
        val context = DatabaseModuleRunContext(
            runId = "11111111-1111-1111-1111-111111111111",
            runtimeSnapshot = RuntimeModuleSnapshot(
                moduleCode = "db-demo",
                moduleTitle = "DB Demo",
                configYaml = "app: {}",
                sqlFiles = emptyMap(),
                appConfig = AppConfig(
                    mergeMode = MergeMode.PLAIN,
                    sources = listOf(SourceConfig(name = "db1")),
                    target = TargetConfig(enabled = true, table = "demo.target_table"),
                ),
                launchSourceKind = "WORKING_COPY",
                executionSnapshotId = "22222222-2222-2222-2222-222222222222",
                configLocation = "db:db-demo",
            ),
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
            actorDisplayName = "kwdev",
            requestedAt = requestedAt,
            sourceOrder = mapOf("db1" to 0),
        )

        store.createRun(context, startedAt = startedAt, outputDir = "/tmp/output")
        store.markSourceStarted(context.runId, "db1", startedAt)
        store.updateSourceProgress(context.runId, "db1", startedAt, 5)
        store.markSourceFinished(context.runId, "db1", "SUCCESS", finishedAt, 10, null)
        store.markSourceSkipped(context.runId, "db1", finishedAt, "schema mismatch")
        store.finishRun(
            runId = context.runId,
            finishedAt = finishedAt,
            status = "SUCCESS",
            mergedRowCount = 10,
            successfulSourceCount = 1,
            failedSourceCount = 0,
            skippedSourceCount = 0,
            targetStatus = "SUCCESS",
            targetTableName = "demo.target_table",
            targetRowsLoaded = 10,
            summaryJson = """{"status":"SUCCESS"}""",
            errorMessage = null,
        )

        assertTrue(timestampCalls.any { it.time == requestedAt.toEpochMilli() })
        assertTrue(timestampCalls.any { it.time == startedAt.toEpochMilli() })
        assertTrue(timestampCalls.any { it.time == finishedAt.toEpochMilli() })
    }

    private fun fakeConnection(timestampCalls: MutableList<Timestamp>): Connection =
        Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "prepareStatement" -> fakePreparedStatement(timestampCalls)
                "setAutoCommit", "commit", "rollback", "close" -> null
                "getAutoCommit" -> false
                else -> defaultReturnValue(method.returnType)
            }
        } as Connection

    private fun fakePreparedStatement(timestampCalls: MutableList<Timestamp>): PreparedStatement =
        Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, args ->
            when (method.name) {
                "executeUpdate" -> 1
                "setTimestamp" -> {
                    timestampCalls += args?.get(1) as Timestamp
                    null
                }
                "setObject" -> {
                    if (args?.get(1) is Instant) {
                        error("Instant must be converted to Timestamp before JDBC binding")
                    }
                    null
                }
                "setString", "setBoolean", "setInt", "setLong", "close" -> null
                else -> defaultReturnValue(method.returnType)
            }
        } as PreparedStatement

    private fun defaultReturnValue(returnType: Class<*>): Any? = when (returnType) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Void.TYPE -> null
        else -> null
    }
}
