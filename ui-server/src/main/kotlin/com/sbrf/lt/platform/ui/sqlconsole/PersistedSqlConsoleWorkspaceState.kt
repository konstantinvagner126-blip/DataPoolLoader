package com.sbrf.lt.platform.ui.sqlconsole

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PersistedSqlConsoleWorkspaceState(
    val draftSql: String = "select 1 as check_value",
    val selectedSourceNames: List<String> = emptyList(),
) {
    fun normalized(): PersistedSqlConsoleWorkspaceState =
        copy(
            draftSql = draftSql.ifBlank { "select 1 as check_value" },
            selectedSourceNames = selectedSourceNames
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
        )
}
