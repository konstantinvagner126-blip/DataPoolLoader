package com.sbrf.lt.platform.ui.sqlconsole

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class PersistedSqlConsoleWorkspaceState(
    val draftSql: String = "select 1 as check_value",
    val selectedGroupNames: List<String>? = null,
    val selectedSourceNames: List<String> = emptyList(),
    val lastAccessedAt: Instant? = null,
) {
    fun normalized(): PersistedSqlConsoleWorkspaceState =
        copy(
            draftSql = draftSql.ifBlank { "select 1 as check_value" },
            selectedGroupNames = selectedGroupNames
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct(),
            selectedSourceNames = selectedSourceNames
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
        )

    fun touched(at: Instant): PersistedSqlConsoleWorkspaceState = copy(lastAccessedAt = at)
}
