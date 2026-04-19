package com.sbrf.lt.datapool.sqlconsole

internal class SqlConsoleStateSupport {
    fun info(config: SqlConsoleConfig): SqlConsoleInfo =
        SqlConsoleInfo(
            configured = config.sources.isNotEmpty(),
            sourceNames = config.sources.map { it.name },
            maxRowsPerShard = config.maxRowsPerShard,
            queryTimeoutSec = config.queryTimeoutSec,
        )

    fun updateSettings(
        currentConfig: SqlConsoleConfig,
        maxRowsPerShard: Int,
        queryTimeoutSec: Int?,
    ): SqlConsoleConfig {
        require(maxRowsPerShard > 0) { "Лимит строк на source должен быть больше 0." }
        require(queryTimeoutSec == null || queryTimeoutSec > 0) {
            "Таймаут запроса на source должен быть больше 0, если задан."
        }
        return currentConfig.copy(
            maxRowsPerShard = maxRowsPerShard,
            queryTimeoutSec = queryTimeoutSec,
        )
    }
}
