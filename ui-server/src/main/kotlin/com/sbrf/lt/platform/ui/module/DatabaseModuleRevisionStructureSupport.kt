package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.SourceConfig
import java.math.BigDecimal
import java.sql.Connection

/**
 * Записывает sources, target и quotas ревизии DB-модуля в registry.
 */
internal class DatabaseModuleRevisionStructureSupport {
    fun insertRevisionStructure(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        appConfig: AppConfig,
        sqlAssetIds: Map<String, String>,
    ) {
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
            sourceInlineSql != null -> "source:$sourceName"
            sourceSqlFile != null -> sourceSqlFile
            else -> commonAssetKey
        }
        return assetKey?.let(sqlAssetIds::get)
    }
}
