package com.sbrf.lt.platform.ui.model

/**
 * Детальная модель модуля для общего editor shell.
 */
data class ModuleDetailsResponse(
    val id: String,
    val descriptor: ModuleMetadataDescriptorResponse,
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
) {
    val title: String
        get() = descriptor.title

    val description: String?
        get() = descriptor.description

    val tags: List<String>
        get() = descriptor.tags

    val hiddenFromUi: Boolean
        get() = descriptor.hiddenFromUi
}
