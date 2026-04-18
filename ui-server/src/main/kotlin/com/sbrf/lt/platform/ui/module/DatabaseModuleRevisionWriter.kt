package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.config.sql.SqlFileReferenceExtractor
import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.datapool.module.validation.ModuleValidationService
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.RootConfig
import com.sbrf.lt.datapool.model.SourceConfig
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.platform.ui.model.toStatusValue
import java.math.BigDecimal
import java.security.MessageDigest
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import java.util.UUID

/**
 * Компонент записи revision-данных DB-модуля: revision, SQL assets, sources, target и quotas.
 */
internal class DatabaseModuleRevisionWriter(
    private val objectMapper: ObjectMapper,
    private val validationService: ModuleValidationService = ModuleValidationService(),
) {
    fun insertPublishedRevision(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        moduleId: String,
        currentRevisionId: String,
        revisionNo: Long,
        actorId: String,
        actorDisplayName: String?,
        workingCopyJson: String,
        workingCopyYaml: String,
        contentHash: String,
    ) {
        val appConfig = parseAppConfig(workingCopyYaml)
        val snapshot = readSnapshot(workingCopyJson)
        val validation = validationService.validate(
            configText = workingCopyYaml,
            sqlReferenceExists = { entry -> snapshot.sqlFiles.any { it.path == entry.path } },
        )
        connection.prepareStatement(ModuleRegistrySql.insertPublishedRevision(normalizedSchema)).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setString(2, moduleId)
            stmt.setLong(3, revisionNo)
            stmt.setString(4, actorDisplayName ?: actorId)
            stmt.setString(5, snapshot.title ?: "")
            stmt.setString(6, snapshot.description)
            stmt.setString(7, objectMapper.writeValueAsString(snapshot.tags))
            stmt.setBoolean(8, snapshot.hiddenFromUi)
            stmt.setString(9, validation.toStatusValue())
            stmt.setString(10, objectMapper.writeValueAsString(validation.toResponse()))
            stmt.setString(11, appConfig.outputDir)
            stmt.setString(12, appConfig.fileFormat.uppercase())
            stmt.setString(13, appConfig.mergeMode.name)
            stmt.setString(14, appConfig.errorMode.name)
            stmt.setInt(15, appConfig.parallelism)
            stmt.setInt(16, appConfig.fetchSize)
            setNullableInt(stmt, 17, appConfig.queryTimeoutSec)
            stmt.setLong(18, appConfig.progressLogEveryRows)
            setNullableLong(stmt, 19, appConfig.maxMergedRows)
            stmt.setBoolean(20, appConfig.deleteOutputFilesAfterCompletion)
            stmt.setString(21, workingCopyJson)
            stmt.setString(22, workingCopyYaml)
            stmt.setString(23, contentHash)
            check(stmt.executeUpdate() == 1) {
                "Не удалось создать published revision для module_id=$moduleId current_revision_id=$currentRevisionId"
            }
        }
    }

    fun insertInitialRevision(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        moduleId: String,
        revisionSource: String,
        request: CreateModuleRequest,
        actorId: String,
    ) {
        val tagsArray = objectMapper.createArrayNode()
        request.tags?.forEach { tagsArray.add(it) }

        val appConfig = parseAppConfig(request.configText)
        val snapshotJson = buildSnapshotJson(
            configText = request.configText,
            sqlFileContents = request.sqlFiles,
            title = request.title,
            description = request.description,
            tags = request.tags,
            hiddenFromUi = request.hiddenFromUi,
        )
        val validation = validationService.validate(
            configText = request.configText,
            sqlReferenceExists = { entry -> request.sqlFiles.containsKey(entry.path) },
        )

        connection.prepareStatement(ModuleRegistrySql.insertRevision(normalizedSchema)).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setString(2, moduleId)
            stmt.setLong(3, 1)
            stmt.setString(4, actorId)
            stmt.setString(5, revisionSource)
            stmt.setString(6, request.title)
            stmt.setString(7, request.description)
            stmt.setString(8, tagsArray.toString())
            stmt.setBoolean(9, request.hiddenFromUi)
            stmt.setString(10, validation.toStatusValue())
            stmt.setString(11, objectMapper.writeValueAsString(validation.toResponse()))
            stmt.setString(12, appConfig.outputDir)
            stmt.setString(13, appConfig.fileFormat.uppercase())
            stmt.setString(14, appConfig.mergeMode.name)
            stmt.setString(15, appConfig.errorMode.name)
            stmt.setInt(16, appConfig.parallelism)
            stmt.setInt(17, appConfig.fetchSize)
            setNullableInt(stmt, 18, appConfig.queryTimeoutSec)
            stmt.setLong(19, appConfig.progressLogEveryRows)
            setNullableLong(stmt, 20, appConfig.maxMergedRows)
            stmt.setBoolean(21, appConfig.deleteOutputFilesAfterCompletion)
            stmt.setString(22, snapshotJson)
            stmt.setString(23, request.configText)
            stmt.setString(24, contentHashForCreate(request))
            stmt.executeUpdate()
        }
    }

    fun insertRevisionSqlAssets(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        configText: String,
        sqlFiles: Map<String, String>,
    ): Map<String, String> {
        val appConfig = parseAppConfig(configText)
        val drafts = linkedMapOf<String, SqlAssetDraft>()
        val commonSql = appConfig.commonSql.trim()
        if (commonSql.isNotEmpty()) {
            drafts["commonSql"] = SqlAssetDraft(
                assetKey = "commonSql",
                assetKind = "COMMON",
                label = "Общий SQL",
                sqlText = commonSql,
            )
        }

        val commonSqlFile = appConfig.commonSqlFile?.trim()?.takeIf { it.isNotEmpty() }
        if (commonSqlFile != null) {
            sqlFiles[commonSqlFile]?.takeIf { it.isNotBlank() }?.let { sqlText ->
                drafts[commonSqlFile] = SqlAssetDraft(
                    assetKey = commonSqlFile,
                    assetKind = "COMMON",
                    label = "Общий SQL",
                    sqlText = sqlText,
                )
            }
        }

        appConfig.sources.forEach { source ->
            val sourceName = source.name.trim().ifEmpty { "source" }
            val inlineSql = source.sql?.trim()?.takeIf { it.isNotEmpty() }
            val sqlFile = source.sqlFile?.trim()?.takeIf { it.isNotEmpty() }
            when {
                inlineSql != null -> {
                    val assetKey = sourceInlineSqlAssetKey(sourceName)
                    drafts[assetKey] = SqlAssetDraft(
                        assetKey = assetKey,
                        assetKind = "SOURCE",
                        label = "Источник: $sourceName",
                        sqlText = inlineSql,
                    )
                }
                sqlFile != null -> {
                    sqlFiles[sqlFile]?.takeIf { it.isNotBlank() }?.let { sqlText ->
                        drafts[sqlFile] = SqlAssetDraft(
                            assetKey = sqlFile,
                            assetKind = "SOURCE",
                            label = "Источник: $sourceName",
                            sqlText = sqlText,
                        )
                    }
                }
            }
        }

        sqlFiles.toSortedMap().forEach { (path, content) ->
            if (!drafts.containsKey(path) && content.isNotBlank()) {
                drafts[path] = SqlAssetDraft(
                    assetKey = path,
                    assetKind = if (path == commonSqlFile) "COMMON" else "SOURCE",
                    label = path,
                    sqlText = content,
                )
            }
        }

        val assetIds = linkedMapOf<String, String>()
        drafts.values.forEachIndexed { sortOrder, draft ->
            val assetId = UUID.randomUUID().toString()
            val contentHash = MessageDigest.getInstance("SHA-256")
                .digest(draft.sqlText.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            connection.prepareStatement(ModuleRegistrySql.copySqlAssets(normalizedSchema)).use { stmt ->
                stmt.setString(1, assetId)
                stmt.setString(2, revisionId)
                stmt.setString(3, draft.assetKind)
                stmt.setString(4, draft.assetKey)
                stmt.setString(5, draft.label)
                stmt.setString(6, draft.sqlText)
                stmt.setInt(7, sortOrder)
                stmt.setString(8, contentHash)
                stmt.executeUpdate()
            }
            assetIds[draft.assetKey] = assetId
        }
        return assetIds
    }

    fun insertRevisionStructure(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        configText: String,
        sqlAssetIds: Map<String, String>,
    ) {
        val appConfig = parseAppConfig(configText)
        val commonAssetKey = appConfig.commonSqlFile?.trim()?.takeIf { it.isNotEmpty() }
            ?: appConfig.commonSql.trim().takeIf { it.isNotEmpty() }?.let { "commonSql" }
        val insertedSourceNames = mutableSetOf<String>()

        appConfig.sources.forEachIndexed { index, source ->
            val sourceName = source.name.trim()
            val sqlAssetId = resolveSourceSqlAssetId(source, commonAssetKey, sqlAssetIds)
            if (sourceName.isBlank() || source.jdbcUrl.isBlank() || source.username.isBlank() || source.password.isBlank() || sqlAssetId == null) {
                return@forEachIndexed
            }

            connection.prepareStatement(ModuleRegistrySql.insertRevisionSource(normalizedSchema)).use { stmt ->
                stmt.setString(1, revisionId)
                stmt.setString(2, sourceName)
                stmt.setInt(3, index)
                stmt.setString(4, source.jdbcUrl)
                stmt.setString(5, source.username)
                stmt.setString(6, source.password)
                stmt.setString(7, sqlAssetId)
                stmt.executeUpdate()
            }
            insertedSourceNames += sourceName
        }

        insertRevisionTarget(connection, normalizedSchema, revisionId, appConfig)

        appConfig.quotas.forEachIndexed { index, quota ->
            val sourceName = quota.source.trim()
            if (sourceName.isBlank() || sourceName !in insertedSourceNames || quota.percent <= 0.0 || quota.percent > 100.0) {
                return@forEachIndexed
            }
            connection.prepareStatement(ModuleRegistrySql.insertRevisionQuota(normalizedSchema)).use { stmt ->
                stmt.setString(1, revisionId)
                stmt.setString(2, sourceName)
                stmt.setBigDecimal(3, BigDecimal.valueOf(quota.percent))
                stmt.setInt(4, index)
                stmt.executeUpdate()
            }
        }
    }

    fun contentHashForCreate(request: CreateModuleRequest): String {
        val input = buildString {
            append(request.configText)
            append('\u0000')
            append(request.title)
            append('\u0000')
            append(request.description.orEmpty())
            append('\u0000')
            append(request.tags.orEmpty().joinToString("|"))
            append('\u0000')
            append(request.hiddenFromUi)
            request.sqlFiles.toSortedMap().forEach { (path, content) ->
                append('\n')
                append(path)
                append('\u0000')
                append(content)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun insertRevisionTarget(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        appConfig: AppConfig,
    ) {
        val target = appConfig.target
        val hasCompleteTarget = target.jdbcUrl.isNotBlank() &&
            target.username.isNotBlank() &&
            target.password.isNotBlank() &&
            target.table.isNotBlank()
        connection.prepareStatement(ModuleRegistrySql.insertRevisionTarget(normalizedSchema)).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setBoolean(2, target.enabled && hasCompleteTarget)
            stmt.setString(3, target.jdbcUrl)
            stmt.setString(4, target.username)
            stmt.setString(5, target.password)
            stmt.setString(6, target.table)
            stmt.setBoolean(7, target.truncateBeforeLoad)
            stmt.executeUpdate()
        }
    }

    private fun resolveSourceSqlAssetId(
        source: SourceConfig,
        commonAssetKey: String?,
        sqlAssetIds: Map<String, String>,
    ): String? {
        val sourceName = source.name.trim()
        val sourceInlineSql = source.sql?.trim()?.takeIf { it.isNotEmpty() }
        val sourceSqlFile = source.sqlFile?.trim()?.takeIf { it.isNotEmpty() }
        val assetKey = when {
            sourceInlineSql != null -> sourceInlineSqlAssetKey(sourceName)
            sourceSqlFile != null -> sourceSqlFile
            else -> commonAssetKey
        }
        return assetKey?.let { sqlAssetIds[it] }
    }

    private fun parseAppConfig(configText: String): AppConfig =
        try {
            ConfigLoader().objectMapper().readValue(configText, RootConfig::class.java).app
        } catch (_: Exception) {
            AppConfig()
        }

    private fun buildSnapshotJson(
        configText: String,
        sqlFileContents: Map<String, String>,
        title: String,
        description: String?,
        tags: List<String>,
        hiddenFromUi: Boolean,
    ): String {
        val sqlLabels = SqlFileReferenceExtractor.labelsByPathOrEmpty(configText, objectMapper)
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
        root.put("title", title)
        root.put("description", description)
        root.set<JsonNode>("tags", objectMapper.valueToTree(tags))
        root.put("hiddenFromUi", hiddenFromUi)
        root.set<JsonNode>("sqlFiles", objectMapper.valueToTree(sqlFiles))
        return objectMapper.writeValueAsString(root)
    }

    private fun readSnapshot(workingCopyJson: String): WorkingCopySnapshot =
        objectMapper.readValue(workingCopyJson, WorkingCopySnapshot::class.java)

    private fun sourceInlineSqlAssetKey(sourceName: String): String = "source:$sourceName"

    private fun setNullableInt(statement: PreparedStatement, index: Int, value: Int?) {
        if (value == null) {
            statement.setNull(index, Types.INTEGER)
        } else {
            statement.setInt(index, value)
        }
    }

    private fun setNullableLong(statement: PreparedStatement, index: Int, value: Long?) {
        if (value == null) {
            statement.setNull(index, Types.BIGINT)
        } else {
            statement.setLong(index, value)
        }
    }
}
