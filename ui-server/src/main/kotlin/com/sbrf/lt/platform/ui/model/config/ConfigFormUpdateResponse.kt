package com.sbrf.lt.platform.ui.model

/**
 * Результат синхронизации YAML-конфига и визуальной формы.
 */
data class ConfigFormUpdateResponse(
    val configText: String,
    val formState: ConfigFormStateResponse,
)
