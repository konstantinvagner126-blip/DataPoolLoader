package com.sbrf.lt.platform.ui.model

/**
 * Запрос на запуск DB-модуля из UI.
 * `launchSourceKind` зарезервирован под будущий явный выбор CURRENT_REVISION/WORKING_COPY.
 */
data class DatabaseRunStartRequest(
    val launchSourceKind: String? = null,
)
