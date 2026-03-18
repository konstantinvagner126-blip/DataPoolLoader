package com.example.datapoolloader.db

import com.example.datapoolloader.model.TargetConfig
import java.sql.DriverManager

class TargetTableValidator {
    fun validate(
        target: TargetConfig,
        resolvedJdbcUrl: String,
        resolvedUsername: String,
        resolvedPassword: String,
        incomingColumns: List<String>,
    ) {
        require(incomingColumns.isNotEmpty()) { "Incoming data must contain at least one column." }

        val tableRef = parseTableReference(target.table)
        DriverManager.getConnection(resolvedJdbcUrl, resolvedUsername, resolvedPassword).use { connection ->
            val columns = connection.prepareStatement(
                """
                select column_name, is_nullable, column_default
                from information_schema.columns
                where table_schema = ? and table_name = ?
                order by ordinal_position
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, tableRef.schema)
                statement.setString(2, tableRef.table)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                TargetColumn(
                                    name = resultSet.getString("column_name"),
                                    nullable = resultSet.getString("is_nullable").equals("YES", ignoreCase = true),
                                    hasDefault = resultSet.getString("column_default") != null,
                                )
                            )
                        }
                    }
                }
            }

            validateCompatibility(target.table, columns, incomingColumns)
        }
    }

    internal fun validateCompatibility(
        targetTable: String,
        targetColumns: List<TargetColumn>,
        incomingColumns: List<String>,
    ) {
        require(targetColumns.isNotEmpty()) { "Target table $targetTable was not found or has no columns." }

        val targetByName = targetColumns.associateBy { it.name }
        val missingInTarget = incomingColumns.filter { it !in targetByName }
        require(missingInTarget.isEmpty()) {
            "Target table $targetTable does not contain incoming columns: ${missingInTarget.joinToString(", ")}"
        }

        val missingRequired = targetColumns
            .filter { !it.nullable && !it.hasDefault && it.name !in incomingColumns }
            .map { it.name }
        require(missingRequired.isEmpty()) {
            "Target table $targetTable requires non-null columns missing in incoming data: ${missingRequired.joinToString(", ")}"
        }
    }

    internal fun parseTableReference(rawTable: String): TableReference {
        val safeIdentifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val parts = rawTable.trim().split('.')
        require(parts.size in 1..2) { "Target table must be in format table or schema.table." }
        parts.forEach {
            require(safeIdentifier.matches(it)) { "Unsupported table identifier '$it'." }
        }
        return if (parts.size == 1) {
            TableReference(schema = "public", table = parts[0])
        } else {
            TableReference(schema = parts[0], table = parts[1])
        }
    }
}

data class TargetColumn(
    val name: String,
    val nullable: Boolean,
    val hasDefault: Boolean,
)

data class TableReference(
    val schema: String,
    val table: String,
)
