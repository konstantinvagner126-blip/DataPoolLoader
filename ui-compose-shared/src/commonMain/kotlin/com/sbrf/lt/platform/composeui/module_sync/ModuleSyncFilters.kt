package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.model.ModuleCatalogItem

fun filterSelectableModules(state: ModuleSyncPageState): List<ModuleCatalogItem> =
    state.availableFileModules
        .sortedWith(compareBy<ModuleCatalogItem> { it.title.ifBlank { it.id } }.thenBy { it.id })
        .filter { module ->
            val query = state.moduleSearchQuery.trim()
            val description = module.description
            query.isBlank() ||
                module.id.contains(query, ignoreCase = true) ||
                module.title.contains(query, ignoreCase = true) ||
                (!description.isNullOrBlank() && description.contains(query, ignoreCase = true))
        }
