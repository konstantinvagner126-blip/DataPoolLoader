package com.sbrf.lt.platform.ui.model

/**
 * Состояние одного source в визуальной форме конфигурации.
 */
data class ConfigFormSourceState(
    val name: String,
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val sql: String?,
    val sqlFile: String?,
)
