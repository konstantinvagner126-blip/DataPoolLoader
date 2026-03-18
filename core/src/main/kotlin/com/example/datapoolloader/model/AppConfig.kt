package com.example.datapoolloader.model

data class RootConfig(
    val app: AppConfig = AppConfig(),
)

data class AppConfig(
    val outputDir: String = "./output",
    val fileFormat: String = "csv",
    val mergeMode: MergeMode = MergeMode.PLAIN,
    val errorMode: ErrorMode = ErrorMode.CONTINUE_ON_ERROR,
    val parallelism: Int = 5,
    val fetchSize: Int = 1000,
    val sql: String = "",
    val sources: List<SourceConfig> = emptyList(),
    val target: TargetConfig = TargetConfig(),
)

data class SourceConfig(
    val name: String = "",
    val jdbcUrl: String = "",
    val username: String = "",
    val password: String = "",
    val sql: String? = null,
)

data class TargetConfig(
    val jdbcUrl: String = "",
    val username: String = "",
    val password: String = "",
    val table: String = "",
    val truncateBeforeLoad: Boolean = false,
    val enabled: Boolean = true,
)

enum class MergeMode {
    PLAIN,
    ROUND_ROBIN,
}

enum class ErrorMode {
    CONTINUE_ON_ERROR,
}
