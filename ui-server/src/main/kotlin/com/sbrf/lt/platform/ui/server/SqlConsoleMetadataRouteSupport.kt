package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleConnectionCheckResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleObjectInspectorRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleObjectInspectorResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleObjectSearchRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleObjectSearchResponse
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectType

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

internal fun UiServerContext.inspectSqlConsoleObject(
    request: SqlConsoleObjectInspectorRequest,
): SqlConsoleObjectInspectorResponse =
    withSqlConsoleCredentialsPath("datapool-ui-sql-console-objects-") { credentialsPath ->
        sqlConsoleService.inspectObject(
            sourceName = request.sourceName,
            schemaName = request.schemaName,
            objectName = request.objectName,
            objectType = SqlConsoleDatabaseObjectType.valueOf(request.objectType.uppercase()),
            credentialsPath = credentialsPath,
        ).toResponse(request.sourceName)
    }
