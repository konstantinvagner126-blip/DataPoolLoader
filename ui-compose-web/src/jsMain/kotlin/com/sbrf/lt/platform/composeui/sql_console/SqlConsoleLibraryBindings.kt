package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.foundation.http.uploadCredentialsFile
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.w3c.files.File

internal class SqlConsoleLibraryBindings(
    private val context: SqlConsolePageBindingContext,
) {
    fun checkConnections() {
        context.scope.launch {
            val checkingState = context.store.beginAction(context.currentState(), "check-connections")
            context.setState(context.store.checkConnections(checkingState))
        }
    }

    fun updateMaxRowsDraft(value: String) {
        context.updateState { context.store.updateMaxRowsPerShardDraft(it, value) }
    }

    fun updateTimeoutDraft(value: String) {
        context.updateState { context.store.updateQueryTimeoutDraft(it, value) }
    }

    fun saveSettings() {
        context.scope.launch {
            val savingState = context.store.beginAction(context.currentState(), "save-settings")
            context.setState(context.store.saveSettings(savingState))
        }
    }

    fun toggleSource(sourceName: String, selected: Boolean) {
        context.updateState { context.store.updateSelectedSources(it, sourceName, selected) }
    }

    fun toggleSourceGroup(group: SqlConsoleSourceGroup, selected: Boolean) {
        context.updateState { context.store.updateSelectedSourceGroup(it, group, selected) }
    }

    fun selectCredentialsFile(file: File?) {
        context.updateUiState { it.copy(selectedCredentialsFile = file) }
    }

    fun uploadCredentials() {
        val file = context.currentUiState().selectedCredentialsFile ?: return
        context.scope.launch {
            context.updateUiState {
                it.copy(
                    credentialsUploadInProgress = true,
                    credentialsMessage = null,
                )
            }
            runCatching {
                uploadCredentialsFile(context.httpClient, file)
            }.onSuccess { uploaded ->
                context.updateUiState {
                    it.copy(
                        credentialsStatus = uploaded,
                        credentialsMessage = "credential.properties загружен.",
                        credentialsMessageLevel = "success",
                        credentialsUploadInProgress = false,
                    )
                }
                context.setState(context.store.checkConnections(context.currentState().copy(actionInProgress = null)))
            }.onFailure { error ->
                context.updateUiState {
                    it.copy(
                        credentialsMessage = error.message ?: "Не удалось загрузить credential.properties.",
                        credentialsMessageLevel = "warning",
                        credentialsUploadInProgress = false,
                    )
                }
            }
        }
    }

    fun selectRecent(selected: String) {
        context.updateUiState { it.copy(selectedRecentQuery = selected) }
    }

    fun selectFavorite(selected: String) {
        context.updateUiState { it.copy(selectedFavoriteQuery = selected) }
    }

    fun applyRecent() {
        context.updateState { context.store.applyRecentQuery(it, context.currentUiState().selectedRecentQuery) }
        context.focusEditor()
    }

    fun applyFavorite() {
        context.updateState { context.store.applyFavoriteQuery(it, context.currentUiState().selectedFavoriteQuery) }
        context.focusEditor()
    }

    fun rememberFavorite() {
        context.updateState { context.store.rememberFavoriteQuery(it) }
    }

    fun removeFavorite() {
        context.updateState { context.store.removeFavoriteQuery(it, context.currentUiState().selectedFavoriteQuery) }
        context.updateUiState { it.copy(selectedFavoriteQuery = "") }
    }

    fun clearRecent() {
        context.updateState { context.store.clearRecentQueries(it) }
        context.updateUiState { it.copy(selectedRecentQuery = "") }
    }

    fun toggleStrictSafety() {
        context.updateState { context.store.updateStrictSafety(it, !it.strictSafetyEnabled) }
    }

    fun toggleAutoCommit(enabled: Boolean) {
        context.updateState { context.store.updateAutoCommitEnabled(it, enabled) }
    }

    fun openFavoriteMetadata(favorite: SqlConsoleFavoriteObject) {
        window.location.href = buildFavoriteMetadataHref(favorite)
    }

    fun removeFavoriteObject(favorite: SqlConsoleFavoriteObject) {
        context.updateState { context.store.removeFavoriteObject(it, favorite) }
    }
}
