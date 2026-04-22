package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSqlResourceSupport(
    private val configFormSupport: ModuleEditorStoreConfigFormSupport,
) {
    fun createSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState {
        val nextPath = normalizeSqlResourceKey(rawName)
            ?: return current.copy(errorMessage = null, successMessage = null)
        if (current.sqlContentsDraft.containsKey(nextPath)) {
            return current.copy(
                errorMessage = "SQL-ресурс '$nextPath' уже существует.",
                successMessage = null,
            )
        }
        return current.copy(
            errorMessage = null,
            successMessage = "Создан SQL-ресурс '$nextPath'. Сохрани модуль, чтобы зафиксировать изменения.",
            selectedSqlPath = nextPath,
            sqlContentsDraft = sortSqlContents(current.sqlContentsDraft + (nextPath to "")),
        )
    }

    suspend fun renameSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState {
        val currentPath = current.selectedSqlPath
            ?: return current.copy(errorMessage = "Сначала выбери SQL-ресурс.", successMessage = null)
        val nextPath = normalizeSqlResourceKey(rawName, currentPath)
            ?: return current.copy(errorMessage = null, successMessage = null)
        if (nextPath == currentPath) {
            return current.copy(errorMessage = null, successMessage = null)
        }
        if (current.sqlContentsDraft.containsKey(nextPath)) {
            return current.copy(
                errorMessage = "SQL-ресурс '$nextPath' уже существует.",
                successMessage = null,
            )
        }

        val renamedSqlContents = current.sqlContentsDraft.toMutableMap().also { contents ->
            val currentValue = contents.remove(currentPath).orEmpty()
            contents[nextPath] = currentValue
        }.let(::sortSqlContents)

        val nextState = configFormSupport.applySqlResourceRename(
            current = current.copy(
                errorMessage = null,
                successMessage = null,
                selectedSqlPath = nextPath,
                sqlContentsDraft = renamedSqlContents,
            ),
            currentPath = currentPath,
            nextPath = nextPath,
        )

        return nextState.copy(
            errorMessage = null,
            successMessage = "SQL-ресурс переименован: '$currentPath' -> '$nextPath'.",
        )
    }

    fun deleteSqlResource(current: ModuleEditorPageState): ModuleEditorPageState {
        val currentPath = current.selectedSqlPath
            ?: return current.copy(errorMessage = "Сначала выбери SQL-ресурс.", successMessage = null)
        val usages = configFormSupport.buildSqlResourceUsages(current.configFormState, currentPath)
        if (usages.isNotEmpty()) {
            return current.copy(
                errorMessage = "Нельзя удалить SQL-ресурс, пока он используется: ${usages.joinToString(", ")}.",
                successMessage = null,
            )
        }

        val nextSqlContents = current.sqlContentsDraft
            .filterKeys { it != currentPath }
            .let(::sortSqlContents)
        return current.copy(
            errorMessage = null,
            successMessage = "SQL-ресурс '$currentPath' удален. Сохрани модуль, чтобы зафиксировать изменения.",
            selectedSqlPath = nextSqlContents.keys.firstOrNull(),
            sqlContentsDraft = nextSqlContents,
        )
    }

    private fun normalizeSqlResourceKey(
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

    private fun sortSqlContents(sqlContents: Map<String, String>): Map<String, String> =
        sqlContents.entries
            .sortedBy { it.key }
            .associateTo(LinkedHashMap()) { it.toPair() }
}
