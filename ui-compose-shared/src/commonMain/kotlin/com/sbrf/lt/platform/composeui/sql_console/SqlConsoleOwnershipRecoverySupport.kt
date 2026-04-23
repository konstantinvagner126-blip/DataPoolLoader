package com.sbrf.lt.platform.composeui.sql_console

fun canRestoreSqlConsoleExecutionOwnership(
    storedOwnerSessionId: String,
    storedOwnerTabInstanceId: String?,
    currentOwnerSessionId: String,
    currentOwnerTabInstanceId: String,
): Boolean =
    storedOwnerSessionId == currentOwnerSessionId &&
        storedOwnerTabInstanceId != null &&
        storedOwnerTabInstanceId == currentOwnerTabInstanceId
