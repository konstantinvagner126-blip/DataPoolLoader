package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.UiSettingsResponse
import com.sbrf.lt.platform.ui.model.UiStateResponse
import com.sbrf.lt.platform.ui.module.ModuleRegistry

internal class RunManagerStateSupport(
    private val moduleRegistry: ModuleRegistry,
    private val uiConfig: UiAppConfig,
) {
    fun currentState(
        snapshots: List<MutableRunSnapshot>,
        credentialsStatus: CredentialsStatusResponse,
    ): UiStateResponse {
        val ordered = snapshots.sortedByDescending { it.startedAt }
        return UiStateResponse(
            credentialsStatus = credentialsStatus,
            uiSettings = UiSettingsResponse(
                showTechnicalDiagnostics = uiConfig.showTechnicalDiagnostics,
                showRawSummaryJson = uiConfig.showRawSummaryJson,
            ),
            activeRun = ordered.firstOrNull { it.status != com.sbrf.lt.datapool.model.ExecutionStatus.SUCCESS && it.status != com.sbrf.lt.datapool.model.ExecutionStatus.FAILED }?.toUi(),
            history = ordered.map { it.toUi() },
        )
    }

    fun loadModuleDetails(
        moduleId: String,
        credentialsStatus: CredentialsStatusResponse,
        credentialProperties: Map<String, String>,
    ): ModuleDetailsResponse {
        val details = moduleRegistry.loadModuleDetails(moduleId)
        val requirement = analyzeCredentialRequirements(details.configText, credentialProperties)
        return details.copy(
            requiresCredentials = requirement.requiresCredentials,
            credentialsStatus = credentialsStatus,
            requiredCredentialKeys = requirement.requiredKeys,
            missingCredentialKeys = requirement.missingKeys,
            credentialsReady = !requirement.requiresCredentials || requirement.ready,
        )
    }

    fun validateCredentialsBeforeRun(
        configText: String,
        credentialProperties: Map<String, String>,
        credentialsStatus: CredentialsStatusResponse,
    ) {
        val requirement = analyzeCredentialRequirements(configText, credentialProperties)
        if (!requirement.requiresCredentials) {
            return
        }
        require(requirement.ready) {
            buildMissingCredentialValuesMessage(
                subjectLabel = "модуля",
                missingKeys = requirement.missingKeys,
                credentialsStatus = credentialsStatus,
            )
        }
    }
}
