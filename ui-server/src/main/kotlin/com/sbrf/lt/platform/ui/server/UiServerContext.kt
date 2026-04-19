package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.db.registry.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.datapool.module.sync.ModuleSyncState
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import com.sbrf.lt.platform.ui.config.appsRootPath
import com.sbrf.lt.platform.ui.config.isConfigured
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.module.ConfigFormService
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import com.sbrf.lt.platform.ui.module.DatabaseModuleStore
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.module.backend.FilesModuleBackend
import com.sbrf.lt.platform.ui.module.backend.ModuleActor
import com.sbrf.lt.platform.ui.run.DatabaseModuleExecutionSource
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunService
import com.sbrf.lt.platform.ui.run.DatabaseOutputRetentionService
import com.sbrf.lt.platform.ui.run.DatabaseRunHistoryCleanupService
import com.sbrf.lt.platform.ui.run.DatabaseRunStore
import com.sbrf.lt.platform.ui.run.FilesModuleRunOperations
import com.sbrf.lt.platform.ui.run.FilesRunHistoryMaintenanceOperations
import com.sbrf.lt.platform.ui.run.FilesOutputRetentionService
import com.sbrf.lt.platform.ui.run.FilesRunHistoryCleanupService
import com.sbrf.lt.platform.ui.run.UiCredentialsService
import com.sbrf.lt.platform.ui.run.history.DatabaseModuleRunHistoryService
import com.sbrf.lt.platform.ui.run.history.ModuleRunHistoryService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleAsyncQueryOperations
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExportService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateService
import com.sbrf.lt.platform.ui.sync.DatabaseModuleSyncImporter
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations

/**
 * Общий runtime-контекст Ktor-сервера UI: dynamic config, доступ к сервисам и guard-методы.
 */
internal class UiServerContext(
    private val uiConfig: UiAppConfig,
    private val uiConfigLoader: UiConfigLoader,
    internal val credentialsService: UiCredentialsService,
    private val runtimeConfigResolver: UiRuntimeConfigResolver,
    private val runtimeContextService: UiRuntimeContextService,
    private val runtimeUiConfig: UiAppConfig,
    private val runtimeContext: UiRuntimeContext,
    internal val filesModuleBackend: FilesModuleBackend,
    private val databaseModuleStore: DatabaseModuleRegistryOperations?,
    private val databaseModuleBackend: DatabaseModuleBackend?,
    internal val filesRunService: FilesModuleRunOperations,
    private val filesRunHistoryMaintenance: FilesRunHistoryMaintenanceOperations,
    internal val configFormService: ConfigFormService,
    internal val sqlConsoleService: SqlConsoleOperations,
    internal val sqlConsoleQueryManager: SqlConsoleAsyncQueryOperations,
    internal val sqlConsoleExportService: SqlConsoleExportService,
    internal val sqlConsoleStateService: SqlConsoleStateService,
    internal val uiConfigPersistenceService: UiConfigPersistenceService,
    private val moduleSyncService: ModuleSyncService?,
    private val databaseModuleRunService: DatabaseModuleRunOperations?,
    private val databaseRunHistoryCleanupService: DatabaseRunHistoryCleanupService?,
    private val databaseOutputRetentionService: DatabaseOutputRetentionService?,
    private val filesModuleRunHistoryService: ModuleRunHistoryService,
    private val databaseModuleRunHistoryService: ModuleRunHistoryService?,
) {
    fun currentUiConfig(): UiAppConfig =
        runCatching { uiConfigLoader.load() }.getOrDefault(uiConfig)

    fun currentRuntimeUiConfig(): UiAppConfig =
        runCatching { runtimeConfigResolver.resolve(currentUiConfig()) }.getOrDefault(runtimeUiConfig)

    fun currentRuntimeContext(): UiRuntimeContext =
        runCatching { runtimeContextService.resolve(currentRuntimeUiConfig()) }.getOrDefault(runtimeContext)

    fun resolveRuntimeContextFromConfig(config: UiAppConfig): UiRuntimeContext =
        runtimeContextService.resolve(runtimeConfigResolver.resolve(config))

    fun currentDatabasePostgresConfig() =
        currentRuntimeUiConfig().moduleStore.postgres.takeIf { it.isConfigured() }

    fun currentDatabaseModuleStore(): DatabaseModuleRegistryOperations? =
        databaseModuleStore ?: currentDatabasePostgresConfig()?.let { DatabaseModuleStore.fromConfig(it) }

    fun currentDatabaseModuleBackend(): DatabaseModuleBackend? =
        databaseModuleBackend ?: currentDatabaseModuleStore()?.let { DatabaseModuleBackend(it) }

    fun currentModuleSyncService(): ModuleSyncService? =
        moduleSyncService ?: currentDatabasePostgresConfig()?.let { postgres ->
            val store = currentDatabaseModuleStore() ?: DatabaseModuleStore.fromConfig(postgres)
            ModuleSyncService(
                connectionProvider = DriverManagerDatabaseConnectionProvider(
                    requireNotNull(postgres.jdbcUrl),
                    requireNotNull(postgres.username),
                    requireNotNull(postgres.password),
                ),
                moduleRegistryImporter = DatabaseModuleSyncImporter(store),
                schema = postgres.schemaName(),
            )
        }

    fun currentDatabaseModuleRunService(): DatabaseModuleRunOperations? =
        databaseModuleRunService ?: currentDatabasePostgresConfig()?.let { postgres ->
            val store = currentDatabaseModuleStore() ?: DatabaseModuleStore.fromConfig(postgres)
            val runStore = DatabaseRunStore.fromConfig(postgres)
            DatabaseModuleRunService(
                databaseModuleStore = store,
                executionSource = DatabaseModuleExecutionSource.fromConfig(postgres),
                runExecutionStore = runStore,
                runQueryStore = runStore,
                credentialsProvider = credentialsService,
            )
        }

    fun currentDatabaseRunHistoryCleanupService(): DatabaseRunHistoryCleanupService? =
        databaseRunHistoryCleanupService ?: currentDatabasePostgresConfig()?.let { postgres ->
            DatabaseRunHistoryCleanupService(
                runStore = DatabaseRunStore.fromConfig(postgres),
            )
        }

    fun currentDatabaseOutputRetentionService(): DatabaseOutputRetentionService? =
        databaseOutputRetentionService ?: currentDatabasePostgresConfig()?.let { postgres ->
            DatabaseOutputRetentionService(
                runStore = DatabaseRunStore.fromConfig(postgres),
                retentionDays = currentRuntimeUiConfig().outputRetention.retentionDays,
                keepMinRunsPerModule = currentRuntimeUiConfig().outputRetention.keepMinRunsPerModule,
            )
        }

    fun currentFilesRunHistoryCleanupService(): FilesRunHistoryCleanupService =
        FilesRunHistoryCleanupService(filesRunHistoryMaintenance)

    fun currentFilesOutputRetentionService(): FilesOutputRetentionService =
        FilesOutputRetentionService(
            runManager = filesRunService,
            retentionDays = currentRuntimeUiConfig().outputRetention.retentionDays,
            keepMinRunsPerModule = currentRuntimeUiConfig().outputRetention.keepMinRunsPerModule,
        )

    fun currentDatabaseModuleRunHistoryService(): ModuleRunHistoryService? =
        databaseModuleRunHistoryService ?: run {
            val backend = currentDatabaseModuleBackend() ?: return@run null
            val runService = currentDatabaseModuleRunService() ?: return@run null
            DatabaseModuleRunHistoryService(backend, runService)
        }

    fun readSyncStateSafely(): ModuleSyncState =
        runCatching {
            val runtimeContext = currentRuntimeContext()
            val syncService = currentModuleSyncService()
            if (runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE && syncService != null) {
                syncService.currentSyncState()
            } else {
                ModuleSyncState()
            }
        }.getOrElse {
            ModuleSyncState()
        }

    fun requireDatabaseMaintenanceIsInactive() {
        if (readSyncStateSafely().maintenanceMode) {
            error("Работа с DB-модулями временно недоступна: идет массовый импорт модулей в БД.")
        }
    }

    fun requireDatabaseModuleIsNotSyncing(moduleCode: String) {
        val activeSync = readSyncStateSafely().activeSingleSync(moduleCode)
        if (activeSync != null) {
            val startedBy = activeSync.startedByActorDisplayName ?: activeSync.startedByActorId
            val startedAt = activeSync.startedAt
            error(
                buildString {
                    append("Импорт модуля '$moduleCode' уже выполняется")
                    if (!startedBy.isNullOrBlank()) {
                        append(" пользователем $startedBy")
                    }
                    append(". Начало: $startedAt.")
                },
            )
        }
    }

    fun databaseModeUnavailableMessage(runtimeContext: UiRuntimeContext): String =
        buildString {
            append("Режим базы данных сейчас недоступен.")
            runtimeContext.fallbackReason?.takeIf { it.isNotBlank() }?.let { reason ->
                append(" Причина: ")
                append(reason)
            }
        }

    fun requireDatabaseMode(runtimeContext: UiRuntimeContext) {
        require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
            databaseModeUnavailableMessage(runtimeContext)
        }
    }

    fun requireDatabaseActorId(runtimeContext: UiRuntimeContext): String =
        requireNotNull(runtimeContext.actor.actorId) {
            "Не удалось определить пользователя для режима базы данных."
        }

    fun requireDatabaseActorSource(runtimeContext: UiRuntimeContext): String =
        requireNotNull(runtimeContext.actor.actorSource) {
            "Не удалось определить источник пользователя для режима базы данных."
        }

    fun requireDatabaseActor(runtimeContext: UiRuntimeContext): ModuleActor =
        ModuleActor(
            actorId = requireDatabaseActorId(runtimeContext),
            actorSource = requireDatabaseActorSource(runtimeContext),
            actorDisplayName = runtimeContext.actor.actorDisplayName,
        )

    fun includeHiddenQueryParam(rawValue: String?): Boolean =
        rawValue == "1" || rawValue.equals("true", ignoreCase = true)

    fun parseLimit(rawValue: String?, defaultValue: Int = 20): Int =
        rawValue?.toIntOrNull()?.coerceIn(1, 200) ?: defaultValue

    fun requireRunHistoryService(
        storageMode: String,
        runtimeContext: UiRuntimeContext,
    ): Pair<ModuleRunHistoryService, ModuleActor?> =
        when (storageMode.lowercase()) {
            "files" -> {
                require(runtimeContext.effectiveMode == UiModuleStoreMode.FILES) {
                    "Страница файловых модулей доступна только в режиме Файлы."
                }
                filesModuleRunHistoryService to null
            }
            "database" -> {
                requireDatabaseMaintenanceIsInactive()
                requireDatabaseMode(runtimeContext)
                val service = requireNotNull(currentDatabaseModuleRunHistoryService()) {
                    "Сервис истории запусков для режима базы данных не настроен."
                }
                service to requireDatabaseActor(runtimeContext)
            }
            else -> error("Неизвестный режим хранения '$storageMode'.")
        }

    fun currentAppsRootOrFail(): java.nio.file.Path =
        requireNotNull(currentRuntimeUiConfig().appsRootPath()) {
            "Путь к каталогу apps не настроен."
        }
}
