package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.datapool.model.AppConfig
import java.security.MessageDigest
import java.sql.Connection
import java.util.UUID

/**
 * Записывает SQL-assets ревизии DB-модуля в registry.
 */
internal class DatabaseModuleRevisionSqlAssetSupport {
    fun insertRevisionSqlAssets(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        appConfig: AppConfig,
        sqlFiles: Map<String, String>,
    ): Map<String, String> {
        val drafts = buildAssetDrafts(appConfig, sqlFiles)
        val assetIds = linkedMapOf<String, String>()
        drafts.values.forEachIndexed { sortOrder, draft ->
            val assetId = UUID.randomUUID().toString()
            connection.prepareStatement(ModuleRegistrySql.copySqlAssets(normalizedSchema)).use { stmt ->
                stmt.setString(1, assetId)
                stmt.setString(2, revisionId)
                stmt.setString(3, draft.assetKind)
                stmt.setString(4, draft.assetKey)
                stmt.setString(5, draft.label)
                stmt.setString(6, draft.sqlText)
                stmt.setInt(7, sortOrder)
                stmt.setString(8, sha256Hex(draft.sqlText))
                stmt.executeUpdate()
            }
            assetIds[draft.assetKey] = assetId
        }
        return assetIds
    }

    private fun buildAssetDrafts(
        appConfig: AppConfig,
        sqlFiles: Map<String, String>,
    ): LinkedHashMap<String, SqlAssetDraft> {
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
        return drafts
    }

    private fun sourceInlineSqlAssetKey(sourceName: String): String = "source:$sourceName"

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
