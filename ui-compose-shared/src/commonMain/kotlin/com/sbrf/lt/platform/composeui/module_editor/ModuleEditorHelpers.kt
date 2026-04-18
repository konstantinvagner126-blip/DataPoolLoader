package com.sbrf.lt.platform.composeui.module_editor

fun buildDraftSqlFiles(state: ModuleEditorPageState): List<ModuleFileContent> {
    val session = state.session ?: return emptyList()
    val persistedSqlFiles = session.module.sqlFiles.associateBy { it.path }
    return state.sqlContentsDraft.entries.sortedBy { it.key }.map { (path, content) ->
        val persisted = persistedSqlFiles[path]
        ModuleFileContent(
            label = persisted?.label ?: defaultSqlLabel(path),
            path = path,
            content = content,
            exists = persisted?.exists ?: true,
        )
    }
}

fun buildSqlUsageBadges(
    state: ModuleEditorPageState,
    path: String?,
): List<SqlUsageBadge> {
    if (path.isNullOrBlank()) {
        return listOf(SqlUsageBadge("Сведения об использовании пока недоступны.", true))
    }
    val formState = state.configFormState ?: return listOf(SqlUsageBadge("Не используется", true))
    val usages = buildList {
        if (formState.commonSqlFile == path) {
            add("SQL по умолчанию")
        }
        formState.sources.forEach { source ->
            if (source.sqlFile == path) {
                add("Источник: ${source.name}")
            }
        }
    }
    return if (usages.isEmpty()) {
        listOf(SqlUsageBadge("Не используется", true))
    } else {
        usages.map { SqlUsageBadge(it, false) }
    }
}

fun defaultSqlLabel(path: String): String =
    path.replace('\\', '/').substringAfterLast('/')

data class SqlUsageBadge(
    val label: String,
    val muted: Boolean,
)
