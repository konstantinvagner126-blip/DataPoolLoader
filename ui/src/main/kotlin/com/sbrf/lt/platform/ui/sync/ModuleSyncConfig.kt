package com.sbrf.lt.platform.ui.sync

import java.nio.file.Path

/**
 * Параметры запуска импорта файловых модулей в DB-реестр.
 */
data class ModuleSyncConfig(
    val appsRoot: Path,
    val includeHidden: Boolean = false,
)
