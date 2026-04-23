package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSqlResourceRenameSupport(
    private val formSyncStore: ModuleEditorSqlResourceFormSyncStore,
    private val namingSupport: ModuleEditorStoreSqlResourceNamingSupport,
) {
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

        val nextState = formSyncStore.applySqlResourceRename(
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
}
