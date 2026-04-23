package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSqlResourceNamingSupport {
    fun normalizeSqlResourceKey(
        rawName: String,
        fallbackValue: String = "",
    ): String? {
        val value = rawName.trim()
        if (value.isBlank()) {
            return null
        }
        if (value.startsWith("classpath:")) {
            return value
        }
        if (value.endsWith(".sql", ignoreCase = true)) {
            val normalized = value.removePrefix("/")
            return if (normalized.startsWith("sql/")) {
                "classpath:$normalized"
            } else {
                "classpath:sql/$normalized"
            }
        }

        val normalized = value
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("_+"), "-")
        val fallback = fallbackValue
            .removePrefix("classpath:sql/")
            .removeSuffix(".sql")
            .trim()
        val baseName = normalized.ifBlank { fallback }
        return baseName.takeIf { it.isNotBlank() }?.let { "classpath:sql/$it.sql" }
    }

    fun sortSqlContents(sqlContents: Map<String, String>): Map<String, String> =
        sqlContents.entries
            .sortedBy { it.key }
            .associateTo(LinkedHashMap()) { it.toPair() }
}
