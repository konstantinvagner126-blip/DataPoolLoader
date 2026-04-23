package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal class StubModuleEditorApi(
    private val saveFilesModuleHandler: suspend (String, SaveModuleRequestDto) -> SaveResultResponseDto = { _, _ ->
        error("saveFilesModule not configured")
    },
    private val saveDatabaseWorkingCopyHandler: suspend (String, SaveModuleRequestDto) -> SaveResultResponseDto = { _, _ ->
        error("saveDatabaseWorkingCopy not configured")
    },
    private val discardDatabaseWorkingCopyHandler: suspend (String) -> SaveResultResponseDto = {
        error("discardDatabaseWorkingCopy not configured")
    },
    private val publishDatabaseWorkingCopyHandler: suspend (String) -> SaveResultResponseDto = {
        error("publishDatabaseWorkingCopy not configured")
    },
) : ModuleEditorApi {
    override suspend fun loadFilesCatalog(): FilesModulesCatalogResponse = error("loadFilesCatalog not configured")

    override suspend fun loadDatabaseCatalog(includeHidden: Boolean): DatabaseModulesCatalogResponse =
        error("loadDatabaseCatalog not configured")

    override suspend fun loadRuntimeContext(): RuntimeContext = error("loadRuntimeContext not configured")

    override suspend fun loadFilesSession(moduleId: String): ModuleEditorSessionResponse =
        error("loadFilesSession not configured")

    override suspend fun loadDatabaseSession(moduleId: String): ModuleEditorSessionResponse =
        error("loadDatabaseSession not configured")

    override suspend fun saveFilesModule(
        moduleId: String,
        request: SaveModuleRequestDto,
    ): SaveResultResponseDto = saveFilesModuleHandler(moduleId, request)

    override suspend fun saveDatabaseWorkingCopy(
        moduleId: String,
        request: SaveModuleRequestDto,
    ): SaveResultResponseDto = saveDatabaseWorkingCopyHandler(moduleId, request)

    override suspend fun discardDatabaseWorkingCopy(moduleId: String): SaveResultResponseDto =
        discardDatabaseWorkingCopyHandler(moduleId)

    override suspend fun publishDatabaseWorkingCopy(moduleId: String): SaveResultResponseDto =
        publishDatabaseWorkingCopyHandler(moduleId)

    override suspend fun createDatabaseModule(request: CreateDbModuleRequestDto): CreateDbModuleResponseDto =
        error("createDatabaseModule not configured")

    override suspend fun deleteDatabaseModule(moduleId: String): DeleteModuleResponseDto =
        error("deleteDatabaseModule not configured")

    override suspend fun startFilesRun(request: StartRunRequestDto): UiRunSnapshotDto =
        error("startFilesRun not configured")

    override suspend fun startDatabaseRun(moduleId: String): DatabaseRunStartResponseDto =
        error("startDatabaseRun not configured")

    override suspend fun parseConfigForm(configText: String): ConfigFormStateDto =
        error("parseConfigForm not configured")

    override suspend fun applyConfigForm(
        configText: String,
        formState: ConfigFormStateDto,
    ): ConfigFormUpdateResponseDto = error("applyConfigForm not configured")
}

internal class StubModuleEditorSelectedModuleRefreshStore(
    private val handler: suspend (ModuleEditorPageState, ModuleEditorRouteState, String) -> ModuleEditorPageState,
) : ModuleEditorSelectedModuleRefreshStore {
    override suspend fun refreshSelectedModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        successMessage: String,
    ): ModuleEditorPageState = handler(current, route, successMessage)
}

internal class StubModuleEditorSqlResourceFormSyncStore(
    private val usagesHandler: (ConfigFormStateDto?, String) -> List<String> = { _, _ -> emptyList() },
    private val renameHandler: suspend (ModuleEditorPageState, String, String) -> ModuleEditorPageState = { current, _, _ ->
        current
    },
) : ModuleEditorSqlResourceFormSyncStore {
    override fun buildSqlResourceUsages(
        formState: ConfigFormStateDto?,
        path: String,
    ): List<String> = usagesHandler(formState, path)

    override suspend fun applySqlResourceRename(
        current: ModuleEditorPageState,
        currentPath: String,
        nextPath: String,
    ): ModuleEditorPageState = renameHandler(current, currentPath, nextPath)
}

internal fun <T> runModuleEditorSuspend(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                completed = result
            }
        },
    )
    return completed!!.getOrThrow()
}
