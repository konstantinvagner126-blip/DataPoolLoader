package com.sbrf.lt.platform.composeui.module_sync

internal class ModuleSyncStoreActionSelectionSupport {
    fun resolveSingleModuleCode(selectedModuleCodes: Set<String>): String? =
        selectedModuleCodes.singleOrNull()?.trim().orEmpty().ifBlank { null }

    fun resolveSelectedModuleCodes(selectedModuleCodes: Set<String>): List<String> =
        selectedModuleCodes
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
}
