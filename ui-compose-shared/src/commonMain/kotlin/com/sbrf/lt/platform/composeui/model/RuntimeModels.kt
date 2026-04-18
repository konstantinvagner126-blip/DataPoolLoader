package com.sbrf.lt.platform.composeui.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModuleStoreMode {
    @SerialName("files")
    FILES,

    @SerialName("database")
    DATABASE,
}

@Serializable
data class RuntimeActorState(
    val resolved: Boolean,
    val actorId: String? = null,
    val actorSource: String? = null,
    val actorDisplayName: String? = null,
    val requiresManualInput: Boolean = false,
    val message: String,
)

@Serializable
data class DatabaseConnectionStatus(
    val configured: Boolean,
    val available: Boolean,
    val schema: String,
    val message: String,
    val errorMessage: String? = null,
)

@Serializable
data class RuntimeContext(
    val requestedMode: ModuleStoreMode,
    val effectiveMode: ModuleStoreMode,
    val fallbackReason: String? = null,
    val actor: RuntimeActorState,
    val database: DatabaseConnectionStatus,
)

@Serializable
data class ModuleCatalogDiagnostics(
    val totalModules: Int = 0,
    val validModules: Int = 0,
    val warningModules: Int = 0,
    val invalidModules: Int = 0,
    val totalIssues: Int = 0,
)

@Serializable
data class ModuleCatalogItem(
    val id: String,
    val descriptor: ModuleMetadataDescriptor,
    val validationStatus: String = "VALID",
    val hasActiveRun: Boolean = false,
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

@Serializable
data class AppsRootStatus(
    val mode: String,
    val configuredPath: String? = null,
    val message: String,
)

@Serializable
data class FilesModulesCatalogResponse(
    val appsRootStatus: AppsRootStatus,
    val diagnostics: ModuleCatalogDiagnostics = ModuleCatalogDiagnostics(),
    val modules: List<ModuleCatalogItem>,
)

@Serializable
data class DatabaseModulesCatalogResponse(
    val runtimeContext: RuntimeContext,
    val diagnostics: ModuleCatalogDiagnostics = ModuleCatalogDiagnostics(),
    val modules: List<ModuleCatalogItem>,
)

@Serializable
data class CredentialsStatusResponse(
    val mode: String,
    val displayName: String,
    val fileAvailable: Boolean,
    val uploaded: Boolean,
)

@Serializable
data class RuntimeModeUpdateRequest(
    val mode: ModuleStoreMode,
)

@Serializable
data class RuntimeModeUpdateResponse(
    val message: String,
    val runtimeContext: RuntimeContext,
)
