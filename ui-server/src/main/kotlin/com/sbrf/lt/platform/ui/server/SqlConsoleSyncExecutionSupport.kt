package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryResponse
import com.sbrf.lt.platform.ui.model.toResponse

internal fun SqlConsoleQueryRequest.toTransactionMode(): SqlConsoleTransactionMode =
    runCatching { SqlConsoleTransactionMode.valueOf(transactionMode.uppercase()) }
        .getOrDefault(SqlConsoleTransactionMode.AUTO_COMMIT)

internal fun UiServerContext.executeSqlConsoleQuery(
    request: SqlConsoleQueryRequest,
): SqlConsoleQueryResponse =
    withSqlConsoleCredentialsPath("datapool-ui-sql-console-") { credentialsPath ->
        sqlConsoleService.executeQuery(
            rawSql = request.sql,
            credentialsPath = credentialsPath,
            selectedSourceNames = request.selectedSourceNames,
            executionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
            transactionMode = request.toTransactionMode(),
        ).toResponse()
    }
