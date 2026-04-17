package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseModuleExecutionSourceTest {

    @Test
    fun `prepares runtime snapshot from current revision and inserts execution snapshot`() {
        val preparedSql = mutableListOf<String>()
        val stringParams = mutableListOf<String?>()
        val source = DatabaseModuleExecutionSource(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    preparedSql = preparedSql,
                    stringParams = stringParams,
                    sourceRows = listOf(
                        mapOf(
                            "module_id" to "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                            "module_code" to "db-demo",
                            "title" to "DB Demo",
                            "config_text" to """
                                app:
                                  outputDir: ./out
                                  mergeMode: plain
                                  errorMode: continue_on_error
                                  parallelism: 1
                                  fetchSize: 100
                                  commonSqlFile: classpath:sql/common.sql
                                  target:
                                    enabled: false
                                  sources:
                                    - name: db1
                                      jdbcUrl: jdbc:test:db1
                                      username: user
                                      password: pwd
                            """.trimIndent(),
                            "source_kind" to "CURRENT_REVISION",
                            "source_revision_id" to "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                            "source_working_copy_id" to null,
                            "working_copy_json" to null,
                        ),
                    ),
                    sqlAssetRows = listOf(
                        mapOf(
                            "asset_key" to "classpath:sql/common.sql",
                            "sql_text" to "select 1",
                        ),
                    ),
                )
            },
        )

        val snapshot = source.prepareExecution(
            moduleCode = "db-demo",
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
            actorDisplayName = "kwdev",
        )

        assertEquals("db-demo", snapshot.moduleCode)
        assertEquals("DB Demo", snapshot.moduleTitle)
        assertEquals("CURRENT_REVISION", snapshot.launchSourceKind)
        assertEquals("db:db-demo#current_revision", snapshot.configLocation)
        assertEquals("select 1", snapshot.appConfig.commonSql)
        assertEquals("select 1", snapshot.sqlFiles["classpath:sql/common.sql"])
        assertNotNull(snapshot.executionSnapshotId)
        assertTrue(preparedSql.any { it.contains("from ui_registry.module m") })
        assertTrue(preparedSql.any { it.contains("from ui_registry.module_revision_sql_asset") })
        assertTrue(preparedSql.any { it.contains("insert into ui_registry.execution_snapshot") })
        assertTrue(stringParams.contains("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
        assertTrue(stringParams.any { it?.contains("\"configText\"") == true })
    }

    @Test
    fun `prepares runtime snapshot from working copy and skips revision sql asset lookup`() {
        val preparedSql = mutableListOf<String>()
        val stringParams = mutableListOf<String?>()
        val source = DatabaseModuleExecutionSource(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    preparedSql = preparedSql,
                    stringParams = stringParams,
                    sourceRows = listOf(
                        mapOf(
                            "module_id" to "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                            "module_code" to "db-demo",
                            "title" to "DB Demo",
                            "config_text" to """
                                app:
                                  outputDir: ./out
                                  mergeMode: plain
                                  errorMode: continue_on_error
                                  parallelism: 1
                                  fetchSize: 100
                                  commonSqlFile: classpath:sql/common.sql
                                  target:
                                    enabled: false
                                  sources:
                                    - name: db1
                                      jdbcUrl: jdbc:test:db1
                                      username: user
                                      password: pwd
                            """.trimIndent(),
                            "source_kind" to "WORKING_COPY",
                            "source_revision_id" to null,
                            "source_working_copy_id" to "cccccccc-cccc-cccc-cccc-cccccccccccc",
                            "working_copy_json" to """
                                {
                                  "sqlFiles": [
                                    {
                                      "label": "Общий SQL",
                                      "path": "classpath:sql/common.sql",
                                      "content": "select 2",
                                      "exists": true
                                    }
                                  ]
                                }
                            """.trimIndent(),
                        ),
                    ),
                )
            },
        )

        val snapshot = source.prepareExecution(
            moduleCode = "db-demo",
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
            actorDisplayName = "kwdev",
        )

        assertEquals("WORKING_COPY", snapshot.launchSourceKind)
        assertEquals("db:db-demo#working_copy", snapshot.configLocation)
        assertEquals("select 2", snapshot.appConfig.commonSql)
        assertEquals("select 2", snapshot.sqlFiles["classpath:sql/common.sql"])
        assertNotNull(snapshot.executionSnapshotId)
        assertTrue(preparedSql.any { it.contains("insert into ui_registry.execution_snapshot") })
        assertTrue(preparedSql.none { it.contains("from ui_registry.module_revision_sql_asset") })
        assertTrue(stringParams.contains("cccccccc-cccc-cccc-cccc-cccccccccccc"))
    }

    private fun fakeConnection(
        preparedSql: MutableList<String>,
        stringParams: MutableList<String?>,
        sourceRows: List<Map<String, String?>>,
        sqlAssetRows: List<Map<String, String?>> = emptyList(),
    ): Connection =
        Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val sql = args?.first() as String
                    preparedSql += sql
                    fakePreparedStatement(
                        rows = when {
                            sql.contains("from ui_registry.module_revision_sql_asset") -> sqlAssetRows
                            sql.contains("from ui_registry.module m") -> sourceRows
                            else -> emptyList()
                        },
                        stringParams = stringParams,
                    )
                }
                "setAutoCommit", "commit", "rollback", "close" -> null
                "getAutoCommit" -> false
                else -> defaultReturnValue(method.returnType)
            }
        } as Connection

    private fun fakePreparedStatement(
        rows: List<Map<String, String?>>,
        stringParams: MutableList<String?>,
    ): PreparedStatement =
        Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, args ->
            when (method.name) {
                "executeQuery" -> fakeResultSet(rows)
                "executeUpdate" -> 1
                "setString" -> {
                    stringParams += args?.get(1) as String?
                    null
                }
                "setBoolean", "setInt", "setLong", "setObject", "close" -> null
                else -> defaultReturnValue(method.returnType)
            }
        } as PreparedStatement

    private fun fakeResultSet(rows: List<Map<String, String?>>): ResultSet {
        var index = -1
        return Proxy.newProxyInstance(
            ResultSet::class.java.classLoader,
            arrayOf(ResultSet::class.java),
        ) { _, method, args ->
            when (method.name) {
                "next" -> {
                    index += 1
                    index < rows.size
                }
                "getString" -> rows[index][args?.first() as String]
                "close" -> null
                else -> defaultReturnValue(method.returnType)
            }
        } as ResultSet
    }

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
