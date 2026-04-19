package com.sbrf.lt.platform.ui.model

data class SqlConsoleFavoriteObjectResponse(
    val sourceName: String,
    val schemaName: String,
    val objectName: String,
    val objectType: String,
    val tableName: String? = null,
)
