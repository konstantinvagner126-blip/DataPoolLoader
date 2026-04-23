package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreCatalogSelectionSupport {
    fun resolveInitialSelectedModuleId(
        preferredId: String?,
        moduleIds: List<String>,
    ): String? =
        when {
            preferredId != null && moduleIds.contains(preferredId) -> preferredId
            else -> moduleIds.firstOrNull()
        }

    fun resolveRefreshedSelectedModuleId(
        currentSelectedId: String?,
        moduleIds: List<String>,
    ): String? =
        currentSelectedId
            ?.takeIf { moduleId -> moduleIds.contains(moduleId) }
            ?: moduleIds.firstOrNull()
}
