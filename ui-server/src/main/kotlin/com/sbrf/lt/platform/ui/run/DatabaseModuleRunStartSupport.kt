package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import java.time.Instant
import java.util.UUID

internal class DatabaseModuleRunStartSupport(
    private val databaseModuleStore: DatabaseModuleRegistryOperations,
    private val executionSource: DatabaseModuleExecutionSource,
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val credentialsProvider: UiCredentialsProvider,
) {
    fun prepareStartContext(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        localActiveRunId: String?,
    ): DatabaseModuleRunContext {
        recoverOrphanRuns(moduleCode, localActiveRunId)
        require(!runExecutionStore.hasActiveRun(moduleCode)) {
            "Для модуля '$moduleCode' уже выполняется DB-запуск. Дождитесь его завершения."
        }

        val details = databaseModuleStore.loadModuleDetails(moduleCode, actorId, actorSource)
        validateCredentialsBeforeRun(details.module.configText)

        val runtimeSnapshot = executionSource.prepareExecution(
            moduleCode = moduleCode,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
        )
        val requestedAt = Instant.now()
        return DatabaseModuleRunContext(
            runId = UUID.randomUUID().toString(),
            runtimeSnapshot = runtimeSnapshot,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
            requestedAt = requestedAt,
            sourceOrder = runtimeSnapshot.appConfig.sources.mapIndexed { index, source -> source.name to index }.toMap(),
            targetStatus = if (runtimeSnapshot.appConfig.target.enabled) "PENDING" else "NOT_ENABLED",
        )
    }

    private fun validateCredentialsBeforeRun(configText: String) {
        val requirement = analyzeCredentialRequirements(configText, credentialsProvider.currentProperties())
        if (!requirement.requiresCredentials) {
            return
        }
        val status = credentialsProvider.currentCredentialsStatus()
        require(requirement.ready) {
            buildMissingCredentialValuesMessage(
                subjectLabel = "DB-модуля",
                missingKeys = requirement.missingKeys,
                credentialsStatus = status,
            )
        }
    }

    private fun recoverOrphanRuns(moduleCode: String, localActiveRunId: String?) {
        runExecutionStore.activeRunIds(moduleCode)
            .filter { it != localActiveRunId }
            .forEach { runId ->
                runExecutionStore.markRunFailed(
                    runId = runId,
                    finishedAt = Instant.now(),
                    errorMessage = "DB-запуск был прерван до завершения и восстановлен как FAILED при следующем старте UI.",
                )
            }
    }
}
