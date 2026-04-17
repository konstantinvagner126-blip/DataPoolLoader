package com.sbrf.lt.datapool.module.sync

/**
 * Текущее состояние import-flow `files -> database` для UI.
 */
data class ModuleSyncState(
    val maintenanceMode: Boolean = false,
    val activeFullSync: ActiveModuleSyncRun? = null,
    val activeSingleSyncs: List<ActiveModuleSyncRun> = emptyList(),
    val message: String = if (maintenanceMode) {
        "Работа с DB-модулями временно недоступна: идет массовый импорт модулей в БД."
    } else {
        "Массовый импорт модулей сейчас не выполняется."
    },
) {
    fun activeSingleSync(moduleCode: String?): ActiveModuleSyncRun? =
        moduleCode?.let { code -> activeSingleSyncs.firstOrNull { it.moduleCode == code } }
}
