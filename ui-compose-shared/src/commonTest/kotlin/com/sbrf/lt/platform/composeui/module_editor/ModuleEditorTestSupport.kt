package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.DatabaseConnectionStatus
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.ModuleCatalogDiagnostics
import com.sbrf.lt.platform.composeui.model.ModuleCatalogItem
import com.sbrf.lt.platform.composeui.model.ModuleMetadataDescriptor
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeActorState
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import com.sbrf.lt.platform.composeui.model.AppsRootStatus
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal fun sampleModuleEditorRuntimeContext(
    effectiveMode: ModuleStoreMode = ModuleStoreMode.FILES,
): RuntimeContext =
    RuntimeContext(
        requestedMode = effectiveMode,
        effectiveMode = effectiveMode,
        actor = RuntimeActorState(
            resolved = true,
            message = "ok",
        ),
        database = DatabaseConnectionStatus(
            configured = true,
            available = true,
            schema = "public",
            message = "ok",
        ),
    )

internal fun sampleModuleEditorCatalogItem(id: String): ModuleCatalogItem =
    ModuleCatalogItem(
        id = id,
        descriptor = ModuleMetadataDescriptor(
            title = id,
        ),
    )

internal fun sampleModuleEditorFilesCatalog(
    moduleIds: List<String> = listOf("module-a", "module-b"),
): FilesModulesCatalogResponse =
    FilesModulesCatalogResponse(
        appsRootStatus = AppsRootStatus(
            mode = "ok",
            message = "ok",
        ),
        diagnostics = ModuleCatalogDiagnostics(),
        modules = moduleIds.map(::sampleModuleEditorCatalogItem),
    )

internal fun sampleModuleEditorDatabaseCatalog(
    moduleIds: List<String> = listOf("module-a", "module-b"),
): DatabaseModulesCatalogResponse =
    DatabaseModulesCatalogResponse(
        runtimeContext = sampleModuleEditorRuntimeContext(ModuleStoreMode.DATABASE),
        diagnostics = ModuleCatalogDiagnostics(),
        modules = moduleIds.map(::sampleModuleEditorCatalogItem),
    )

internal fun sampleModuleEditorConfigFormState(): ConfigFormStateDto =
    ConfigFormStateDto(
        outputDir = "/tmp/out",
        fileFormat = "csv",
        mergeMode = "APPEND",
        errorMode = "FAIL_FAST",
        parallelism = 2,
        fetchSize = 100,
        queryTimeoutSec = 30,
        progressLogEveryRows = 500,
        maxMergedRows = 1_000,
        deleteOutputFilesAfterCompletion = false,
        commonSql = "select 1",
        targetEnabled = false,
        targetJdbcUrl = "",
        targetUsername = "",
        targetPassword = "",
        targetTable = "",
        targetTruncateBeforeLoad = false,
    )

internal fun sampleModuleEditorSession(
    moduleId: String = "module-a",
    storageMode: String = "files",
    configText: String = "demo-config",
): ModuleEditorSessionResponse =
    ModuleEditorSessionResponse(
        storageMode = storageMode,
        module = ModuleDetailsResponse(
            id = moduleId,
            descriptor = ModuleMetadataDescriptor(
                title = moduleId,
            ),
            configPath = "application.yml",
            configText = configText,
            sqlFiles = listOf(
                ModuleFileContent(
                    label = "sql",
                    path = "sql/main.sql",
                    content = "select 1",
                    exists = true,
                ),
            ),
            requiresCredentials = false,
            credentialsStatus = CredentialsStatusResponse(
                mode = "none",
                displayName = "none",
                fileAvailable = false,
                uploaded = false,
            ),
        ),
        capabilities = ModuleLifecycleCapabilities(),
    )

internal class StubModuleEditorApi(
    private val loadFilesCatalogHandler: suspend () -> FilesModulesCatalogResponse = {
        error("loadFilesCatalog not configured")
    },
    private val loadDatabaseCatalogHandler: suspend (Boolean) -> DatabaseModulesCatalogResponse = {
        error("loadDatabaseCatalog not configured")
    },
    private val loadRuntimeContextHandler: suspend () -> RuntimeContext = {
        error("loadRuntimeContext not configured")
    },
    private val loadFilesSessionHandler: suspend (String) -> ModuleEditorSessionResponse = {
        error("loadFilesSession not configured")
    },
    private val loadDatabaseSessionHandler: suspend (String) -> ModuleEditorSessionResponse = {
        error("loadDatabaseSession not configured")
    },
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
    private val startFilesRunHandler: suspend (StartRunRequestDto) -> UiRunSnapshotDto = {
        error("startFilesRun not configured")
    },
    private val startDatabaseRunHandler: suspend (String) -> DatabaseRunStartResponseDto = {
        error("startDatabaseRun not configured")
    },
    private val createDatabaseModuleHandler: suspend (CreateDbModuleRequestDto) -> CreateDbModuleResponseDto = {
        error("createDatabaseModule not configured")
    },
    private val deleteDatabaseModuleHandler: suspend (String) -> DeleteModuleResponseDto = {
        error("deleteDatabaseModule not configured")
    },
    private val parseConfigFormHandler: suspend (String) -> ConfigFormStateDto = {
        error("parseConfigForm not configured")
    },
) : ModuleEditorApi {
    override suspend fun loadFilesCatalog(): FilesModulesCatalogResponse = loadFilesCatalogHandler()

    override suspend fun loadDatabaseCatalog(includeHidden: Boolean): DatabaseModulesCatalogResponse =
        loadDatabaseCatalogHandler(includeHidden)

    override suspend fun loadRuntimeContext(): RuntimeContext = loadRuntimeContextHandler()

    override suspend fun loadFilesSession(moduleId: String): ModuleEditorSessionResponse =
        loadFilesSessionHandler(moduleId)

    override suspend fun loadDatabaseSession(moduleId: String): ModuleEditorSessionResponse =
        loadDatabaseSessionHandler(moduleId)

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
        createDatabaseModuleHandler(request)

    override suspend fun deleteDatabaseModule(moduleId: String): DeleteModuleResponseDto =
        deleteDatabaseModuleHandler(moduleId)

    override suspend fun startFilesRun(request: StartRunRequestDto): UiRunSnapshotDto =
        startFilesRunHandler(request)

    override suspend fun startDatabaseRun(moduleId: String): DatabaseRunStartResponseDto =
        startDatabaseRunHandler(moduleId)

    override suspend fun parseConfigForm(configText: String): ConfigFormStateDto =
        parseConfigFormHandler(configText)

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
