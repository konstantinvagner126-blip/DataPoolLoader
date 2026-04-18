package com.sbrf.lt.platform.ui.model

/**
 * Единая backend-модель редактора модуля для FILES и DATABASE.
 */
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
