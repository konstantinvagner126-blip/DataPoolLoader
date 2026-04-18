package com.sbrf.lt.platform.ui.model

/**
 * Диагностика текущего состояния каталога `apps` для файлового режима.
 */
data class AppsRootStatusResponse(
    val mode: String,
    val configuredPath: String? = null,
    val message: String,
)
