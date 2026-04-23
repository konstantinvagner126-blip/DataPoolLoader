package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.sqlconsole.RawShardConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.ShardConnectionChecker
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectColumnLoader
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectInspector
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectSearchResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectSearcher
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObject
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectColumn
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectInspector
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectType
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceGroupConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlConsoleServiceMetadataTest {

    @Test
    fun `exposes timeout in info`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                queryTimeoutSec = 45,
                sourceCatalog = listOf(testSource("shard1")),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
        )

        assertEquals(45, service.info().queryTimeoutSec)
    }

    @Test
    fun `exposes source groups in info`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sourceCatalog = listOf(testSource("shard1"), testSource("shard2")),
                groups = listOf(
                    SqlConsoleSourceGroupConfig("dev", listOf("shard1", "shard2")),
                    SqlConsoleSourceGroupConfig("ift", listOf("shard2")),
                ),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
        )

        assertEquals(listOf("dev", "ift"), service.info().groups.map { it.name })
        assertEquals(listOf("shard1", "shard2"), service.info().groups.first().sources)
    }

    @Test
    fun `exposes synthetic ungrouped source group in info`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sourceCatalog = listOf(testSource("shard1"), testSource("shard2"), testSource("shard3")),
                groups = listOf(SqlConsoleSourceGroupConfig("dev", listOf("shard1", "shard2"))),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
        )

        val info = service.info()

        assertEquals(listOf("dev", "Без группы"), info.groups.map { it.name })
        assertEquals(false, info.groups.first().synthetic)
        assertEquals(true, info.groups.last().synthetic)
        assertEquals(listOf("shard3"), info.groups.last().sources)
    }

    @Test
    fun `fails when source group references unknown source`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sourceCatalog = listOf(testSource("shard1")),
                groups = listOf(SqlConsoleSourceGroupConfig("dev", listOf("shard1", "missing"))),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
            connectionChecker = ShardConnectionChecker { _, _ -> error("should not be called") },
        )

        val error = assertFailsWith<IllegalArgumentException> {
            service.checkConnections(credentialsPath = null)
        }

        assertTrue(error.message!!.contains("missing"))
    }

    @Test
    fun `updates max rows per shard at runtime`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(maxRowsPerShard = 200, sourceCatalog = listOf(testSource("shard1"))),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
        )

        val updated = service.updateMaxRowsPerShard(350)

        assertEquals(350, updated.maxRowsPerShard)
        assertEquals(350, service.info().maxRowsPerShard)
    }

    @Test
    fun `updates timeout at runtime`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                maxRowsPerShard = 200,
                queryTimeoutSec = 45,
                sourceCatalog = listOf(testSource("shard1")),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
        )

        val updated = service.updateSettings(maxRowsPerShard = 280, queryTimeoutSec = 90)

        assertEquals(280, updated.maxRowsPerShard)
        assertEquals(90, updated.queryTimeoutSec)
        assertEquals(90, service.info().queryTimeoutSec)
    }

    @Test
    fun `checks connections for all configured sources`() {
        val checked = mutableListOf<String>()
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                queryTimeoutSec = 12,
                sourceCatalog = listOf(testSource("shard1"), testSource("shard2")),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
            connectionChecker = ShardConnectionChecker { shard, timeout ->
                checked += shard.name
                assertEquals(12, timeout)
                if (shard.name == "shard2") error("boom")
                RawShardConnectionCheckResult(shardName = shard.name, status = "SUCCESS", message = "ok")
            },
        )

        val result = service.checkConnections(credentialsPath = null)

        assertEquals(listOf("shard1", "shard2"), checked)
        assertEquals("SUCCESS", result.sourceResults.first { it.shardName == "shard1" }.status)
        assertEquals("FAILED", result.sourceResults.first { it.shardName == "shard2" }.status)
    }

    @Test
    fun `connection check returns failed status when placeholder is not resolved`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sourceCatalog = listOf(
                    SqlConsoleSourceConfig("shard1", "\${SHARD1_URL}", "user1", "pwd1"),
                    testSource("shard2"),
                ),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
            connectionChecker = ShardConnectionChecker { shard, _ ->
                RawShardConnectionCheckResult(shardName = shard.name, status = "SUCCESS", message = "ok")
            },
        )

        val result = service.checkConnections(credentialsPath = null)

        assertEquals("FAILED", result.sourceResults.first { it.shardName == "shard1" }.status)
        assertTrue(result.sourceResults.first { it.shardName == "shard1" }.errorMessage!!.contains("SHARD1_URL"))
        assertEquals("SUCCESS", result.sourceResults.first { it.shardName == "shard2" }.status)
    }

    @Test
    fun `searches database objects only for selected sources`() {
        val searched = mutableListOf<String>()
        val service = SqlConsoleService(
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"), testSource("shard2"))),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
            objectSearcher = ShardSqlObjectSearcher { shard, rawQuery, maxObjects ->
                searched += shard.name
                assertEquals("offer", rawQuery)
                assertEquals(30, maxObjects)
                ShardSqlObjectSearchResult(
                    objects = listOf(
                        SqlConsoleDatabaseObject(
                            schemaName = "public",
                            objectName = "${shard.name}_offer",
                            objectType = SqlConsoleDatabaseObjectType.TABLE,
                        ),
                    ),
                )
            },
        )

        val result = service.searchObjects(
            rawQuery = "offer",
            credentialsPath = null,
            selectedSourceNames = listOf("shard2"),
        )

        assertEquals(listOf("shard2"), searched)
        assertEquals(1, result.sourceResults.size)
        assertEquals("shard2", result.sourceResults.single().sourceName)
        assertEquals("public", result.sourceResults.single().objects.single().schemaName)
    }

    @Test
    fun `loads inspector metadata only for requested source and object`() {
        val inspected = mutableListOf<String>()
        val service = SqlConsoleService(
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"), testSource("shard2"))),
            objectInspector = ShardSqlObjectInspector { shard, schemaName, objectName, objectType ->
                inspected += "${shard.name}|$schemaName|$objectName|${objectType.name}"
                SqlConsoleDatabaseObjectInspector(
                    schemaName = schemaName,
                    objectName = objectName,
                    objectType = objectType,
                    definition = "create view public.offer as select 1;",
                    columns = listOf(SqlConsoleDatabaseObjectColumn(name = "id", type = "bigint", nullable = false)),
                )
            },
        )

        val result = service.inspectObject(
            sourceName = "shard2",
            schemaName = "public",
            objectName = "offer",
            objectType = SqlConsoleDatabaseObjectType.VIEW,
            credentialsPath = null,
        )

        assertEquals(listOf("shard2|public|offer|VIEW"), inspected)
        assertEquals("create view public.offer as select 1;", result.definition)
        assertEquals("id", result.columns.single().name)
    }

    @Test
    fun `loads object columns only for selected sources`() {
        val loaded = mutableListOf<String>()
        val service = SqlConsoleService(
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"), testSource("shard2"))),
            objectColumnLoader = ShardSqlObjectColumnLoader { shard, schemaName, objectName, objectType ->
                loaded += "${shard.name}|$schemaName|$objectName|${objectType.name}"
                listOf(
                    SqlConsoleDatabaseObjectColumn(
                        name = "${shard.name}_id",
                        type = "bigint",
                        nullable = false,
                    ),
                )
            },
        )

        val result = service.loadObjectColumns(
            schemaName = "public",
            objectName = "offer",
            objectType = SqlConsoleDatabaseObjectType.TABLE,
            credentialsPath = null,
            selectedSourceNames = listOf("shard2"),
        )

        assertEquals(listOf("shard2|public|offer|TABLE"), loaded)
        assertEquals("public", result.schemaName)
        assertEquals("offer", result.objectName)
        assertEquals(SqlConsoleDatabaseObjectType.TABLE, result.objectType)
        assertEquals(1, result.sourceResults.size)
        assertEquals("shard2", result.sourceResults.single().sourceName)
        assertEquals("shard2_id", result.sourceResults.single().columns.single().name)
    }
}
