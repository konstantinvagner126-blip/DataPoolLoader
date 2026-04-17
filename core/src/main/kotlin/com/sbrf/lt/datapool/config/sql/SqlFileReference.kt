package com.sbrf.lt.datapool.config.sql

/**
 * Ссылка на SQL-ресурс, извлеченная из YAML-конфига.
 */
data class SqlFileReference(
    val label: String,
    val path: String,
)
