package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.platform.ui.config.UiRuntimeContext

/**
 * Ответ на переключение режима UI с обновленным runtime context.
 */
data class UiRuntimeModeUpdateResponse(
    val message: String,
    val runtimeContext: UiRuntimeContext,
)
