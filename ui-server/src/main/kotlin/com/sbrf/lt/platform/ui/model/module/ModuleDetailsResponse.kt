package com.sbrf.lt.platform.ui.model

/**
 * Детальная модель модуля для общего editor shell.
 */
data class ModuleDetailsResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
    val validationStatus: String = "VALID",
    val validationIssues: List<ModuleValidationIssueResponse> = emptyList(),
    val configPath: String,
    val configText: String,
    val sqlFiles: List<ModuleFileContent>,
    val requiresCredentials: Boolean,
    val credentialsStatus: CredentialsStatusResponse,
    val requiredCredentialKeys: List<String> = emptyList(),
    val missingCredentialKeys: List<String> = emptyList(),
    val credentialsReady: Boolean = true,
)
