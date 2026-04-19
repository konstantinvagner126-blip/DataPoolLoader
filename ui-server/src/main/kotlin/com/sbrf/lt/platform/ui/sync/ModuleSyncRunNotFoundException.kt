package com.sbrf.lt.platform.ui.sync

import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException

internal class ModuleSyncRunNotFoundException(
    syncRunId: String,
) : UiEntityNotFoundException("История импорта '$syncRunId' не найдена.")
