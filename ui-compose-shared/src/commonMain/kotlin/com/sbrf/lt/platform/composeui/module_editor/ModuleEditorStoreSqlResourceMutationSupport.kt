package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSqlResourceMutationSupport(
    private val configFormSupport: ModuleEditorStoreConfigFormSupport,
    private val namingSupport: ModuleEditorStoreSqlResourceNamingSupport,
) {
    fun createSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState {
        val nextPath = namingSupport.normalizeSqlResourceKey(rawName)
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
            sqlContentsDraft = namingSupport.sortSqlContents(current.sqlContentsDraft + (nextPath to "")),
        )
    }

    suspend fun renameSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState {
        val currentPath = current.selectedSqlPath
            ?: return current.copy(errorMessage = "Сначала выбери SQL-ресурс.", successMessage = null)
        val nextPath = namingSupport.normalizeSqlResourceKey(rawName, currentPath)
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
        }.let(namingSupport::sortSqlContents)

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
            .let(namingSupport::sortSqlContents)
        return current.copy(
            errorMessage = null,
            successMessage = "SQL-ресурс '$currentPath' удален. Сохрани модуль, чтобы зафиксировать изменения.",
            selectedSqlPath = nextSqlContents.keys.firstOrNull(),
            sqlContentsDraft = nextSqlContents,
        )
    }
}
