# DataPoolLoader Backlog

Текущий backlog сформирован после ревизии всех `.md`-документов проекта.

Исторические планы, roadmap, черновики и отдельный review-документ схлопнуты в этот backlog и архитектурные правила. В рабочем виде оставлены только:

- архитектурные правила;
- архитектурный review;
- backlog и backlog history;
- отдельные safety/reference документы SQL-консоли.

Главный фокус ближайшего этапа:

- хорошая архитектура;
- надежность;
- расширяемость;
- снижение стоимости изменений;
- cleanup legacy и structural debt.

Связанные документы:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md)
- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)
- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)

## Правила приоритизации

- `P0` — архитектурные и операционные задачи, которые прямо влияют на надежность и maintainability.
- `P1` — обязательный следующий слой после `P0`, без которого архитектурный порядок останется незавершенным.
- `P2` — среднесрочное усиление качества и developer experience.
- `P3` — отложенные продуктовые и UX-задачи, которые не должны обгонять архитектурную программу.

## P0

### 0. Архитектурная программа: вычистить `ui-server` как перегруженный boundary

Статус:

- частично реализовано

Цель:

- сделать `ui-server` тоньше, предсказуемее и дешевле в поддержке;
- прекратить разрастание `composition root + routes + state orchestration` в один knowledge-heavy слой.

Что нужно сделать:

- пересмотреть роли [UiServerContext.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerContext.kt) и [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt);
- продолжить thinning route handlers:
  - [CommonRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonRoutes.kt)
  - [DatabaseRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseRoutes.kt)
  - [SqlConsoleRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleRoutes.kt)
- запретить новый orchestration-код в routes;
- вынести или сократить knowledge-rich runtime branching там, где это возможно;
- продолжить замещение concrete-связок контрактами и support-слоями.

Что уже сделано:

- transport-конфигурация `ui-server` больше не живет целиком в [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt);
- JSON mapper, Ktor plugins и route wiring вынесены в отдельные support-файлы:
  - [UiServerObjectMapper.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerObjectMapper.kt)
  - [UiServerPlugins.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerPlugins.kt)
  - [UiServerRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerRoutes.kt);
- [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt) стал ближе к composition root, а не к transport-свалке.
- сборка `UiServerContextDependencies` больше не живет прямо в `uiModule`:
  - [UiServerModuleDependenciesFactory.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerModuleDependenciesFactory.kt)
  - [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt) теперь не держит вручную graph assembly для `UiServerContext`;
- `uiModule` больше не устанавливает Ktor plugins/routes напрямую:
  - [UiServerApplicationSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerApplicationSupport.kt)
  - [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt) теперь только собирает `UiServerContext` и делегирует transport-installation;
- DB route layer больше не держится на одном смешанном support-файле:
  - cleanup flow разрезан на payload/action слои:
    - [DatabaseCleanupRoutePayloadSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseCleanupRoutePayloadSupport.kt)
    - [DatabaseCleanupRouteActions.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseCleanupRouteActions.kt)
  - sync/import flow больше не смешивает context, payload parsing и actions в одном файле:
    - [DatabaseSyncRouteContexts.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseSyncRouteContexts.kt)
    - [DatabaseSyncRoutePayloadSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseSyncRoutePayloadSupport.kt)
    - [DatabaseSyncRouteActions.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseSyncRouteActions.kt)
  - module flow вынесен в отдельные support-слои:
    - [DatabaseModuleRouteContexts.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModuleRouteContexts.kt)
    - [DatabaseModuleServiceRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModuleServiceRouteSupport.kt)
    - [DatabaseModulesCatalogRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModulesCatalogRouteSupport.kt);
- [DatabaseRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseRoutes.kt) теперь агрегирует отдельные route groups:
  - [DatabaseCleanupRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseCleanupRoutes.kt)
  - [DatabaseSyncRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseSyncRoutes.kt)
  - [DatabaseModuleRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModuleRoutes.kt);
- DB module route group дальше разрезан по ответственности:
  - [DatabaseModuleLifecycleRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModuleLifecycleRoutes.kt)
  - [DatabaseModuleRunRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModuleRunRoutes.kt);
- SQL-console route support больше не смешивает metadata, export и async execution lifecycle в одном файле:
  - [SqlConsoleMetadataRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleMetadataRouteSupport.kt)
  - [SqlConsoleExportRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleExportRouteSupport.kt)
  - execution flow дополнительно разрезан на:
    - [SqlConsoleExecutionPathSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleExecutionPathSupport.kt)
    - sync execution в [SqlConsoleSyncExecutionSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleSyncExecutionSupport.kt)
    - async execution/session actions в [SqlConsoleAsyncExecutionSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleAsyncExecutionSupport.kt);
- [SqlConsoleRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleRoutes.kt) теперь только агрегирует отдельные route groups:
  - [SqlConsoleStateRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleStateRoutes.kt)
  - [SqlConsoleMetadataRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleMetadataRoutes.kt)
  - [SqlConsoleQueryRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleQueryRoutes.kt)
  - [SqlConsoleExportRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleExportRoutes.kt)
  - [SqlConsoleAsyncQueryRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleAsyncQueryRoutes.kt);
- `PageRoutes` больше не держит вручную повторяющийся redirect/static transport-код;
- page-level routing support больше не смешивает redirect и static resource flow в одном файле:
  - compose redirect registration и mode guards живут в [PageComposeRedirectSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/PageComposeRedirectSupport.kt)
  - compose query/bundle helpers живут в [PageComposeQuerySupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/PageComposeQuerySupport.kt)
  - static text pages и static resource proxy/migration живут в [PageStaticResourceSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/PageStaticResourceSupport.kt);
- [PageRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/PageRoutes.kt) теперь только агрегирует route groups:
  - [PageComposeAliasRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/PageComposeAliasRoutes.kt)
  - [PageScreenRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/PageScreenRoutes.kt)
  - [PageStaticRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/PageStaticRoutes.kt);
- page screen route layer дальше разрезан:
  - module-related screens живут в [PageModuleScreenRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/PageModuleScreenRoutes.kt)
  - SQL/maintenance screens живут в [PageSqlScreenRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/PageSqlScreenRoutes.kt)
  - [PageScreenRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/PageScreenRoutes.kt) остался тонким агрегатором;
- `CommonRoutes`, `DatabaseRoutes` и `SqlConsoleRoutes` уже меньше знают о mode-specific maintenance flow, actor wiring, credentials/appsRoot orchestration и service resolution.
- [CommonRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonRoutes.kt) больше не смешивает runtime, files modules, files run, cleanup и websocket transport в одном файле;
- общий files/runtime route layer разрезан на отдельные route groups:
  - [CommonRuntimeRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonRuntimeRoutes.kt)
  - [CommonFilesModuleRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonFilesModuleRoutes.kt)
  - [CommonFilesRunRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonFilesRunRoutes.kt)
  - [CommonMaintenanceRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonMaintenanceRoutes.kt);
- общий route support больше не смешивает разные ответственности в одном файле:
  - maintenance flow вынесен в отдельные support-слои:
    - [CommonRunHistoryCleanupRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonRunHistoryCleanupRouteSupport.kt)
    - [CommonOutputRetentionRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonOutputRetentionRouteSupport.kt)
    - DB response adaptation для common cleanup preview/result вынесен в [DatabaseRunHistoryCleanupRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseRunHistoryCleanupRouteSupport.kt)
  - config/module flow разрезан по отдельным support-слоям:
    - [CommonRuntimeModeRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonRuntimeModeRouteSupport.kt)
    - [CommonFilesModuleRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonFilesModuleRouteSupport.kt)
    - [CommonConfigFormRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonConfigFormRouteSupport.kt)
  - credentials upload вынесен в [CommonCredentialsRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonCredentialsRouteSupport.kt);
- [ModuleRunRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/ModuleRunRoutes.kt) больше не держит вручную path parsing и service resolution;
- для экрана `История и результаты` появился отдельный support-слой:
  - route contexts живут в [ModuleRunRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/ModuleRunRouteSupport.kt)
  - history service resolution живет в [ModuleRunHistoryRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/ModuleRunHistoryRouteSupport.kt)
  - общие route param helpers живут в [RouteParamSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/RouteParamSupport.kt).
- startup/bootstrap слой `ui-server` вынесен из [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt) в [UiServerStartup.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerStartup.kt);
- сборка [UiServerContext.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerContext.kt) вынесена из [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt) в:
  - [UiServerContextDependencies.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerContextDependencies.kt)
  - [UiServerContextFactory.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerContextFactory.kt);
- default factory wiring для `ModuleRegistry / RunManager / SqlConsole* / FilesRunHistoryService` теперь живет рядом со startup-слоем, а не в composition root;
- startup слой дальше разрезан по ответственности:
  - default factory wiring живет в [UiServerDefaultFactories.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerDefaultFactories.kt)
  - static text/resource loading живет в [UiServerResourceLoading.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerResourceLoading.kt);
- [UiServerContext.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerContext.kt) перестал быть knowledge-rich объектом:
  - dynamic DB/files service resolution разрезан по mode-specific слоям:
    - DB module/sync services живут в [UiServerDatabaseModuleDynamicServices.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerDatabaseModuleDynamicServices.kt)
    - DB run/history/retention services живут в [UiServerDatabaseRunDynamicServices.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerDatabaseRunDynamicServices.kt)
    - [UiServerFilesDynamicServices.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerFilesDynamicServices.kt)
  - runtime guards и route query helpers разрезаны по отдельным слоям:
    - DB sync/maintenance guards живут в [UiServerDatabaseSyncGuards.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerDatabaseSyncGuards.kt)
    - DB mode availability logic живет в [UiServerDatabaseModeSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerDatabaseModeSupport.kt)
    - DB actor extraction живет в [UiServerDatabaseActorSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerDatabaseActorSupport.kt)
    - query parsing helpers живут в [UiServerRouteQuerySupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerRouteQuerySupport.kt)
  - runtime config/context access вынесен в [UiServerRuntimeAccess.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerRuntimeAccess.kt);
- DB module lifecycle route group дальше разрезан:
  - read/catalog flow живет в [DatabaseModuleCatalogRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModuleCatalogRoutes.kt)
  - mutation flow агрегируется в [DatabaseModuleMutationRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModuleMutationRoutes.kt) и разрезан на:
    - [DatabaseModuleWorkingCopyRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModuleWorkingCopyRoutes.kt)
    - [DatabaseModuleRegistryMutationRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModuleRegistryMutationRoutes.kt);
- сам [UiServerContext.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerContext.kt) теперь ближе к carrier-объекту зависимостей, а не к смешанному service-locator/runtime-helper слою.

Критерий завершения:

- `ui-server` снова выглядит как boundary/orchestration слой, а не как второй `core`;
- новые сценарии не требуют разрастания routes и `UiServerContext`.

### 1. Архитектурная программа: нормализовать error model

Статус:

- частично реализовано

Цель:

- убрать грубую и нечестную модель ошибок;
- разделить ошибки пользователя, конфликт состояния и внутренние сбои.

Что нужно сделать:

- убрать практику `Throwable -> 400` из [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt);
- ввести минимум три класса ошибок:
  - validation/business error;
  - conflict/state error;
  - internal error;
- привести transport-слой к понятным и стабильным ответам;
- убрать места, где UI не может понять, это ошибка сценария или баг сервера.

Что уже сделано:

- transport-слой больше не маскирует все исключения под `400`;
- введены осмысленные `404 / 409 / 503 / 500` для ключевых server-path’ов;
- появились доменные not-found/conflict исключения вне `server`-пакета;
- для DB sync-run details и SQL-console export добавлены отдельные not-found сценарии вместо `500`;
- ключевые сценарии FILES / DB / SQL-console закреплены серверными тестами.

Критерий завершения:

- сервер перестает маскировать внутренние дефекты под пользовательские ошибки;
- UI может по типу ответа различать, что именно произошло.

### 2. Архитектурная программа: нормализовать state model

Статус:

- частично реализовано

Цель:

- развести runtime state, persisted history, UI preferences и operational config;
- сделать состояние проекта классифицированным и предсказуемым.

Что нужно сделать:

- провести ревизию persisted state моделей в `ui-server`;
- зафиксировать для каждого значимого state:
  - source of truth;
  - recoverable state;
  - cache;
  - UI preference;
  - operational history;
- убрать смешение UI preferences и operational state;
- запретить добавление новых глобальных mutable service без архитектурного justification;
- отдельно разобрать:
  - SQL console state;
  - run state;
  - cleanup/retention state;
  - runtime mode/config state.

Что уже сделано:

- начат cleanup persisted state файлового run-layer;
- `run-state.json` больше не хранит uploaded credentials;
- credentials вынесены в отдельный `credentials-state.json` с legacy-миграцией из старого `run-state.json`;
- это покрыто тестами на separation и migration;
- SQL console persisted state больше не живет одним mixed-файлом;
- `sql-console-state.json` переведен в legacy migration-only формат;
- рабочее состояние SQL-консоли разделено на:
  - `sql-console-workspace-state.json`
  - `sql-console-library-state.json`
  - `sql-console-preferences-state.json`;
- сервис SQL-консоли теперь собирает единый response из двух отдельных source-of-truth.
- query history, favorites и favorite objects больше не смешаны с execution/UI preferences;
- `sql-console-preferences-state.json` теперь хранит только `pageSize`, `strictSafetyEnabled`, `transactionMode`;
- пользовательский query/object content вынесен в отдельный `sql-console-library-state.json` с миграцией:
  - из legacy `sql-console-state.json`
  - из старого расширенного `sql-console-preferences-state.json`.
- legacy combined SQL-console state больше не маскируется под рабочий store:
  - [LegacySqlConsoleStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/LegacySqlConsoleStateStore.kt) теперь держит явную legacy-only роль;
  - [LegacySqlConsoleState.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/LegacySqlConsoleState.kt) теперь моделирует именно legacy combined state.

Критерий завершения:

- новые и существующие состояния можно классифицировать без двусмысленности;
- у проекта появляется ясная карта источников истины.

### 3. Архитектурная программа: разрезать giant UI files

Статус:

- частично реализовано

Цель:

- снизить стоимость изменений во frontend;
- превратить крупные Compose-экраны в набор feature-блоков.

Первый приоритет:

- [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt)
- [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt)
- [ModuleRunsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPage.kt)
- [ModuleEditorConfigForm.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigForm.kt)

Что нужно сделать:

- выделить shell pages;
- вынести action-panels и visual sections в отдельные компоненты;
- уменьшить прямой объем page-файлов;
- не тащить дополнительную логику обратно в page/store после декомпозиции.

Что уже сделано:

- начат реальный распил [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt);
- крупные visual sections и helper-слои вынесены в:
  - [SqlConsoleLibrarySections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleLibrarySections.kt)
  - [SqlConsoleEditorSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleEditorSections.kt)
  - [SqlConsoleResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultSections.kt)
  - [SqlConsolePageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageSupport.kt);
- page-файл перестал держать почти всю SQL-console подсистему в одном месте;
- package-level SQL helper-ы нормализованы и переиспользуются между SQL-console экранами;
- прежний second-level giant-файл `SqlConsolePageSections.kt` устранен.
- начат реальный распил [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt);
- editor visual sections и helper-слои вынесены в:
  - [ModuleEditorShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorShellSections.kt)
  - [ModuleEditorRunSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorRunSections.kt)
  - [ModuleEditorWorkspaceSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorWorkspaceSections.kt)
  - [ModuleEditorMetadataSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorMetadataSections.kt)
  - [ModuleEditorPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPageSupport.kt);
- прежний second-level giant-файл `ModuleEditorPageSections.kt` устранен.
- начат реальный распил [ModuleEditorConfigForm.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigForm.kt);
- config-form sections и helper-слои вынесены в:
  - [ModuleEditorConfigFormSettingsSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormSettingsSections.kt)
  - [ModuleEditorConfigFormSourceSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormSourceSections.kt)
  - [ModuleEditorConfigFormFieldSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormFieldSupport.kt)
  - [ModuleEditorConfigFormSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormSupport.kt);
- прежний second-level giant-файл `ModuleEditorConfigFormSections.kt` устранен.
- начат реальный распил [ModuleRunsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPage.kt);
- runs visual sections и helper-слои вынесены в:
  - [ModuleRunsPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPageSections.kt)
  - [ModuleRunsPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPageSupport.kt);
- [ModuleRunsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPage.kt) сокращен до shell/wiring-слоя, а detail-pane вынесен в отдельные section-компоненты.
- начат реальный распил [ModuleSyncPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPage.kt);
- sync visual sections и helper-слои вынесены в:
  - [ModuleSyncPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPageSections.kt)
  - [ModuleSyncPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPageSupport.kt);
- [ModuleSyncPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPage.kt) сокращен до shell/wiring-слоя.
- начат реальный распил [RunHistoryCleanupPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupPage.kt);
- cleanup visual sections и helper-слои вынесены в:
  - [RunHistoryCleanupPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupPageSections.kt)
  - [RunHistoryCleanupPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupPageSupport.kt);
- [RunHistoryCleanupPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupPage.kt) сокращен до shell/wiring-слоя.
- homepage visual sections и helper-слои вынесены в:
  - [HomePageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/home/HomePageSections.kt)
  - [HomePageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/home/HomePageSupport.kt);
- [HomePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/home/HomePage.kt) сокращен до shell/wiring-слоя и перестал держать hero/card/helper реализацию в одном файле.
- после page-layer начат распил тяжелых shared store-файлов:
  - loading/fallback/session assembly вынесены из [ModuleEditorStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStore.kt) в [ModuleEditorStoreLoadingSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStoreLoadingSupport.kt)
  - action/mutation flow вынесен в [ModuleEditorStoreActionSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStoreActionSupport.kt)
  - loading и sync action flow вынесены из [ModuleSyncStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncStore.kt) в [ModuleSyncStoreLoadingSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncStoreLoadingSupport.kt) и [ModuleSyncStoreActionSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncStoreActionSupport.kt);
- shared store decomposition продолжен на остальных экранах:
  - loading/fallback/history selection вынесены из [ModuleRunsStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsStore.kt) в [ModuleRunsStoreLoadingSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsStoreLoadingSupport.kt)
  - loading/persisted-state assembly и execution lifecycle вынесены из [SqlConsoleStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStore.kt) в [SqlConsoleStoreLoadingSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStoreLoadingSupport.kt) и [SqlConsoleStoreExecutionSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStoreExecutionSupport.kt)
  - loading/refresh и cleanup/output action flow вынесены из [RunHistoryCleanupStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupStore.kt) в [RunHistoryCleanupStoreLoadingSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupStoreLoadingSupport.kt) и [RunHistoryCleanupStoreActionSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupStoreActionSupport.kt)
  - loading/search/favorite-object mutation вынесены из [SqlConsoleObjectsStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsStore.kt) в [SqlConsoleObjectsStoreLoadingSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsStoreLoadingSupport.kt) и [SqlConsoleObjectsStoreActionSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsStoreActionSupport.kt)
  - home loading/mode-switch flow вынесен из [HomePageStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/home/HomePageStore.kt) в [HomePageStoreSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/home/HomePageStoreSupport.kt);
- `P0.3` перешел из чисто page-level декомпозиции в следующий слой: store/support separation без возврата orchestration обратно в page/store giant-файлы.

Критерий завершения:

- новые экраны не растут как монолиты;
- текущие giant-файлы разбиты до состояния, когда их можно нормально ревьюить и менять локально.

### 4. Архитектурная программа: разрезать `styles.css`

Статус:

- частично реализовано

Цель:

- убрать giant CSS-file debt;
- сделать стили обозримыми и локализованными по подсистемам.

Что нужно сделать:

- провести инвентаризацию текущего [styles.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles.css);
- разделить стили минимум на логические группы:
  - foundation/base;
  - layout;
  - module editor;
  - module runs;
  - sql console;
  - home/landing;
  - maintenance screens;
- удалить мертвые и дублирующие классы;
- запретить добавление новых крупных блоков в один общий CSS-файл без явной причины.

Что уже сделано:

- giant [styles.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles.css) переведен в import-manifest;
- порядок каскада сохранен через ordered CSS-chunks:
  - [00-foundation.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/00-foundation.css)
  - [05-home-help.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/05-home-help.css)
  - [10-config-editor.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/10-config-editor.css)
  - [20-sql-console.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/20-sql-console.css)
  - [30-run-history.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/30-run-history.css)
  - [35-sync-maintenance.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/35-sync-maintenance.css)
  - [40-sql-results.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/40-sql-results.css);
- из split CSS уже убраны первые дублирующиеся selector-блоки, foundation/home-help слой физически разделен, а maintenance/sync экран больше не живет в foundation и unrelated result-styles;
- ресурсы проходят packaging и попадают в `ui-server` bundle.

Критерий завершения:

- стили проекта разбиты по подсистемам;
- giant CSS-файл больше не является точкой концентрации UI-долга.

### 5. Архитектурная программа: провести cleanup legacy и мусора

Статус:

- частично реализовано

Цель:

- убрать из проекта код, который остался от старых решений, больше не нужен или дублирует актуальную логику.

Что нужно сделать:

- провести тщательный анализ dead code / legacy branches / старых helper-слоев;
- убрать мертвые компоненты, неиспользуемые модели, старые переходные адаптеры и дубли;
- отдельно проверить:
  - legacy куски в `ui-compose-web`;
  - неиспользуемые CSS-классы;
  - устаревшие persisted поля;
  - старые route / API ветки;
  - лишние support-слои, если они больше не дают ценности;
- cleanup делать только после явного анализа, а не слепым удалением.

Что уже сделано:

- удален пустой переходный sync-layer [DatabaseSyncRouteSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseSyncRouteSupport.kt), который после распила DB sync flow деградировал до одного `typealias`;
- `LegacySqlConsoleStateStore` приведен к реальной migration-only роли:
  - production-store больше не содержит write-path для `sql-console-state.json`;
  - запись legacy state перенесена в тестовый код, а не остается внутри runtime-слоя.
- из `LegacySqlConsoleState` удален мертвый legacy-хвост `executionPolicy`:
  - поле больше не участвует в миграции;
  - фиксированная политика `STOP_ON_FIRST_ERROR` обеспечивается рабочими SQL-console слоями, а не legacy-state моделью.
- мертвое поле `executionPolicy` удалено и из живого публичного SQL-console контракта:
  - shared models, server DTO и store-layer больше не носят это поле через `state/update/query` payloads;
  - фиксированная политика `STOP_ON_FIRST_ERROR` остается только во внутренних execution-слоях, где она реально используется.
- DB registry cleanup: удалены пустые переходные `typealias` вокруг создания модулей:
  - `CreateModuleRequest` и `CreateModuleResult` больше не маскируют `RegistryModuleDraft` и `RegistryModuleCreationResult`;
  - `ui-server` DB registry слой теперь использует реальные registry-модели без лишней псевдо-абстракции.
- из `core` удалены неиспользуемые deprecated aliases:
  - `PostgresExporter`
  - `PostgresImporter`
  они не использовались внутри проекта и оставались только как исторический API-хвост.
- migration `uploadedCredentials` из `run-state.json` теперь не оставляет legacy-поле в файловом runtime-state:
  - [UiCredentialsStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/UiCredentialsStateStore.kt) после успешной миграции очищает `uploadedCredentials` из старого `run-state.json`;
  - legacy state перестает оставаться двусмысленным после первого чтения.
- split-миграция SQL-console теперь дочищает legacy combined state до конца:
  - `sql-console-state.json` удаляется только после того, как созданы `workspace/library/preferences` state-файлы;
  - legacy combined state больше не остается на диске как конкурирующий источник данных после успешной полной миграции.
- SQL-console helper cleanup: общие state/persistence helper-ы больше не дублируются между store-слоями:
  - вынесены в [SqlConsoleStateSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStateSupport.kt)
  - `SqlConsoleStore` и `SqlConsoleObjectsStore` больше не держат параллельные реализации `matches`, state-update mapping и default draft logic.
- runtime/UI helper cleanup:
  - общая подпись режима хранения вынесена из `home`-пакета в [ModuleStoreModeLabels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/model/ModuleStoreModeLabels.kt)
  - runtime fallback warning и mode-mismatch predicate вынесены в [RuntimeContextUiSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/runtime/RuntimeContextUiSupport.kt)
  - `SqlConsolePage`, `SqlConsoleObjectsPage`, `ModuleRunsPage` и `RunHistoryCleanupPage` больше не держат локальные дубли строковой сборки fallback-warning.
- mode/fallback dedup cleanup продолжен:
  - DB-unavailable тексты переведены на общий helper в [RuntimeContextUiSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/runtime/RuntimeContextUiSupport.kt)
  - `ModuleEditorShellSections` / `ModuleSyncPageSections` / `ModuleRunsPage` больше не держат локальные `fallbackReason ?: ...` ветки
  - `RuntimeModeSwitch`, `ModuleRunsPage`, `ModuleRunsPageSections` и `RunHistoryCleanupPageSections` больше не держат ручные `Файлы/База данных` label-ветки там, где уже есть общий `ModuleStoreMode.label`
  - одноразовые wrapper-функции `buildSqlConsoleFallbackWarning` и `buildFallbackWarning` удалены, экраны используют общий runtime fallback builder напрямую.
- `module_sync` helper cleanup:
  - локальный `formatInstant` переведен на общий [Formatters.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/format/Formatters.kt)
  - повторяющаяся сборка summary по `activeSingleSyncs` вынесена в один helper вместо параллельных `joinToString { describeActiveSingleSync(...) }` в section-слое.
- DOM helper cleanup:
  - строковые CSS class-list больше не токенизируются вручную по экранным файлам через `split/filter/toTypedArray`
  - общий helper добавлен в [Attrs.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/dom/Attrs.kt)
  - на него переведены foundation и экранные участки:
    - [SectionCard.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/component/SectionCard.kt)
    - [RunProgressWidget.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/component/RunProgressWidget.kt)
    - [ModuleSyncPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPageSections.kt)
    - [ModuleRunsPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPageSections.kt)
    - [ModuleEditorRunSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorRunSections.kt).
- live-updates helper cleanup:
  - общий builder WebSocket URL вынесен в [LiveUpdates.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/updates/LiveUpdates.kt)
  - feature-specific wrappers удалены из:
    - [ModuleEditorPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPageSupport.kt)
    - [ModuleRunsPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPageSupport.kt)
  - экраны используют foundation helper напрямую:
    - [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt)
    - [ModuleRunsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPage.kt).
- межфичевой helper cleanup:
  - generic CSS/status helper-ы вынесены из `module_runs` в foundation:
    - [StatusClassSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/component/StatusClassSupport.kt)
  - `module_editor` больше не зависит от `module_runs` ради `eventEntryCssClass` и `runStatusCssClass`
  - старый feature-scoped helper-файл [ModuleRunsFormatters.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsFormatters.kt) удален.
- shared run-view helper cleanup:
  - общие run presentation/parsing helper-ы вынесены из `module_runs` в нейтральный shared пакет [run](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/run)
  - `module_editor` больше не зависит от `module_runs` ради `translate* / detect* / parseStructuredRunSummary / buildCompactProgressEntries / format*` helper-логики
  - старые feature-scoped shared helper-файлы удалены:
    - [ModuleRunsLabels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsLabels.kt)
    - [ModuleRunsFormatting.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsFormatting.kt)
    - [ModuleRunsSummary.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsSummary.kt).
- SQL-console label/helper cleanup:
  - web-слой больше не дублирует shared label/status helper-ы поверх [SqlConsoleLabels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleLabels.kt)
  - локальный duplicate-файл [SqlConsoleStatusTextSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStatusTextSupport.kt) удален
  - `SqlConsolePage.kt` и `SqlConsoleResultSections.kt` используют shared `runButtonTone / sourceStatusTone / sourceStatusSuffix / build*Text` напрямую, без промежуточных web-wrapper-ов.
- module_sync display helper cleanup:
  - локальный wrapper `formatInstant(...)` удален из [ModuleSyncPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPageSupport.kt)
  - `module_sync` section-слой использует общий [Formatters.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/format/Formatters.kt) напрямую
  - fallback `startedByActorDisplayName ?: startedByActorId` сведен к одному helper `actorLabel(...)` вместо локального дублирования в support и sections.
- cleanup formatting cleanup:
  - локальные `formatInstant(...)` и `formatBytes(...)` удалены из [RunHistoryCleanupPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupPageSupport.kt)
  - общие formatter-ы `formatCompactDateTime(...)` и `formatByteSize(...)` добавлены в [Formatters.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/format/Formatters.kt)
  - [RunHistoryCleanupPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupPageSections.kt) теперь использует foundation formatting напрямую, без feature-only дублирования.
- run presentation helper cleanup:
  - generic run-display helper-ы вынесены из [ModuleRunsPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPageSupport.kt) в нейтральный foundation-слой [RunPresentationSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/run/RunPresentationSupport.kt)
  - `ModuleRunsPageSupport.kt` больше не держит `formatStageDuration / formatTimeoutSeconds / formatRowsInterval / formatBooleanFlag / extractArtifactName`
  - [ModuleRunsPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPageSections.kt) использует foundation run-support напрямую, а в feature support остались только route-specific и diagnostics-specific части.
- module_sync badge cleanup:
  - локальные `syncStatusBadgeClass(...)` и `syncActionBadgeClass(...)` удалены из [ModuleSyncPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPageSupport.kt)
  - shared `ModuleSyncLabels.kt` теперь держит `syncStatusTone(...)` и `syncActionTone(...)` рядом с `translate*` helper-ами
  - [ModuleSyncPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPageSections.kt) использует общий [StatusBadge](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/component/SectionCard.kt) вместо ручной сборки bootstrap badge-классов.
- module_sync shared helper cleanup:
  - `actorLabel(...)` вынесен из web support в shared [ModuleSyncLabels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncLabels.kt)
  - `filterSelectableModules(...)` вынесен из [ModuleSyncPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPageSupport.kt) в shared [ModuleSyncFilters.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncFilters.kt)
  - web `module_sync` больше не держит pure state/filter helper-логику, которая не зависит от browser/Compose runtime.
- module_sync text helper cleanup:
  - `buildMaintenanceMessage(...)`, `describeActiveSingleSync(...)`, `buildActiveSingleSyncSummary(...)` и `syncRunMeta(...)` вынесены из web support в shared [ModuleSyncLabels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncLabels.kt) через formatter callback
  - [ModuleSyncPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPageSupport.kt) удален
  - [ModuleSyncPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncPageSections.kt) использует shared text/presentation helper-ы напрямую, без отдельного web support-слоя.
- screen-local helper cleanup продолжен:
  - `SqlConsoleLibrarySections.kt` больше не держит две параллельные реализации query picker-блоков; общий локальный `SqlConsoleQueryPickerBlock(...)` закрывает recent/favorite queries
  - `ModuleEditorShellSections.kt` больше не повторяет вручную `module-editor-toolbar-group` markup; toolbar-группы сведены к локальному `EditorToolbarGroup(...)`
  - editor секции больше не дублируют кнопку `Свернуть/Развернуть`; общий `SectionExpandToggleButton(...)` вынесен в [ModuleEditorSectionStateSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorSectionStateSupport.kt) и используется в config/run слоях.
- feature-local cleanup продолжен:
  - `SqlConsoleResultSections.kt` больше не держит две параллельные placeholder-ветки для `execution/result == null`; общий локальный `RenderExecutionResultPlaceholder(...)` закрывает pending/error empty-states для data/status tabs
  - `SqlConsoleObjectsPage.kt` больше не дублирует object identity markup между navigation target, favorites и object cards; общий локальный `SqlObjectIdentityBlock(...)` собирает name/context/detail presentation в одном месте
  - `ModuleEditorConfigFormSourceSections.kt` больше не повторяет add/remove action-button markup для sources/quotas; общий локальный `ConfigCollectionActionButton(...)` закрывает эти collection actions.
- form/result helper cleanup продолжен:
  - `ModuleEditorConfigFormSettingsSections.kt` и `ModuleEditorConfigFormSourceSections.kt` больше не держат две параллельные `when (sqlState.mode)` ветки; общий `CommitSqlModeFields(...)` в [ModuleEditorConfigFormFieldSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormFieldSupport.kt) собирает `INLINE/CATALOG/EXTERNAL` UI в одном месте
  - `SqlConsoleResultSections.kt` больше не токенизирует class-string вручную через `split(\" \")`; status strip переведен на общий DOM helper `classesFromString(...)`
  - `SqlConsoleEditorSections.kt` больше не токенизирует class-string вручную через `split(\" \")`; `CommandGuardrail` и `StatementRiskBadge` используют общий DOM helper `classesFromString(...)`.
- metadata/workspace/result cleanup продолжен:
  - `SqlConsoleResultSections.kt` больше не дублирует shard status badge markup между table/card presentation; общий локальный `ShardStatusBadge(...)` закрывает этот статусный фрагмент
  - `ModuleEditorMetadataSections.kt` больше не повторяет editable row-shell для text/textarea полей; общий локальный `MetadataEditableRow(...)` держит label/help/value layout в одном месте
  - `ModuleEditorWorkspaceSections.kt` больше не повторяет action-button markup в SQL catalog toolbar; общий локальный `SqlCatalogActionButton(...)` закрывает `Создать / Переименовать / Удалить`.
- placeholder/nav/value-shell cleanup продолжен:
  - `SqlConsoleResultSections.kt` больше не размазывает `sql-result-placeholder` markup по нескольким веткам; общий локальный `SqlResultPlaceholder(...)` закрывает этот экранный placeholder
  - `SqlConsoleObjectsPage.kt` больше не повторяет hero navigation button markup; общий локальный `ObjectsNavActionButton(...)` закрывает `На главную / SQL-консоль / Объекты БД`
  - `ModuleEditorMetadataSections.kt` больше не дублирует `module-metadata-value + helpText` shell между editable и checkbox полями; общий локальный `MetadataValueBlock(...)` закрывает этот value/help wrapper.
- credentials upload helper cleanup:
  - duplicate `uploadCredentialsFile(...)` больше не живет параллельно в `module_editor` и `sql_console`
  - общий web-only helper вынесен в [CredentialsHttpSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/http/CredentialsHttpSupport.kt)
  - [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt) и [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt) используют foundation helper напрямую вместо feature-scoped дублей.
- credentials status helper cleanup:
  - `loadCredentialsStatus(...)` тоже вынесен из SQL feature-layer в [CredentialsHttpSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/http/CredentialsHttpSupport.kt)
  - [SqlConsolePageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageSupport.kt) больше не держит credentials HTTP lifecycle.
- module_editor section-state cleanup:
  - duplicate localStorage helper-ы для раскрытия секций больше не живут параллельно в `ModuleEditorPageSupport.kt` и `ModuleEditorConfigFormSupport.kt`
  - общий helper вынесен в [ModuleEditorSectionStateSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorSectionStateSupport.kt)
  - [ModuleEditorRunSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorRunSections.kt) и [ModuleEditorConfigFormFieldSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormFieldSupport.kt) используют единый helper вместо двух локальных реализаций.
- module_editor route/presentation cleanup:
  - route URL builder-ы вынесены из page support в [ModuleEditorRoute.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorRoute.kt)
  - query string для editor route больше не собирается вручную в [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt), а живет рядом с route builder-ами в [ModuleEditorRoute.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorRoute.kt)
  - `formatEditorTimeoutSeconds(...)` заменен на общий foundation helper `formatTimeoutSeconds(...)`
  - остаточный [ModuleEditorPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPageSupport.kt) удален, а одноразовый `validationBadgeClass(...)` локализован в [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt).
- module_editor config form cleanup:
  - mixed [ModuleEditorConfigFormSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormSupport.kt) разрезан по ответственности
  - mutation helper-ы вынесены в [ModuleEditorConfigFormMutationSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormMutationSupport.kt)
  - SQL mode state/apply helper-ы вынесены в [ModuleEditorConfigFormSqlModeSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormSqlModeSupport.kt).
- module_editor validation label cleanup:
  - `validationBadgeClass(...)` вынесен из [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt) в shared [ModuleEditorLabels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorLabels.kt) рядом с `translateValidationStatus(...)`
  - page больше не держит локальный validation helper, который уже является частью общей presentation-модели редактора.
- sql_console object SQL cleanup:
  - object/favorite SQL builder-ы и object-type label helper-ы вынесены из mixed [SqlConsolePageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageSupport.kt) в focused [SqlConsoleObjectSqlSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectSqlSupport.kt)
  - [SqlConsoleLibrarySections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleLibrarySections.kt) и [SqlConsoleObjectsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPage.kt) используют общий object SQL helper-слой вместо локальных private-дублей
  - `SqlConsolePageSupport.kt` больше не смешивает editor outline/editor actions и object metadata/favorite SQL generation в одном файле.
- sql_console navigation cleanup:
  - `SqlObjectNavigationTarget` и `matches(...)` вынесены из [SqlConsoleObjectsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPage.kt) в focused [SqlConsoleObjectNavigationSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectNavigationSupport.kt)
  - objects page больше не держит локальный navigation matching helper внутри экранного файла.
- sql_console object display cleanup:
  - display helper-ы `qualifiedName()/contextLabel()` для favorite/object/navigation target сведены в общий object support-слой
  - [SqlConsoleObjectsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPage.kt) и [SqlConsoleLibrarySections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleLibrarySections.kt) больше не держат локальные `schema.object` и `source • type` string-builder ветки.
- sql_console script/editor cleanup:
  - script outline и SQL formatting вынесены в [SqlConsoleScriptSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleScriptSupport.kt)
  - Monaco/editor interaction helper-ы вынесены в [SqlConsoleEditorInteractionSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleEditorInteractionSupport.kt)
  - data/result helper-ы вынесены в [SqlConsoleQueryResultSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleQueryResultSupport.kt)
  - остаточный mixed [SqlConsolePageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageSupport.kt) удален.
- sql_console result text cleanup:
  - execution/shard summary text builder-ы вынесены из [SqlConsoleResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultSections.kt) в focused [SqlConsoleResultTextSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultTextSupport.kt)
  - result sections больше не держат несколько локальных `buildString`-веток для execution meta, shard status и data-page summary в одном UI-файле.
- sql_console status badge cleanup:
  - локальный `StatusBadge(status)` удален из [SqlConsoleResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultSections.kt)
  - SQL result sections используют foundation [StatusBadge](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/component/SectionCard.kt) и shared `sourceStatusBadgeTone(...)` из [SqlConsoleLabels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleLabels.kt) вместо feature-scoped badge renderer-а.
- module_editor numeric field cleanup:
  - повторяющиеся wrapper-ы `CommitIntField/CommitOptionalIntField/CommitLongField/CommitOptionalLongField/CommitOptionalDoubleField` больше не держат копипасту parse-веток
  - общий required/optional numeric commit helper живет внутри [ModuleEditorConfigFormFieldSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormFieldSupport.kt), а typed wrapper-ы сведены к тонким adapter-функциям.
- sql_console object table-reference cleanup:
  - подпись связанной таблицы вынесена из [SqlConsoleObjectsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPage.kt) в общий object helper [SqlConsoleObjectSqlSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectSqlSupport.kt)
  - object card больше не собирает `Таблица: schema.table` строку локально внутри экранного файла.
- sql_console object helper dedup:
  - generic builder-ы `buildSqlObjectQualifiedName(...)` и `buildSqlObjectContextLabel(...)` сведены в [SqlConsoleObjectSqlSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectSqlSupport.kt)
  - [SqlConsoleObjectNavigationSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectNavigationSupport.kt) больше не дублирует ту же string-building логику для navigation target.
- module_editor field header cleanup:
  - повторяющийся label/help markup вынесен в локальный `ConfigFieldHeader(...)` внутри [ModuleEditorConfigFormFieldSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormFieldSupport.kt)
  - `CommitTextField / CommitTextareaField / CommitSelectField / CommitNumericTextField` больше не держат одинаковые `Span(config-form-label/help)` ветки.
- module_editor external SQL alert text cleanup:
  - pure copy для внешней SQL-ссылки вынесена из web form-layer в shared [ModuleEditorLabels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorLabels.kt)
  - [ModuleEditorConfigFormFieldSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormFieldSupport.kt) больше не держит route/storage-specific alert text inline.
- module_editor shell action cleanup:
  - дублирующийся link-button `История и результаты` вынесен из двух toolbar-веток в локальный `RunsHistoryLinkButton(...)` внутри [ModuleEditorShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorShellSections.kt)
  - DB/files toolbar больше не держат две одинаковые anchor-конструкции с `buildRunsHref(...)`.
- sql_console library toggle cleanup:
  - checkbox-блоки `Read-only` и `Autocommit` сведены к одному `SqlConsoleSettingToggle(...)` внутри [SqlConsoleLibrarySections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleLibrarySections.kt)
  - library sections больше не держат две почти одинаковые `Label + Input(type=Checkbox)` ветки.
- sql_console navigation support cleanup:
  - `SqlObjectNavigationTarget` и `matches(...)` схлопнуты в общий object helper-слой [SqlConsoleObjectSqlSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectSqlSupport.kt)
  - отдельный tiny-файл [SqlConsoleObjectNavigationSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectNavigationSupport.kt) удален как слой без самостоятельной architectural ценности.
- module_runs support cleanup:
  - остаточный [ModuleRunsPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPageSupport.kt) удален
  - `backHref` перенесен в [ModuleRunsRoute.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsRoute.kt)
  - `technicalDiagnosticsJson` локализован в [ModuleRunsPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPageSections.kt), чтобы feature page больше не зависел от отдельного support-файла ради одной константы.
- run_history_cleanup label cleanup:
  - pure copy/helper-логика вынесена из web в shared [RunHistoryCleanupLabels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupLabels.kt)
  - [RunHistoryCleanupPageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupPageSupport.kt) удален
  - web cleanup-экран теперь держит только browser/compose formatting usage, а не дублирует shared copy-логику.
- home page support cleanup:
  - page-local helper-ы `buildModeStatusText / parseModeAccessError / buildModeAccessAlertText` локализованы прямо в [HomePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/home/HomePage.kt)
  - отдельный [HomePageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/home/HomePageSupport.kt) удален как лишний одноразовый support-файл.
- SQL store cleanup:
  - общее переключение `selectedSourceNames` вынесено в [SqlConsoleStateSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStateSupport.kt)
  - `SqlConsoleStore` и `SqlConsoleObjectsStore` больше не держат одинаковые локальные реализации toggle-логики.
- legacy-терминология убирается и из test-layer:
  - тесты exporter/importer в `core` переименованы под актуальные классы `PostgresSourceExporter` и `PostgresTargetImporter`, без сохранения старых `PostgresExporter/PostgresImporter` имен.
- sql_console result placeholder cleanup:
  - повторяющиеся `sql-result-placeholder` ветки сведены к локальному `SqlResultPlaceholder(...)` в [SqlConsoleResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultSections.kt)
  - секции результата больше не дублируют один и тот же empty/pending placeholder markup в нескольких местах.
- module_editor metadata value-shell cleanup:
  - общий layout `module-metadata-value + help text` сведен к локальному `MetadataValueBlock(...)` в [ModuleEditorMetadataSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorMetadataSections.kt)
  - editable row и checkbox field больше не держат параллельные value-wrapper ветки с одинаковым help-text rendering.
- sql_console objects hero nav cleanup:
  - повторяющиеся hero navigation button-ы сведены к локальному `ObjectsNavActionButton(...)` в [SqlConsoleObjectsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPage.kt)
  - экран объектов БД больше не держит три отдельные `btn/anchor/button` ветки для одного и того же navigation pattern.
- module_editor collection card cleanup:
  - повторяющийся shell `config-form-card/config-form-card-body/config-form-fields` для `sources/quotas` сведен к локальному `ConfigCollectionCard(...)` в [ModuleEditorConfigFormSourceSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormSourceSections.kt)
  - source/quota sections больше не держат две параллельные card-header/remove/content ветки с одинаковым layout.
- sql_console favorite action cleanup:
  - action-button strip в `Избранных объектах` сведен к локальному `SqlFavoriteObjectActionButton(...)` в [SqlConsoleLibrarySections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleLibrarySections.kt)
  - favorite object cards больше не повторяют пять одинаковых `btn + type=button + onClick` веток вручную.
- module_editor workspace action cleanup:
  - create-panel actions и SQL-catalog toolbar actions сведены к одному `WorkspaceActionButton(...)` в [ModuleEditorWorkspaceSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorWorkspaceSections.kt)
  - workspace слой больше не держит два разных helper-а для одного и того же button pattern с различием только в `btn-sm` и disabled-state.
- module_editor metadata row-shell cleanup:
  - общий row-shell для `read-only / editable / checkbox` строк сведен к `MetadataRowShell(...)` в [ModuleEditorMetadataSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorMetadataSections.kt)
  - metadata section больше не держит три параллельные ветки `module-metadata-row + label` markup.
- module_editor shell toolbar cleanup:
  - общий wrapper `module-editor-toolbar/module-editor-toolbar-row` сведен к локальному `EditorToolbar(...)` в [ModuleEditorShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorShellSections.kt)
  - DB/files toolbar-ветки больше не дублируют одинаковый shell вокруг toolbar groups.
- sql_console objects panel/action cleanup:
  - panel shell для `Текущий объект / Избранные объекты / Sources / Поиск объектов` сведен к локальному `SqlObjectPanel(...)` в [SqlConsoleObjectsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPage.kt)
  - object card action-button markup сведен к `SqlObjectActionButton(...)`, поэтому `favorite/select/count` actions больше не повторяют один и тот же `btn-sm` pattern вручную.
- sql_console output pane cleanup:
  - повторяющийся wrapper `sql-output-pane + active` сведен к локальному `OutputPane(...)` в [SqlConsoleResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultSections.kt)
  - `data/status` pane больше не держат две параллельные shell-ветки с одинаковым active-state markup.
- sql_console muted result text cleanup:
  - повторяющиеся `small text-secondary mb-3` summary-блоки сведены к локальному `ResultMutedText(...)` в [SqlConsoleResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultSections.kt)
  - select/status/pending ветки больше не размазывают одинаковый muted summary markup по нескольким функциям.
- sql_console library action cleanup:
  - query picker и favorite object actions сведены к общему `SqlLibraryActionButton(...)` в [SqlConsoleLibrarySections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleLibrarySections.kt)
  - library слой больше не держит отдельные `apply/clear/favorite object` button helper-ветки для одного и того же `btn-sm` action pattern.
- module_editor hero nav cleanup:
  - hero navigation button-ы сведены к локальному `ModuleEditorNavActionButton(...)` в [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt)
  - editor page больше не держит отдельные `anchor + disabled button` ветки для одного и того же hero navigation pattern.
- module_editor catalog sidebar cleanup:
  - левый каталог модулей сведен к локальному `ModuleCatalogSidebar(...)` в [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt)
  - page-level wiring больше не смешивает route actions, includeHidden toggle, catalog status и list rendering в одной большой inline-ветке.
- module_editor catalog item cleanup:
  - rendering одного элемента каталога вынесен в `ModuleCatalogListItem(...)` в [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt)
  - module list больше не держит inline badge/title/tag rendering внутри большого sidebar-блока.
- module_editor config-form action cleanup:
  - action button-ы `Собрать форму заново / Перечитать из application.yml` сведены к `ConfigFormActionButton(...)` в [ModuleEditorConfigForm.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigForm.kt)
  - settings form больше не держит две отдельные button-ветки для одного и того же secondary action pattern.
- sql_console editor block cleanup:
  - muted text и button shell-ы для outline/statement selection сведены к `SqlEditorMutedText(...)`, `SqlEditorOutlineButton(...)` и `SqlEditorStatementButton(...)` в [SqlConsoleEditorSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleEditorSections.kt)
  - editor sections больше не повторяют одинаковые `small text-secondary` и `btn btn-sm` ветки между outline и statement selector.
- sql_console source-result panel cleanup:
  - panel header и muted text для результатов по source сведены к `SqlObjectSourceResultPanel(...)` и `SqlObjectSourceMutedText(...)` в [SqlConsoleObjectsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPage.kt)
  - source result list больше не держит повторяющиеся header/summary/empty-state shell-ветки на каждом source.
- sql_console objects control cleanup:
  - source selection checkbox и search action сведены к `SqlObjectSourceCheckbox(...)` и `SqlObjectSearchButton(...)` в [SqlConsoleObjectsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPage.kt)
  - object browser больше не держит inline checkbox/search button markup внутри основного экранного файла.
- module_editor run status cleanup:
  - muted status text и validation severity badge сведены к `EditorRunMutedText(...)` и `ValidationSeverityBadge(...)` в [ModuleEditorRunSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorRunSections.kt)
  - run panel больше не держит повторяющиеся `text-secondary small` и `severity badge` ветки в loading/empty/validation paths.
- sql_console toolbar action cleanup:
  - основная toolbar action-strip сведен к `SqlToolbarActionButton(...)` в [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt)
  - workspace toolbar больше не повторяет восемь почти одинаковых `btn + type=button + disabled + onClick` веток для run/stop/commit/export действий.
- sql_console page shell cleanup:
  - hero navigation button-ы сведены к `SqlConsoleNavActionButton(...)` в [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt)
  - source sidebar вынесен в `SqlConsoleSourceSidebar(...)`, а source selection в `SqlConsoleSourceSelectionBlock(...)` и `SqlConsoleSourceCheckbox(...)`
  - page-level layout больше не смешивает hero, settings, source selection и sidebar wiring в одной большой inline-ветке.
- sql_console credentials panel cleanup:
  - credentials upload/details вынесены из page-layout в `SqlConsoleCredentialsPanel(...)` в [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt)
  - SQL-консоль больше не держит inline file-input/upload status flow внутри общего sidebar-блока.
- module_editor page shell cleanup:
  - hero art вынесен в `ModuleEditorHeroArt(...)`, `DatabaseModuleHeroArt(...)` и `FilesModuleHeroArt(...)`
  - catalog actions вынесены в `ModuleCatalogActionBar(...)`, а loading/list body в `ModuleCatalogBody(...)`
  - [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt) стал ближе к shell/wiring-слою и меньше смешивает hero, catalog actions и catalog body.
- sql_console workspace panel cleanup:
  - правая workspace-ветка вынесена из [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt) в `SqlConsoleWorkspacePanel(...)`
  - page-level shell больше не смешивает query library, favorites, Monaco editor, toolbar и output panel в одной длинной inline-ветке.
- sql_console workspace toolbar cleanup:
  - toolbar с page-size selector и query actions вынесен в `SqlConsoleWorkspaceToolbar(...)` в [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt)
  - run/stop/commit/export control flow больше не живет inline внутри общего workspace layout.
- module_editor content pane cleanup:
  - правая content-ветка вынесена из [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt) в `ModuleEditorContentPane(...)`
  - loading/empty/session/credentials/tabs/create-module/dialog flow больше не смешивается в основном page-shell.
- sql_console page file split cleanup:
  - screen subflows вынесены из [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt) в [SqlConsolePageShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageShellSections.kt)
  - `SqlConsolePage.kt` сокращен до shell/wiring-слоя, а sidebar/workspace/toolbar flow больше не держится внутри одного page-файла.
- module_editor page file split cleanup:
  - hero/content subflows вынесены из [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt) в [ModuleEditorPageShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPageShellSections.kt)
  - сам page-файл больше не держит крупный content flow и hero-art реализацию inline.
- module_editor catalog file split cleanup:
  - catalog/sidebar list flow вынесен из [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt) в [ModuleEditorCatalogSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorCatalogSections.kt)
  - catalog actions/body/item rendering теперь живут отдельным срезом, а не внутри основного page-файла.
- sql_console second-level shell split cleanup:
  - sidebar/navigation flow дальше разрезан из [SqlConsolePageShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageShellSections.kt) в [SqlConsoleSidebarSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleSidebarSections.kt)
  - workspace/toolbar flow вынесен в [SqlConsoleWorkspaceSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleWorkspaceSections.kt), а промежуточный mixed shell-файл удален.
- module_editor second-level page-shell split cleanup:
  - content/session flow дальше разрезан из [ModuleEditorPageShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPageShellSections.kt) в [ModuleEditorContentSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorContentSections.kt)
  - hero/navigation flow вынесен в [ModuleEditorHeroSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorHeroSections.kt), а промежуточный shell-файл удален.
- module_editor second-level catalog split cleanup:
  - sidebar/body flow дальше разрезан из [ModuleEditorCatalogSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorCatalogSections.kt) в [ModuleEditorCatalogSidebarSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorCatalogSidebarSections.kt)
  - rendering элемента каталога вынесен в [ModuleEditorCatalogItemSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorCatalogItemSections.kt), а старый mixed catalog-файл удален.
- sql_console objects page split cleanup:
  - shell/control секции вынесены из [SqlConsoleObjectsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPage.kt) в [SqlConsoleObjectsShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsShellSections.kt)
  - result/card секции вынесены в [SqlConsoleObjectsResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsResultSections.kt), поэтому основной page-файл больше не держит shell и result presentation в одном месте.
- sql_console result sections split cleanup:
  - data-pane flow вынесен из [SqlConsoleResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultSections.kt) в [SqlConsoleDataResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleDataResultSections.kt)
  - status-pane flow вынесен в [SqlConsoleStatusResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStatusResultSections.kt), а исходный файл остался aggregator/shared-ui слоем.
- module_editor config field split cleanup:
  - базовые text/select/checkbox form controls вынесены из [ModuleEditorConfigFormFieldSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormFieldSupport.kt) в [ModuleEditorConfigFormBasicFieldSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormBasicFieldSupport.kt)
  - numeric form controls вынесены в [ModuleEditorConfigFormNumericFieldSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigFormNumericFieldSupport.kt), а исходный файл оставлен для SQL-specific field flow.

Критерий завершения:

- в проекте уменьшено число legacy-paths;
- кодовая база чище, проще и с меньшим количеством “исторических хвостов”.

### 6. Архитектурная программа: довести SQL-консоль как самостоятельную подсистему

Статус:

- частично реализовано

Цель:

- перестать относиться к SQL-консоли как к обычному экрану;
- довести execution lifecycle, safety и архитектурные границы до устойчивого состояния.

Что уже есть:

- отдельные документы по failure scenarios и Monaco autocomplete;
- async execution;
- manual `Commit / Rollback`;
- object browser;
- IDE-like UX;
- отдельные support/contract-слои в backend.

Что нужно сделать:

- добить safety long-running execution;
- дочистить lifecycle pending commit / rollback;
- продолжить cleanup giant UI/page/store слоя SQL-консоли;
- закрепить SQL-консоль отдельными архитектурными и тестовыми инвариантами.

Критерий завершения:

- SQL-консоль рассматривается и поддерживается как самостоятельная подсистема, а не как “еще один экран”.

### 7. SQL-консоль: safety hardening по аварийным сценариям

Статус:

- не реализовано

Источник:

- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)

Цель:

- исключить orphan execution и висящие транзакции;
- довести SQL-консоль до состояния, где безопасность БД обеспечивается server-side механизмами, а не надеждой на живую вкладку браузера.

Что нужно сделать:

- owner session и owner token для execution session;
- heartbeat во время `RUNNING` и `PENDING_COMMIT`;
- lease timeout и hard TTL;
- automatic rollback при потере владельца;
- системные статусы rollback по timeout/lost owner;
- tests на:
  - close tab;
  - refresh;
  - network loss;
  - duplicate tab;
  - server restart.

Критерий завершения:

- manual transaction не может жить бесконтрольно;
- при потере владельца система гарантированно уходит в безопасный rollback.

## P1

### 9. Операционная надежность long-running операций

Статус:

- частично реализовано

Цель:

- сделать длинные операции предсказуемыми, безопасными и наблюдаемыми.

Что входит:

- batch runs;
- DB runs;
- cleanup/retention;
- SQL execution;
- отмена;
- закрытие вкладки;
- рестарт `ui-server`;
- orphan state и восстановление после сбоев.

Что нужно сделать:

- выровнять lifecycle-модели между подсистемами;
- убрать неочевидные pending-состояния;
- закрепить политику rollback / cleanup / recovery;
- усилить server-side и scenario-level тесты.

### 10. Cleanup и укрощение толстых store-слоев

Статус:

- не реализовано

Цель:

- не допустить, чтобы `ui-compose-shared` превратился в mini-backend.

Первый приоритет:

- [ModuleEditorStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStore.kt)
- store-слои SQL-консоли;
- store-слои maintenance/run-history экранов.

Что нужно сделать:

- отделить state management от тяжелой orchestration-логики;
- не держать в одном store:
  - загрузку;
  - mutation lifecycle;
  - runtime branching;
  - UX-политику;
  - error normalization.

### 12. Финализировать boundary модульного редактора и storage contracts

Статус:

- не реализовано

Цель:

- дочистить архитектурные границы вокруг единого module editor;
- убрать оставшиеся file/db-specific leakage из editor/store/service слоев.

Что нужно сделать:

- проверить, где editor еще знает слишком много о storage mode;
- дочистить контракты around:
  - module catalog;
  - module editor store;
  - SQL resource lifecycle;
  - metadata / lifecycle / publish / working copy semantics;
- убедиться, что `Module Editor` действительно остается единым экраном с разными storage adapters, а не двумя логиками в одном файле;
- убрать устаревшие переходные решения, если они остались после реализации DB/files режима.

Критерий завершения:

- storage mode остается implementation detail для значимой части UI;
- module editor и связанные backend contracts становятся чище и предсказуемее.

### 11. Зафиксировать и поддерживать repo-level архитектурную дисциплину

Статус:

- частично реализовано

Что уже есть:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [AGENTS.md](/Users/kwdev/DataPoolLoader/AGENTS.md)

Что нужно сделать:

- держать архитектурные требования в [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md) как в едином живом документе;
- закрепить обязательную code-quality дисциплину на уровне правил разработки:
  - смотреть на код sonar-like взглядом;
  - не пропускать дублирование;
  - не тащить мертвый код и legacy-хвосты;
  - отслеживать file/method/class size, сложность и скрытую связанность;
- не допускать расхождения между кодом и repo-level правилами;
- при заметных архитектурных изменениях обновлять документацию в том же change set;
- после каждого большого этапа backlog выполнять отдельный архитектурный review и по его итогам корректировать backlog и правила.

## P2

### 16. Усилить тестовую стратегию под архитектурную программу

Статус:

- не реализовано

Цель:

- сделать архитектурный рефакторинг безопасным.

Что нужно сделать:

- добавить больше store-level tests;
- усилить server contract tests;
- добавить smoke-покрытие на критичные UI-сценарии;
- отдельно страховать:
  - SQL console lifecycle;
  - run lifecycle;
  - cleanup/retention;
  - runtime fallback scenarios.

### 17. Формализовать карту состояния проекта

Статус:

- не реализовано

Цель:

- иметь отдельный инженерный документ, в котором перечислены:
  - все значимые persisted состояния;
  - все runtime state;
  - их source of truth;
  - правила восстановления и очистки.

Это нужно для дальнейшего рефакторинга и для предотвращения повторного смешения state-моделей.

### 18. SQL-консоль: staged Monaco hints и autocomplete для PostgreSQL

Статус:

- не реализовано

Источник:

- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)

Цель:

- развивать Monaco постепенно, без тяжелого IDE/LSP-стека и без ущерба для архитектуры SQL-консоли.

Этапы:

- уровень 1:
  - local completion по keywords/functions/snippets;
  - hover hints;
- уровень 2:
  - metadata-aware autocomplete по schema/table/view/index names;
  - bounded search-first completion по объектам БД;
  - exact object column lookup там, где это оправдано;
- уровень 3:
  - deeper language intelligence только при доказанной необходимости.

Критерий завершения:

- Monaco становится ощутимо полезнее;
- autocomplete не ломает execution lifecycle и не тащит тяжелую stateful backend-механику.

## P3

### 20. Product и UX-задачи, не влияющие на архитектурную программу

Статус:

- отложено

Сюда временно относятся все задачи, которые:

- не повышают архитектурный порядок;
- не улучшают надежность;
- не уменьшают structural debt;
- не помогают cleanup legacy.

Они возвращаются в активный backlog только после завершения текущей архитектурной волны или при отдельном явном переприоритизировании.
