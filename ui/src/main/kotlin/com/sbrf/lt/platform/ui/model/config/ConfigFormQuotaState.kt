package com.sbrf.lt.platform.ui.model

/**
 * Квота одного источника в визуальной форме конфигурации.
 */
data class ConfigFormQuotaState(
    val source: String,
    val percent: Double?,
)
