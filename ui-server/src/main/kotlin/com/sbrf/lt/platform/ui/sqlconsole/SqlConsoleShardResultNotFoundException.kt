package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException

internal class SqlConsoleShardResultNotFoundException(
    shardName: String,
) : UiEntityNotFoundException("Результат для source '$shardName' не найден.")
