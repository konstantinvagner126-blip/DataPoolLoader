package com.sbrf.lt.platform.composeui.sql_console

internal const val DEFAULT_SQL_CONSOLE_DRAFT: String = "select 1 as check_value"

enum class SqlConsoleSourceGroupSelectionState {
    NONE,
    PARTIAL,
    ALL,
}

data class SqlConsoleSourceSelectionUpdate(
    val selectedSourceNames: List<String>,
    val selectedGroupNames: List<String>,
    val manuallyIncludedSourceNames: List<String>,
    val manuallyExcludedSourceNames: List<String>,
)

internal fun defaultSqlConsoleStateSnapshot(): SqlConsoleStateSnapshot =
    SqlConsoleStateSnapshot(draftSql = DEFAULT_SQL_CONSOLE_DRAFT)

internal fun SqlConsolePageState.toPersistedState(): SqlConsoleStateUpdate =
    SqlConsoleStateUpdate(
        draftSql = draftSql,
        recentQueries = recentQueries,
        favoriteQueries = favoriteQueries,
        favoriteObjects = favoriteObjects,
        selectedGroupNames = selectedGroupNames,
        selectedSourceNames = selectedSourceNames,
        pageSize = pageSize,
        strictSafetyEnabled = strictSafetyEnabled,
        transactionMode = transactionMode,
    )

internal fun SqlConsoleStateSnapshot.toStateUpdate(
    draftSql: String = this.draftSql,
    selectedGroupNames: List<String> = this.selectedGroupNames.orEmpty(),
    selectedSourceNames: List<String>,
    favoriteObjects: List<SqlConsoleFavoriteObject>,
): SqlConsoleStateUpdate =
    SqlConsoleStateUpdate(
        draftSql = draftSql,
        recentQueries = recentQueries,
        favoriteQueries = favoriteQueries,
        favoriteObjects = favoriteObjects,
        selectedGroupNames = selectedGroupNames,
        selectedSourceNames = selectedSourceNames,
        pageSize = pageSize,
        strictSafetyEnabled = strictSafetyEnabled,
        transactionMode = transactionMode,
    )

internal fun rememberQuery(
    current: List<String>,
    sql: String,
    limit: Int,
): List<String> =
    listOf(sql) + current.filterNot { it == sql }.take(limit - 1)

internal fun normalizePageSize(value: Int): Int =
    when (value) {
        25, 50, 100 -> value
        else -> 50
    }

internal fun normalizeTransactionMode(value: String): String =
    when (value.uppercase()) {
        "TRANSACTION_PER_SHARD" -> "TRANSACTION_PER_SHARD"
        else -> "AUTO_COMMIT"
    }

internal fun SqlConsoleFavoriteObject.matches(other: SqlConsoleFavoriteObject): Boolean =
    sourceName == other.sourceName &&
        schemaName == other.schemaName &&
        objectName == other.objectName &&
        objectType == other.objectType &&
        tableName == other.tableName

fun SqlConsoleInfo.sourceCatalogNames(): List<String> =
    sourceCatalog.map { it.name.trim() }.filter { it.isNotEmpty() }

internal fun toggleSelectedSourceNames(
    current: List<String>,
    sourceName: String,
    enabled: Boolean,
): List<String> =
    if (enabled) {
        (current + sourceName).distinct()
    } else {
        current.filterNot { it == sourceName }
    }

fun initializeSelectedSourceState(
    groups: List<SqlConsoleSourceGroup>,
    selectedSourceNames: List<String>,
): SqlConsoleSourceSelectionUpdate {
    val normalizedSelectedSourceNames = selectedSourceNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val normalizedGroups = groups.normalizeSourceGroups()
    val selectedGroupNames = normalizedGroups
        .filter { group -> group.sources.isNotEmpty() && group.sources.all { it in normalizedSelectedSourceNames } }
        .map { it.name }
    return buildSelectedSourceState(
        groups = normalizedGroups,
        selectedGroupNames = selectedGroupNames,
        manuallyIncludedSourceNames = normalizedSelectedSourceNames,
        manuallyExcludedSourceNames = emptyList(),
    )
}

fun restoreSelectedSourceState(
    groups: List<SqlConsoleSourceGroup>,
    selectedGroupNames: List<String>?,
    selectedSourceNames: List<String>,
): SqlConsoleSourceSelectionUpdate {
    if (selectedGroupNames == null) {
        return initializeSelectedSourceState(groups, selectedSourceNames)
    }
    val normalizedGroups = groups.normalizeSourceGroups()
    val selectedByGroups = selectedSourcesFromGroups(normalizedGroups, selectedGroupNames)
    val normalizedSelectedSourceNames = selectedSourceNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val manualInclusions = normalizedSelectedSourceNames
        .filterNot { it in selectedByGroups.toSet() }
    val manualExclusions = selectedByGroups
        .filterNot { it in normalizedSelectedSourceNames.toSet() }
    return buildSelectedSourceState(
        groups = normalizedGroups,
        selectedGroupNames = selectedGroupNames,
        manuallyIncludedSourceNames = manualInclusions,
        manuallyExcludedSourceNames = manualExclusions,
    )
}

internal fun toggleSelectedSourceGroupNames(
    groups: List<SqlConsoleSourceGroup>,
    currentSelectedGroupNames: List<String>,
    currentSelectedSourceNames: List<String>,
    manuallyIncludedSourceNames: List<String>,
    manuallyExcludedSourceNames: List<String>,
    group: SqlConsoleSourceGroup,
    enabled: Boolean,
): SqlConsoleSourceSelectionUpdate {
    val normalizedGroups = groups.normalizeSourceGroups()
    val normalizedSelectedGroupNames = currentSelectedGroupNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val normalizedGroupName = group.name.trim()
    if (normalizedGroupName.isBlank()) {
        return buildSelectedSourceState(
            groups = normalizedGroups,
            selectedGroupNames = normalizedSelectedGroupNames,
            manuallyIncludedSourceNames = manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = manuallyExcludedSourceNames,
        )
    }
    val nextSelectedGroupNames = if (enabled) {
        (normalizedSelectedGroupNames + normalizedGroupName).distinct()
    } else {
        normalizedSelectedGroupNames.filterNot { it == normalizedGroupName }
    }
    return buildSelectedSourceState(
        groups = normalizedGroups,
        selectedGroupNames = nextSelectedGroupNames,
        manuallyIncludedSourceNames = manuallyIncludedSourceNames,
        manuallyExcludedSourceNames = manuallyExcludedSourceNames,
    )
}

internal fun toggleSelectedSourceWithGroups(
    groups: List<SqlConsoleSourceGroup>,
    currentSelectedGroupNames: List<String>,
    currentSelectedSourceNames: List<String>,
    manuallyIncludedSourceNames: List<String>,
    manuallyExcludedSourceNames: List<String>,
    sourceName: String,
    enabled: Boolean,
): SqlConsoleSourceSelectionUpdate {
    val normalizedGroups = groups.normalizeSourceGroups()
    val normalizedSourceName = sourceName.trim()
    if (normalizedSourceName.isBlank()) {
        return buildSelectedSourceState(
            groups = normalizedGroups,
            selectedGroupNames = currentSelectedGroupNames,
            manuallyIncludedSourceNames = manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = manuallyExcludedSourceNames,
        )
    }
    val sourcesSelectedByGroups = selectedSourcesFromGroups(normalizedGroups, currentSelectedGroupNames).toSet()
    val normalizedManualInclusions = manuallyIncludedSourceNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val normalizedManualExclusions = manuallyExcludedSourceNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val nextManualInclusions = normalizedManualInclusions.toMutableSet()
    val nextManualExclusions = normalizedManualExclusions.toMutableSet()
    if (enabled) {
        nextManualExclusions.remove(normalizedSourceName)
        if (normalizedSourceName !in sourcesSelectedByGroups) {
            nextManualInclusions.add(normalizedSourceName)
        }
    } else {
        nextManualInclusions.remove(normalizedSourceName)
        if (normalizedSourceName in sourcesSelectedByGroups) {
            nextManualExclusions.add(normalizedSourceName)
        } else {
            nextManualExclusions.remove(normalizedSourceName)
        }
    }
    return buildSelectedSourceState(
        groups = normalizedGroups,
        selectedGroupNames = currentSelectedGroupNames,
        manuallyIncludedSourceNames = nextManualInclusions.toList(),
        manuallyExcludedSourceNames = nextManualExclusions.toList(),
    )
}

fun sourceGroupSelectionState(
    group: SqlConsoleSourceGroup,
    selectedSourceNames: List<String>,
): SqlConsoleSourceGroupSelectionState {
    val groupSourceNames = group.sources
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (groupSourceNames.isEmpty()) {
        return SqlConsoleSourceGroupSelectionState.NONE
    }
    val selectedCount = groupSourceNames.count { it in selectedSourceNames }
    return when {
        selectedCount == 0 -> SqlConsoleSourceGroupSelectionState.NONE
        selectedCount == groupSourceNames.size -> SqlConsoleSourceGroupSelectionState.ALL
        else -> SqlConsoleSourceGroupSelectionState.PARTIAL
    }
}

private fun buildSelectedSourceState(
    groups: List<SqlConsoleSourceGroup>,
    selectedGroupNames: List<String>,
    manuallyIncludedSourceNames: List<String>,
    manuallyExcludedSourceNames: List<String>,
): SqlConsoleSourceSelectionUpdate {
    val normalizedGroups = groups.normalizeSourceGroups()
    val normalizedSelectedGroupNames = selectedGroupNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val selectedByGroups = selectedSourcesFromGroups(normalizedGroups, normalizedSelectedGroupNames)
    val normalizedManuallyIncludedSourceNames = manuallyIncludedSourceNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .filterNot { it in selectedByGroups.toSet() }
    val normalizedManuallyExcludedSourceNames = manuallyExcludedSourceNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .filter { it in selectedByGroups.toSet() }
    val selectedSourceNames = (selectedByGroups + normalizedManuallyIncludedSourceNames)
        .filterNot { it in normalizedManuallyExcludedSourceNames.toSet() }
        .distinct()
    return SqlConsoleSourceSelectionUpdate(
        selectedSourceNames = selectedSourceNames,
        selectedGroupNames = normalizedSelectedGroupNames,
        manuallyIncludedSourceNames = normalizedManuallyIncludedSourceNames,
        manuallyExcludedSourceNames = normalizedManuallyExcludedSourceNames,
    )
}

private fun List<SqlConsoleSourceGroup>.normalizeSourceGroups(): List<SqlConsoleSourceGroup> =
    map { group ->
        group.copy(
            name = group.name.trim(),
            sources = group.sources.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        )
    }.filter { it.name.isNotBlank() }

private fun selectedSourcesFromGroups(
    groups: List<SqlConsoleSourceGroup>,
    selectedGroupNames: List<String>,
): List<String> {
    val groupNames = selectedGroupNames.toSet()
    return groups
        .filter { it.name in groupNames }
        .flatMap { it.sources }
        .distinct()
}
