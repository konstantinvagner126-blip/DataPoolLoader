package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import java.util.UUID

internal class DatabaseRunStoreEventPersistenceSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
    private val objectMapperWithTime: ObjectMapper,
) {
    fun appendEvent(
        runId: String,
        seqNo: Int,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        payload: Map<String, Any?>,
    ) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.insertEvent(normalizedSchema)).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, runId)
                stmt.setInt(3, seqNo)
                stmt.setString(4, stage)
                stmt.setString(5, eventType)
                stmt.setString(6, severity)
                stmt.setString(7, sourceName)
                stmt.setString(8, message)
                stmt.setString(9, objectMapperWithTime.writeValueAsString(payload))
                stmt.executeUpdate()
            }
        }
    }
}
