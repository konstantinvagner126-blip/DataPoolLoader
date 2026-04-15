package com.sbrf.lt.platform.ui.module

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseModuleStoreTest {

    @Test
    fun `lists catalog modules from current revisions`() {
        val preparedSql = mutableListOf<String>()
        val includeHiddenParams = mutableListOf<Boolean>()
        val rows = listOf(
            mapOf(
                "module_code" to "db-demo",
                "title" to "DB Demo",
                "description" to "Модуль из PostgreSQL",
                "tags_json" to """["postgres","db"]""",
                "validation_status" to "WARNING",
                "validation_issues_json" to """[{"severity":"WARNING","message":"Проверить target"}]""",
            ),
        )
        val store = DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    rows = rows,
                    preparedSql = preparedSql,
                    includeHiddenParams = includeHiddenParams,
                )
            },
        )

        val modules = store.listModules()

        assertEquals(listOf(false), includeHiddenParams)
        assertTrue(preparedSql.single().contains("ui_registry.module"))
        assertEquals(1, modules.size)
        assertEquals("db-demo", modules.single().id)
        assertEquals("DB Demo", modules.single().title)
        assertEquals("Модуль из PostgreSQL", modules.single().description)
        assertEquals(listOf("postgres", "db"), modules.single().tags)
        assertEquals("WARNING", modules.single().validationStatus)
        assertEquals("Проверить target", modules.single().validationIssues.single().message)
    }

    @Test
    fun `include hidden flag is passed to catalog query`() {
        val includeHiddenParams = mutableListOf<Boolean>()
        val store = DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    rows = emptyList(),
                    includeHiddenParams = includeHiddenParams,
                )
            },
        )

        assertEquals(emptyList(), store.listModules(includeHidden = true))

        assertEquals(listOf(true), includeHiddenParams)
    }

    @Test
    fun `loads module details from current revision and normalized sql assets`() {
        val preparedSql = mutableListOf<String>()
        val stringParams = mutableListOf<String>()
        val store = DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    rows = listOf(
                        mapOf(
                            "module_code" to "db-demo",
                            "title" to "DB Demo",
                            "description" to "Модуль из PostgreSQL",
                            "tags_json" to """["database"]""",
                            "validation_status" to "VALID",
                            "validation_issues_json" to "[]",
                            "config_text" to "app:\n  mergeMode: plain\n",
                            "source_kind" to "CURRENT_REVISION",
                            "current_revision_id" to "11111111-1111-1111-1111-111111111111",
                            "working_copy_id" to null,
                            "working_copy_status" to null,
                            "base_revision_id" to null,
                            "working_copy_json" to null,
                        ),
                    ),
                    sqlAssetRows = listOf(
                        mapOf(
                            "label" to "Общий SQL",
                            "asset_key" to "common",
                            "sql_text" to "select 1",
                        ),
                    ),
                    preparedSql = preparedSql,
                    stringParams = stringParams,
                )
            },
        )

        val details = store.loadModuleDetails(
            moduleCode = "db-demo",
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
        )

        assertEquals(listOf("kwdev", "OS_LOGIN", "db-demo", "11111111-1111-1111-1111-111111111111"), stringParams)
        assertTrue(preparedSql.any { it.contains("module_working_copy") })
        assertTrue(preparedSql.any { it.contains("module_revision_sql_asset") })
        assertEquals("CURRENT_REVISION", details.sourceKind)
        assertEquals("db-demo", details.module.id)
        assertEquals("DB Demo", details.module.title)
        assertEquals("db:db-demo", details.module.configPath)
        assertEquals("app:\n  mergeMode: plain\n", details.module.configText)
        assertEquals("Общий SQL", details.module.sqlFiles.single().label)
        assertEquals("common", details.module.sqlFiles.single().path)
        assertEquals("select 1", details.module.sqlFiles.single().content)
    }

    @Test
    fun `loads module details from personal working copy snapshot when it exists`() {
        val preparedSql = mutableListOf<String>()
        val store = DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    rows = listOf(
                        mapOf(
                            "module_code" to "db-demo",
                            "title" to "DB Demo Draft",
                            "description" to null,
                            "tags_json" to "[]",
                            "validation_status" to "WARNING",
                            "validation_issues_json" to """[{"severity":"WARNING","message":"draft"}]""",
                            "config_text" to "app:\n  mergeMode: quota\n",
                            "source_kind" to "WORKING_COPY",
                            "current_revision_id" to "11111111-1111-1111-1111-111111111111",
                            "working_copy_id" to "22222222-2222-2222-2222-222222222222",
                            "working_copy_status" to "DIRTY",
                            "base_revision_id" to "11111111-1111-1111-1111-111111111111",
                            "working_copy_json" to """
                                {
                                  "sqlFiles": [
                                    {
                                      "label": "Источник: db1",
                                      "path": "db1",
                                      "content": "select 10",
                                      "exists": true
                                    }
                                  ]
                                }
                            """.trimIndent(),
                        ),
                    ),
                    preparedSql = preparedSql,
                )
            },
        )

        val details = store.loadModuleDetails(
            moduleCode = "db-demo",
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
        )

        assertEquals("WORKING_COPY", details.sourceKind)
        assertEquals("DIRTY", details.workingCopyStatus)
        assertEquals("22222222-2222-2222-2222-222222222222", details.workingCopyId)
        assertEquals("select 10", details.module.sqlFiles.single().content)
        assertTrue(preparedSql.none { it.contains("module_revision_sql_asset") })
    }

    @Test
    fun `rejects invalid schema name`() {
        val store = DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            schema = "ui_registry; drop schema public",
        )

        val error = assertFailsWith<IllegalArgumentException> {
            store.listModules()
        }

        assertTrue(error.message!!.contains("Некорректное имя schema"))
    }

    private fun fakeConnection(
        rows: List<Map<String, String?>>,
        sqlAssetRows: List<Map<String, String?>> = emptyList(),
        preparedSql: MutableList<String> = mutableListOf(),
        includeHiddenParams: MutableList<Boolean> = mutableListOf(),
        stringParams: MutableList<String> = mutableListOf(),
    ): Connection {
        return Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val sql = args?.first() as String
                    preparedSql += sql
                    fakePreparedStatement(
                        rows = if (sql.contains("module_revision_sql_asset")) sqlAssetRows else rows,
                        includeHiddenParams = includeHiddenParams,
                        stringParams = stringParams,
                    )
                }
                "close" -> null
                else -> defaultReturnValue(method.returnType)
            }
        } as Connection
    }

    private fun fakePreparedStatement(
        rows: List<Map<String, String?>>,
        includeHiddenParams: MutableList<Boolean>,
        stringParams: MutableList<String>,
    ): PreparedStatement =
        Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, args ->
            when (method.name) {
                "setBoolean" -> {
                    includeHiddenParams += args?.get(1) as Boolean
                    null
                }
                "setString" -> {
                    stringParams += args?.get(1) as String
                    null
                }
                "executeQuery" -> fakeResultSet(rows)
                "close" -> null
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
