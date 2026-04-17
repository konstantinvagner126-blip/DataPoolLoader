package com.sbrf.lt.platform.ui.config

import java.sql.DriverManager

data class UiDatabaseConnectionStatus(
    val configured: Boolean,
    val available: Boolean,
    val schema: String,
    val message: String,
    val errorMessage: String? = null,
)

open class UiDatabaseConnectionChecker(
    private val probe: (UiModuleStorePostgresConfig) -> Unit = { config ->
        DriverManager.getConnection(config.jdbcUrl, config.username, config.password).use { connection ->
            connection.prepareStatement("select 1").use { statement ->
                statement.execute()
            }
        }
    },
) {
    open fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus {
        val schema = config.schemaName()
        if (!config.isConfigured()) {
            return UiDatabaseConnectionStatus(
                configured = false,
                available = false,
                schema = schema,
                message = "Параметры PostgreSQL registry не настроены.",
            )
        }

        val unresolvedFields = unresolvedPlaceholderFields(config)
        if (unresolvedFields.isNotEmpty()) {
            return UiDatabaseConnectionStatus(
                configured = true,
                available = false,
                schema = schema,
                message = "Параметры PostgreSQL registry содержат неразрешенные placeholders.",
                errorMessage = "Не удалось разрешить placeholders для полей: ${unresolvedFields.joinToString(", ")}.",
            )
        }

        return try {
            probe(config)
            UiDatabaseConnectionStatus(
                configured = true,
                available = true,
                schema = schema,
                message = "PostgreSQL registry доступен.",
            )
        } catch (ex: Exception) {
            UiDatabaseConnectionStatus(
                configured = true,
                available = false,
                schema = schema,
                message = "PostgreSQL registry недоступен.",
                errorMessage = ex.message,
            )
        }
    }

    private fun unresolvedPlaceholderFields(config: UiModuleStorePostgresConfig): List<String> = buildList {
        if (config.jdbcUrl?.isPlaceholder() == true) add("jdbcUrl")
        if (config.username?.isPlaceholder() == true) add("username")
        if (config.password?.isPlaceholder() == true) add("password")
    }

    private fun String.isPlaceholder(): Boolean =
        PLACEHOLDER_PATTERN.matches(trim())

    private companion object {
        val PLACEHOLDER_PATTERN = Regex("^\\$\\{[A-Za-z0-9_.-]+}$")
    }
}
