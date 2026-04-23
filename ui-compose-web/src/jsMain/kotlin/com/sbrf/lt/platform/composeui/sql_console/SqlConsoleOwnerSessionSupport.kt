package com.sbrf.lt.platform.composeui.sql_console

import kotlin.js.Date
import kotlin.random.Random

internal fun generateSqlConsoleOwnerSessionId(): String =
    "sql-owner-${Date.now().toLong().toString(36)}-${Random.nextLong().toString(36)}"
