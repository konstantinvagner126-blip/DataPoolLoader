package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorConfigFormSnapshotStore {
    suspend fun loadSnapshot(configText: String): ConfigFormSnapshot
}

internal class ModuleEditorStoreConfigFormSnapshotSupport(
    private val api: ModuleEditorApi,
) : ModuleEditorConfigFormSnapshotStore {
    override suspend fun loadSnapshot(configText: String): ConfigFormSnapshot =
        runCatching {
            ConfigFormSnapshot(
                state = api.parseConfigForm(configText),
                errorMessage = null,
            )
        }.getOrElse { error ->
            ConfigFormSnapshot(
                state = null,
                errorMessage = error.message ?: "Не удалось собрать визуальную форму.",
            )
        }
}
