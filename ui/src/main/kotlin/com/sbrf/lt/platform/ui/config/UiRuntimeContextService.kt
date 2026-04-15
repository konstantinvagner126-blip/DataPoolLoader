package com.sbrf.lt.platform.ui.config

data class UiRuntimeActorState(
    val resolved: Boolean,
    val actorId: String? = null,
    val actorSource: String? = null,
    val actorDisplayName: String? = null,
    val requiresManualInput: Boolean = false,
    val message: String,
)

data class UiRuntimeContext(
    val requestedMode: UiModuleStoreMode,
    val effectiveMode: UiModuleStoreMode,
    val fallbackReason: String? = null,
    val actor: UiRuntimeActorState,
    val database: UiDatabaseConnectionStatus,
)

open class UiRuntimeContextService(
    private val actorResolver: UiActorResolver = UiActorResolver(),
    private val connectionChecker: UiDatabaseConnectionChecker = UiDatabaseConnectionChecker(),
    private val schemaMigrator: UiDatabaseSchemaMigrator = UiDatabaseSchemaMigrator(),
) {
    open fun resolve(uiConfig: UiAppConfig): UiRuntimeContext {
        val actorState = resolveActor()
        val databaseState = resolveDatabase(uiConfig.moduleStore)
        val requestedMode = uiConfig.moduleStore.mode
        val effectiveMode = determineEffectiveMode(requestedMode, actorState, databaseState)
        val fallbackReason = determineFallbackReason(requestedMode, effectiveMode, actorState, databaseState)

        return UiRuntimeContext(
            requestedMode = requestedMode,
            effectiveMode = effectiveMode,
            fallbackReason = fallbackReason,
            actor = actorState,
            database = databaseState,
        )
    }

    private fun resolveActor(): UiRuntimeActorState {
        val actor = actorResolver.resolveAutomaticActor()
        return if (actor != null) {
            UiRuntimeActorState(
                resolved = true,
                actorId = actor.actorId,
                actorSource = actor.actorSource.name,
                actorDisplayName = actor.actorDisplayName,
                message = "Пользователь ОС определен автоматически.",
            )
        } else {
            UiRuntimeActorState(
                resolved = false,
                requiresManualInput = true,
                message = "Не удалось определить пользователя ОС. Для DB-режима нужен ручной ввод actorId.",
            )
        }
    }

    private fun resolveDatabase(moduleStore: UiModuleStoreConfig): UiDatabaseConnectionStatus {
        val initialStatus = connectionChecker.check(moduleStore.postgres)
        if (moduleStore.mode != UiModuleStoreMode.DATABASE || !initialStatus.available) {
            return initialStatus
        }

        return try {
            schemaMigrator.migrate(moduleStore.postgres)
            initialStatus
        } catch (ex: Exception) {
            initialStatus.copy(
                available = false,
                message = "PostgreSQL registry доступен, но schema migration завершилась ошибкой.",
                errorMessage = ex.message,
            )
        }
    }

    private fun determineEffectiveMode(
        requestedMode: UiModuleStoreMode,
        actorState: UiRuntimeActorState,
        databaseState: UiDatabaseConnectionStatus,
    ): UiModuleStoreMode {
        if (requestedMode == UiModuleStoreMode.FILES) {
            return UiModuleStoreMode.FILES
        }
        if (!databaseState.available) {
            return UiModuleStoreMode.FILES
        }
        if (!actorState.resolved) {
            return UiModuleStoreMode.FILES
        }
        return UiModuleStoreMode.DATABASE
    }

    private fun determineFallbackReason(
        requestedMode: UiModuleStoreMode,
        effectiveMode: UiModuleStoreMode,
        actorState: UiRuntimeActorState,
        databaseState: UiDatabaseConnectionStatus,
    ): String? {
        if (requestedMode != UiModuleStoreMode.DATABASE || effectiveMode == UiModuleStoreMode.DATABASE) {
            return null
        }
        if (!databaseState.available) {
            return databaseState.errorMessage ?: databaseState.message
        }
        if (!actorState.resolved) {
            return actorState.message
        }
        return "Режим database недоступен, UI переключен в files."
    }
}
