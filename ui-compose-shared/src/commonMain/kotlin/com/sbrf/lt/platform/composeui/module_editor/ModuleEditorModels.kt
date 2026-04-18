package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.ModuleCatalogItem
import com.sbrf.lt.platform.composeui.model.ModuleMetadataDescriptor
import kotlinx.serialization.Serializable

@Serializable
data class ModuleLifecycleCapabilities(
    val save: Boolean = false,
    val saveWorkingCopy: Boolean = false,
    val discardWorkingCopy: Boolean = false,
    val publish: Boolean = false,
    val run: Boolean = false,
    val createModule: Boolean = false,
    val deleteModule: Boolean = false,
)

@Serializable
data class ModuleValidationIssueResponse(
    val severity: String,
    val message: String,
)

@Serializable
data class ModuleFileContent(
    val label: String,
    val path: String,
    val content: String,
    val exists: Boolean,
)

@Serializable
data class ModuleDetailsResponse(
    val id: String,
    val descriptor: ModuleMetadataDescriptor,
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

@Serializable
data class ModuleEditorSessionResponse(
    val storageMode: String,
    val module: ModuleDetailsResponse,
    val capabilities: ModuleLifecycleCapabilities,
    val sourceKind: String? = null,
    val currentRevisionId: String? = null,
    val workingCopyId: String? = null,
    val workingCopyStatus: String? = null,
    val baseRevisionId: String? = null,
)

data class ModuleEditorRouteState(
    val storage: String,
    val moduleId: String? = null,
    val includeHidden: Boolean = false,
    val openCreateDialog: Boolean = false,
)

enum class ModuleEditorTab(
    val label: String,
) {
    SETTINGS("Настройки модуля"),
    SQL("SQL"),
    CONFIG("application.yml"),
    META("Метаданные"),
}

data class ModuleMetadataDraft(
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
)

data class CreateModuleDraft(
    val moduleCode: String = "",
    val title: String = "",
    val description: String = "",
    val tagsText: String = "",
    val hiddenFromUi: Boolean = true,
    val configText: String = defaultCreateModuleConfigTemplate(),
)

data class ModuleEditorPageState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val actionInProgress: String? = null,
    val filesCatalog: FilesModulesCatalogResponse? = null,
    val databaseCatalog: DatabaseModulesCatalogResponse? = null,
    val selectedModuleId: String? = null,
    val session: ModuleEditorSessionResponse? = null,
    val activeTab: ModuleEditorTab = ModuleEditorTab.SETTINGS,
    val selectedSqlPath: String? = null,
    val configTextDraft: String = "",
    val sqlContentsDraft: Map<String, String> = emptyMap(),
    val metadataDraft: ModuleMetadataDraft = ModuleMetadataDraft(),
    val createModuleDialogOpen: Boolean = false,
    val createModuleDraft: CreateModuleDraft = CreateModuleDraft(),
    val configFormState: ConfigFormStateDto? = null,
    val configFormLoading: Boolean = false,
    val configFormError: String? = null,
    val configFormSourceText: String = "",
) {
    val modules: List<ModuleCatalogItem>
        get() = filesCatalog?.modules ?: databaseCatalog?.modules.orEmpty()

    val hasDraftChanges: Boolean
        get() {
            val module = session?.module ?: return false
            val persistedSql = module.sqlFiles.associate { it.path to it.content }
            return configTextDraft != module.configText ||
                sqlContentsDraft != persistedSql ||
                metadataDraft.title != module.title ||
                metadataDraft.description != (module.description ?: "") ||
                metadataDraft.tags != module.tags ||
                metadataDraft.hiddenFromUi != module.hiddenFromUi
        }

    val configFormNeedsSync: Boolean
        get() = configTextDraft != configFormSourceText
}
