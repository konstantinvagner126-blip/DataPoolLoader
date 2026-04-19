package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleConnectionCheckResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleObjectSearchRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleObjectSearchResponse
import com.sbrf.lt.platform.ui.model.toResponse

internal fun UiServerContext.checkSqlConsoleConnections(): SqlConsoleConnectionCheckResponse =
    withSqlConsoleCredentialsPath("datapool-ui-sql-console-check-") { credentialsPath ->
        sqlConsoleService.checkConnections(credentialsPath = credentialsPath).toResponse(
            configured = sqlConsoleService.info().configured,
        )
    }

internal fun UiServerContext.searchSqlConsoleObjects(
    request: SqlConsoleObjectSearchRequest,
): SqlConsoleObjectSearchResponse =
    withSqlConsoleCredentialsPath("datapool-ui-sql-console-objects-") { credentialsPath ->
        sqlConsoleService.searchObjects(
            rawQuery = request.query,
            credentialsPath = credentialsPath,
            selectedSourceNames = request.selectedSourceNames,
            maxObjectsPerSource = request.maxObjectsPerSource,
        ).toResponse()
    }
