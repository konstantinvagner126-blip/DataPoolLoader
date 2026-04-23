package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiDatabaseConnectionStatus
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeActorState
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal fun exportConnection(
    columns: List<String>,
    rows: List<List<Any?>>,
): Connection {
    var cursor = -1
    val metaData = Proxy.newProxyInstance(
        ResultSetMetaData::class.java.classLoader,
        arrayOf(ResultSetMetaData::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getColumnCount" -> columns.size
            "getColumnLabel" -> columns[(args?.get(0) as Int) - 1]
            else -> defaultValue(method.returnType)
        }
    } as ResultSetMetaData
    val resultSet = Proxy.newProxyInstance(
        ResultSet::class.java.classLoader,
        arrayOf(ResultSet::class.java),
    ) { _, method, args ->
        when (method.name) {
            "next" -> {
                cursor++
                cursor < rows.size
            }
            "getObject" -> rows[cursor][(args?.get(0) as Int) - 1]
            "getMetaData" -> metaData
            "close" -> null
            else -> defaultValue(method.returnType)
        }
    } as ResultSet
    val statement = Proxy.newProxyInstance(
        PreparedStatement::class.java.classLoader,
        arrayOf(PreparedStatement::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "executeQuery" -> resultSet
            "setFetchSize", "close" -> null
            else -> defaultValue(method.returnType)
        }
    } as PreparedStatement
    return Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "prepareStatement" -> statement
            "setAutoCommit", "close", "commit", "rollback" -> null
            "getAutoCommit" -> false
            else -> defaultValue(method.returnType)
        }
    } as Connection
}

internal fun testRuntimeContext(mode: UiModuleStoreMode): UiRuntimeContext =
    UiRuntimeContext(
        requestedMode = mode,
        effectiveMode = mode,
        actor = UiRuntimeActorState(
            resolved = true,
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
            actorDisplayName = "kwdev",
            message = "ok",
        ),
        database = UiDatabaseConnectionStatus(
            configured = mode == UiModuleStoreMode.DATABASE,
            available = mode == UiModuleStoreMode.DATABASE,
            schema = "ui_registry",
            message = if (mode == UiModuleStoreMode.DATABASE) "ok" else "db unavailable",
        ),
    )

internal fun createProject() = Files.createTempDirectory("ui-server").apply {
    resolve("settings.gradle.kts").writeText("rootProject.name = \"test\"")
    val appDir = resolve("apps/demo-app")
    appDir.createDirectories()
    appDir.resolve("build.gradle.kts").writeText("plugins { application }")
    appDir.resolve("ui-module.yml").writeText(
        """
        title: Demo App
        description: Учебный модуль для UI-тестов.
        tags:
          - postgres
          - demo
        """.trimIndent(),
    )
    val resources = appDir.resolve("src/main/resources").createDirectories()
    resources.resolve("application.yml").writeText(
        """
        app:
          commonSqlFile: classpath:sql/common.sql
          sources:
            - name: db2
              jdbcUrl: jdbc:postgresql://localhost:5432/source
              username: source_user
              password: source_password
              sqlFile: classpath:sql/db2.sql
        """.trimIndent(),
    )
    resources.resolve("sql").createDirectories()
    resources.resolve("sql/common.sql").writeText("select 1")
    resources.resolve("sql/db2.sql").writeText("select 2")
}

private fun defaultValue(type: Class<*>): Any? = when {
    type == java.lang.Boolean.TYPE -> false
    type == java.lang.Integer.TYPE -> 0
    type == java.lang.Long.TYPE -> 0L
    else -> null
}
