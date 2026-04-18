package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode

/**
 * Запрос на переключение предпочитаемого режима UI между FILES и DATABASE.
 */
data class UiRuntimeModeUpdateRequest(
    val mode: UiModuleStoreMode,
)
