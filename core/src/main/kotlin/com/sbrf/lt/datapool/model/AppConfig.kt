package com.sbrf.lt.datapool.model

import com.fasterxml.jackson.annotation.JsonAlias

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
    val progressLogEveryRows: Long = 10_000,
    val maxMergedRows: Long? = null,
    @param:JsonAlias("sql")
    val commonSql: String = "",
    val sources: List<SourceConfig> = emptyList(),
    val quotas: List<SourceQuotaConfig> = emptyList(),
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

data class SourceQuotaConfig(
    val source: String = "",
    val percent: Double = 0.0,
)

enum class MergeMode {
    PLAIN,
    ROUND_ROBIN,
    PROPORTIONAL,
    QUOTA,
}

enum class ErrorMode {
    CONTINUE_ON_ERROR,
}
