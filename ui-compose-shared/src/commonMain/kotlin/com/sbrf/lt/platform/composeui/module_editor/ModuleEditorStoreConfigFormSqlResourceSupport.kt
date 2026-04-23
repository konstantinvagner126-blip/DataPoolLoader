package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreConfigFormSqlResourceSupport {
    fun buildSqlResourceUsages(
        formState: ConfigFormStateDto?,
        path: String,
    ): List<String> {
        if (formState == null) {
            return emptyList()
        }
        return buildList {
            if (formState.commonSqlFile == path) {
                add("SQL по умолчанию")
            }
            formState.sources.forEach { source ->
                if (source.sqlFile == path) {
                    add("Источник: ${source.name}")
                }
            }
        }
    }

    fun sqlResourceUsedByForm(
        formState: ConfigFormStateDto,
        path: String,
    ): Boolean =
        formState.commonSqlFile == path || formState.sources.any { it.sqlFile == path }

    fun renameSqlResourceInFormState(
        formState: ConfigFormStateDto,
        currentPath: String,
        nextPath: String,
    ): ConfigFormStateDto =
        formState.copy(
            commonSqlFile = formState.commonSqlFile
                ?.takeUnless { it == currentPath }
                ?: nextPath.takeIf { formState.commonSqlFile == currentPath },
            sources = formState.sources.map { source ->
                if (source.sqlFile == currentPath) {
                    source.copy(sqlFile = nextPath)
                } else {
                    source
                }
            },
        )
}
