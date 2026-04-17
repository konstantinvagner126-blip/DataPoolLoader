package com.sbrf.lt.platform.ui.model

/**
 * Запрос на применение изменений визуальной формы к YAML-конфигу.
 */
data class ConfigFormUpdateRequest(
    val configText: String,
    val formState: ConfigFormStateResponse,
)
