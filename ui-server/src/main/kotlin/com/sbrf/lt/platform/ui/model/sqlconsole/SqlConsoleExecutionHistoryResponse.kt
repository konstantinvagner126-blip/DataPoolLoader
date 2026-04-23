package com.sbrf.lt.platform.ui.model

data class SqlConsoleExecutionHistoryResponse(
    val entries: List<SqlConsoleExecutionHistoryEntryResponse> = emptyList(),
)

data class SqlConsoleExecutionHistoryEntryResponse(
    val executionId: String,
    val sql: String,
    val selectedSourceNames: List<String> = emptyList(),
    val autoCommitEnabled: Boolean = true,
    val status: String,
    val transactionState: String = "NONE",
    val startedAt: String,
    val finishedAt: String? = null,
    val durationMillis: Long? = null,
    val errorMessage: String? = null,
)
