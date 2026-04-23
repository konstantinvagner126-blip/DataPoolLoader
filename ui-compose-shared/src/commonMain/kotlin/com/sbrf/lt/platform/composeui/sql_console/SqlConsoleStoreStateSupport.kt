package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleStoreStateSupport {
    fun startLoading(current: SqlConsolePageState): SqlConsolePageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun updateDraftSql(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(draftSql = value)

    fun updateSelectedSources(
        current: SqlConsolePageState,
        sourceName: String,
        enabled: Boolean,
    ): SqlConsolePageState {
        val selectionUpdate = toggleSelectedSourceWithGroups(
            groups = current.info?.groups.orEmpty(),
            currentSelectedGroupNames = current.selectedGroupNames,
            currentSelectedSourceNames = current.selectedSourceNames,
            manuallyIncludedSourceNames = current.manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = current.manuallyExcludedSourceNames,
            sourceName = sourceName,
            enabled = enabled,
        )
        return current.copy(
            selectedSourceNames = selectionUpdate.selectedSourceNames,
            selectedGroupNames = selectionUpdate.selectedGroupNames,
            manuallyIncludedSourceNames = selectionUpdate.manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = selectionUpdate.manuallyExcludedSourceNames,
        )
    }

    fun updateSelectedSourceGroup(
        current: SqlConsolePageState,
        group: SqlConsoleSourceGroup,
        enabled: Boolean,
    ): SqlConsolePageState {
        val selectionUpdate = toggleSelectedSourceGroupNames(
            groups = current.info?.groups.orEmpty(),
            currentSelectedGroupNames = current.selectedGroupNames,
            currentSelectedSourceNames = current.selectedSourceNames,
            manuallyIncludedSourceNames = current.manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = current.manuallyExcludedSourceNames,
            group = group,
            enabled = enabled,
        )
        return current.copy(
            selectedSourceNames = selectionUpdate.selectedSourceNames,
            selectedGroupNames = selectionUpdate.selectedGroupNames,
            manuallyIncludedSourceNames = selectionUpdate.manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = selectionUpdate.manuallyExcludedSourceNames,
        )
    }

    fun updatePageSize(
        current: SqlConsolePageState,
        pageSize: Int,
    ): SqlConsolePageState =
        current.copy(pageSize = normalizePageSize(pageSize))

    fun updateStrictSafety(
        current: SqlConsolePageState,
        enabled: Boolean,
    ): SqlConsolePageState =
        current.copy(strictSafetyEnabled = enabled)

    fun updateAutoCommitEnabled(
        current: SqlConsolePageState,
        enabled: Boolean,
    ): SqlConsolePageState =
        current.copy(
            transactionMode = if (enabled) "AUTO_COMMIT" else "TRANSACTION_PER_SHARD",
        )

    fun updateMaxRowsPerShardDraft(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(maxRowsPerShardDraft = value)

    fun updateQueryTimeoutDraft(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(queryTimeoutSecDraft = value)

    fun beginAction(
        current: SqlConsolePageState,
        actionName: String,
    ): SqlConsolePageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)
}
