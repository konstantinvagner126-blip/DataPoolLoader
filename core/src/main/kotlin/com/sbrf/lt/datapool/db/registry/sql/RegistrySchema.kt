package com.sbrf.lt.datapool.db.registry.sql

/**
 * Нормализация имени schema для SQL registry.
 */
fun normalizeRegistrySchemaName(schema: String): String {
    val normalized = schema.trim()
    require(Regex("[A-Za-z_][A-Za-z0-9_]*").matches(normalized)) {
        "Некорректное имя schema PostgreSQL registry: $schema"
    }
    return normalized
}
