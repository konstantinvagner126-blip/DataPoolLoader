package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.app.RuntimeModuleSnapshot
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.module.DatabaseConnectionProvider
import com.sbrf.lt.platform.ui.module.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.platform.ui.module.SqlFileEntries
import com.sbrf.lt.platform.ui.module.normalizeSchemaName
import com.sbrf.lt.platform.ui.module.sqlAssetsSql
import java.security.MessageDigest
import java.sql.Connection
import java.util.UUID

/**
 * Готовит runtime snapshot DB-модуля из current revision или personal working copy
 * и одновременно сохраняет execution snapshot в PostgreSQL registry.
 */
class DatabaseModuleExecutionSource(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String = UiModuleStorePostgresConfig.DEFAULT_SCHEMA,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val snapshotFactory: RuntimeConfigSnapshotFactory = RuntimeConfigSnapshotFactory(),
) {
    private val sqlFilesType = object : TypeReference<List<ModuleFileContent>>() {}

    companion object {
        fun fromConfig(config: UiModuleStorePostgresConfig): DatabaseModuleExecutionSource =
            DatabaseModuleExecutionSource(
                connectionProvider = DriverManagerDatabaseConnectionProvider(config),
                schema = config.schemaName(),
            )
    }

    fun prepareExecution(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): RuntimeModuleSnapshot {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val source = loadSource(connection, normalizedSchema, moduleCode, actorId, actorSource)
                val sqlFiles = loadSqlFiles(connection, normalizedSchema, source)
                val executionSnapshotId = UUID.randomUUID().toString()
                val runtimeSnapshot = snapshotFactory.createSnapshot(
                    moduleCode = source.moduleCode,
                    moduleTitle = source.title,
                    configText = source.configText,
                    sqlFiles = sqlFiles,
                    launchSourceKind = source.sourceKind,
                    configLocation = "db:${source.moduleCode}#${source.sourceKind.lowercase()}",
                    executionSnapshotId = executionSnapshotId,
                )

                val snapshotJson = buildSnapshotJson(source.configText, sqlFiles)
                val contentHash = contentHash(source.configText, sqlFiles)
                insertExecutionSnapshot(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    executionSnapshotId = executionSnapshotId,
                    source = source,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = actorDisplayName,
                    snapshotJson = snapshotJson,
                    contentHash = contentHash,
                )
                connection.commit()
                return runtimeSnapshot
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    private fun loadSource(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseExecutionSourceRow {
        connection.prepareStatement(DatabaseModuleExecutionSourceSql.source(normalizedSchema)).use { stmt ->
            stmt.setString(1, actorId)
            stmt.setString(2, actorSource)
            stmt.setString(3, moduleCode)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) {
                    error("DB-модуль '$moduleCode' не найден.")
                }
                return DatabaseExecutionSourceRow(
                    moduleId = rs.getString("module_id"),
                    moduleCode = rs.getString("module_code"),
                    title = rs.getString("title"),
                    configText = rs.getString("config_text"),
                    sourceKind = rs.getString("source_kind"),
                    sourceRevisionId = rs.getString("source_revision_id"),
                    sourceWorkingCopyId = rs.getString("source_working_copy_id"),
                    workingCopyJson = rs.getString("working_copy_json"),
                )
            }
        }
    }

    private fun loadSqlFiles(
        connection: Connection,
        normalizedSchema: String,
        source: DatabaseExecutionSourceRow,
    ): Map<String, String> {
        return if (source.sourceKind == "WORKING_COPY") {
            readWorkingCopySqlFiles(source.workingCopyJson).associate { it.path to it.content }
        } else {
            connection.prepareStatement(sqlAssetsSql(normalizedSchema)).use { stmt ->
                stmt.setString(1, source.sourceRevisionId)
                stmt.executeQuery().use { rs ->
                    buildMap {
                        while (rs.next()) {
                            put(rs.getString("asset_key"), rs.getString("sql_text"))
                        }
                    }
                }
            }
        }
    }

    private fun insertExecutionSnapshot(
        connection: Connection,
        normalizedSchema: String,
        executionSnapshotId: String,
        source: DatabaseExecutionSourceRow,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        snapshotJson: String,
        contentHash: String,
    ) {
        connection.prepareStatement(DatabaseModuleExecutionSourceSql.insertExecutionSnapshot(normalizedSchema)).use { stmt ->
            stmt.setString(1, executionSnapshotId)
            stmt.setString(2, source.moduleId)
            stmt.setString(3, actorId)
            stmt.setString(4, actorSource)
            stmt.setString(5, actorDisplayName)
            stmt.setString(6, source.sourceRevisionId)
            stmt.setString(7, source.sourceWorkingCopyId)
            stmt.setString(8, snapshotJson)
            stmt.setString(9, source.configText)
            stmt.setString(10, contentHash)
            stmt.executeUpdate()
        }
    }

    private fun readWorkingCopySqlFiles(workingCopyJson: String?): List<ModuleFileContent> {
        if (workingCopyJson.isNullOrBlank()) return emptyList()
        val root = objectMapper.readTree(workingCopyJson)
        val sqlFilesNode = root.path("sqlFiles").takeIf(JsonNode::isArray) ?: return emptyList()
        return objectMapper.readValue(sqlFilesNode.traverse(objectMapper), sqlFilesType)
    }

    private fun buildSnapshotJson(configText: String, sqlFileContents: Map<String, String>): String {
        val sqlLabels = SqlFileEntries.labelsByPathOrEmpty(configText, objectMapper)
        val sqlFiles = sqlFileContents.entries
            .sortedBy { it.key }
            .map { (path, content) ->
                ModuleFileContent(
                    label = sqlLabels[path] ?: path,
                    path = path,
                    content = content,
                    exists = true,
                )
            }
        val root = objectMapper.createObjectNode()
        root.put("configText", configText)
        root.set<JsonNode>("sqlFiles", objectMapper.valueToTree(sqlFiles))
        return objectMapper.writeValueAsString(root)
    }

    private fun contentHash(configText: String, sqlFiles: Map<String, String>): String {
        val input = buildString {
            append(configText)
            sqlFiles.toSortedMap().forEach { (path, content) ->
                append('\n')
                append(path)
                append('\u0000')
                append(content)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
