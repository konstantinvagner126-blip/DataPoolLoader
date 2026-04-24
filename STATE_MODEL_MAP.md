# State Model Map

Дата: 2026-04-23

Назначение документа:

- зафиксировать текущую карту значимых состояний проекта;
- развести persisted local state, runtime state, operational history, UI preferences и operational config;
- дать единый инженерный reference для следующих рефакторингов и новых stateful изменений.

Ограничение области:

- документ описывает прежде всего UI-owned state в `ui-server`, `ui-compose-shared` и связанных runtime boundary;
- доменные данные `core`, файловое содержимое модулей и DB-таблицы рассматриваются только там, где UI владеет их runtime-orchestration или локальным persisted mirror;
- это карта состояний, а не полный dump всех storage schema.

Связанные документы:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [BACKLOG.md](/Users/kwdev/DataPoolLoader/BACKLOG.md)
- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)

## 1. Таксономия состояния

В проекте используются только следующие базовые типы состояния:

- `transient runtime state`:
  process-local или browser-local состояние, которое живет только пока жив процесс или открыта страница.
- `persisted local state`:
  локальное состояние пользователя, сохраняемое на диск между перезапусками.
- `source of truth`:
  слой, который считается каноническим для конкретного решения и от которого зависят остальные проекции.
- `cache`:
  производное состояние, которое можно пересоздать из source of truth без потери смысла.
- `UI preference`:
  пользовательские настройки представления и удобства, но не execution/history state.
- `operational history`:
  локальная история фактически выполненных действий и их outcomes.
- `operational config`:
  конфигурация приложения и runtime mode, задаваемые через `ui-application.yml`, а не через UI state JSON.

Базовые инварианты:

- `ui-application.yml` и `UiAppConfig` не считаются UI preference state;
- `ui-compose-shared` page/store state по умолчанию не считается persisted state;
- active execution / ownership / active run registries никогда не считаются recoverable persisted state;
- combined legacy state files допускаются только в migration-only роли.

## 2. Operational Config

### 2.1. UI config

- Контракт:
  [UiConfig.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/config/UiConfig.kt)
- Loader:
  [UiConfigLoader](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/config/UiConfig.kt#L70)
- Persistence boundary:
  [UiConfigPersistenceService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/config/UiConfigPersistenceService.kt)

Классификация:

- тип: `operational config`
- source of truth: внешний `ui-application.yml` или classpath `application.yml`
- что включает:
  `moduleStore`, `sqlConsole`, `kafka` cluster catalog и Kafka operation limits
- recovery: на каждом старте конфиг перечитывается заново
- cleanup: только через явное изменение config-файла; не должен смешиваться с JSON state-файлами

### 2.2. Runtime mode / actor / database availability

- Контракт:
  [UiRuntimeContextService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/config/UiRuntimeContextService.kt)

Классификация:

- тип: `transient runtime state`
- source of truth: `UiAppConfig` + OS actor resolution + database availability + schema migration check
- persistence: отсутствует
- recovery: полностью пересчитывается на старте процесса
- cleanup: не требует отдельной очистки; исчезает при завершении процесса

## 3. Persisted Local State в `ui-server`

### 3.1. Run history

- Модель:
  [PersistedRunState.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/PersistedRunState.kt)
- Store:
  [RunStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunStateStore.kt)
- Файл:
  `run-state.json`

Классификация:

- тип: `operational history`
- source of truth: persisted `UiRunSnapshot` history в `run-state.json`
- recovery:
  на загрузке `RUNNING` snapshots переводятся в `FAILED` через `withRecoveredInterruptedRuns()`
- cleanup:
  история сокращается и переписывается через maintenance/retention сценарии, а не через ad-hoc mutation в UI

### 3.2. Uploaded credentials

- Модель:
  [PersistedCredentialsState.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/PersistedCredentialsState.kt)
- Store:
  [UiCredentialsStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/UiCredentialsStateStore.kt)
- Файл:
  `credentials-state.json`

Классификация:

- тип: `persisted local state`
- source of truth: отдельный credentials-state файл
- recovery:
  состояние восстанавливается на старте без привязки к run history
- cleanup:
  legacy `uploadedCredentials` мигрируются из `run-state.json` и удаляются из legacy места хранения

### 3.3. SQL console workspace state

- Модель:
  [PersistedSqlConsoleWorkspaceState.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/PersistedSqlConsoleWorkspaceState.kt)
- Store:
  [SqlConsoleWorkspaceStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleWorkspaceStateStore.kt)
- Service:
  [SqlConsoleStateService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleStateService.kt)
- Retention:
  [SqlConsoleWorkspaceRetentionService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleWorkspaceRetentionService.kt)
- Файлы:
  `sql-console-workspace-state.json`, `sql-console-workspace-state-<workspace>.json`

Классификация:

- тип: `persisted local state`
- source of truth: per-workspace persisted JSON + write-through in-memory cache в `SqlConsoleStateService`
- что хранится:
  `draftSql`, `selectedGroupNames`, `selectedSourceNames`, `lastAccessedAt`
- recovery:
  default workspace может мигрировать из legacy combined state; остальные workspace восстанавливаются из своих файлов
- cleanup:
  отсутствующие файлы означают default state; combined legacy file не должен возвращаться как source of truth;
  non-default workspace file может быть удален retention-cleanup после `30 days` inactivity;
  access в текущем процессе пинит workspace от cleanup до завершения процесса

### 3.4. SQL console library state

- Модель:
  [PersistedSqlConsoleLibraryState.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/PersistedSqlConsoleLibraryState.kt)
- Store:
  [SqlConsoleLibraryStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleLibraryStateStore.kt)

Классификация:

- тип: `operational history`
- source of truth: `sql-console-library-state.json`
- что хранится:
  `recentQueries`, `favoriteQueries`, `favoriteObjects`
- замечание:
  favorites и recent history живут в одном library boundary, но этот boundary отделен от workspace state и preferences
- recovery:
  возможна миграция из legacy combined SQL-console state
- cleanup:
  normalization ограничивает размер и удаляет дубли

### 3.5. SQL console preferences state

- Модель:
  [PersistedSqlConsolePreferencesState.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/PersistedSqlConsolePreferencesState.kt)
- Store:
  [SqlConsolePreferencesStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsolePreferencesStateStore.kt)

Классификация:

- тип: `UI preference`
- source of truth: `sql-console-preferences-state.json`
- что хранится:
  `pageSize`, `strictSafetyEnabled`, `transactionMode`
- recovery:
  миграция возможна из legacy combined state
- cleanup:
  normalization оставляет только допустимые значения

### 3.6. SQL console execution history

- Модель:
  [PersistedSqlConsoleExecutionHistoryState.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/PersistedSqlConsoleExecutionHistoryState.kt)
- Store:
  [SqlConsoleExecutionHistoryStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleExecutionHistoryStateStore.kt)
- Service:
  [SqlConsoleExecutionHistoryService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleExecutionHistoryService.kt)
- Retention:
  [SqlConsoleWorkspaceRetentionService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleWorkspaceRetentionService.kt)
- Файлы:
  `sql-console-execution-history-state.json`, `sql-console-execution-history-state-<workspace>.json`

Классификация:

- тип: `operational history`
- source of truth: per-workspace execution history JSON + write-through service cache
- recovery:
  история восстанавливается на старте; entries нормализуются и ограничиваются по лимиту
- cleanup:
  устаревшие записи вытесняются лимитом; active execution не восстанавливается из этой истории;
  non-default workspace history file участвует в том же `30 days` retention cleanup, что и workspace-state pair;
  cleanup не должен удалять history pinned workspace текущего процесса

### 3.7. Legacy SQL console state

- Модель:
  [LegacySqlConsoleState.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/LegacySqlConsoleState.kt)
- Store:
  [LegacySqlConsoleStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/LegacySqlConsoleStateStore.kt)
- Cleanup:
  [SqlConsoleLegacyStateCleanup.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleLegacyStateCleanup.kt)

Классификация:

- тип: `migration-only legacy state`
- source of truth: не является source of truth
- recovery:
  читается только если split state files еще не созданы
- cleanup:
  удаляется после успешной миграции `workspace + library + preferences`

## 4. Process-Local Runtime State в `ui-server`

### 4.1. RunManager runtime snapshots

- Runtime owner:
  [RunManager.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunManager.kt)
- Runtime support:
  [RunManagerRuntimeStateSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunManagerRuntimeStateSupport.kt)

Классификация:

- тип: `transient runtime state`
- source of truth while process is alive: in-memory `snapshots` list
- persisted mirror: `RunStateStore`
- recovery:
  на инициализации runtime snapshots восстанавливаются из persisted history
- cleanup:
  publish path persist-ит и переэмитит state; after restart only persisted history survives

### 4.2. Active DB run registry

- Контракт:
  [DatabaseModuleActiveRunRegistry.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/DatabaseModuleActiveRunRegistry.kt)

Классификация:

- тип: `transient runtime state`
- source of truth: process-local concurrent map `moduleCode -> runId`
- recovery:
  отсутствует; на рестарте реестр очищается
- cleanup:
  `clear(moduleCode, runId)` или завершение процесса

### 4.3. SQL console active executions

- Runtime owner:
  [SqlConsoleQueryManager.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleQueryManager.kt)
- Active execution model:
  [ActiveExecution.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/ActiveExecution.kt)

Классификация:

- тип: `transient runtime state`
- source of truth while query is active: in-memory execution registry inside query state support
- recovery:
  отсутствует; server restart обрывает active execution lifecycle
- cleanup:
  `cancel`, `commit`, `rollback`, owner-loss timeout, pending-commit TTL, explicit release, process restart
- важный инвариант:
  persisted SQL state и execution history не являются механизмом восстановления active ownership/control-path

### 4.4. SQL console service caches

- Workspace/library/preferences cache:
  [SqlConsoleStateService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleStateService.kt)
- Execution history cache:
  [SqlConsoleExecutionHistoryService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleExecutionHistoryService.kt)

Классификация:

- тип: `cache`
- source of truth: соответствующие persisted state files
- recovery:
  лениво поднимаются из store на первом обращении
- cleanup:
  replacement/normalization идет через save path; process restart полностью сбрасывает cache слой

### 4.5. Update flows and server context

- Ktor/server context:
  [UiServerContext.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerContext.kt)

Классификация:

- тип: `transient runtime wiring`
- source of truth: composition root на старте `ui-server`
- recovery:
  нет отдельного recovery; собирается заново на каждый старт
- cleanup:
  уничтожается вместе с процессом

## 5. Shared UI State в `ui-compose-shared`

Базовое правило этого слоя:

- `ui-compose-shared` владеет browser/client-side page state и route state;
- это состояние не считается persisted source of truth, если отдельно не существует server-side persisted boundary;
- refresh должен либо заново загрузить state с сервера, либо восстановить только те части, для которых уже есть отдельный persisted contract в `ui-server`.

### 5.1. SQL console page state

- Контракты:
  [SqlConsoleModels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleModels.kt)

Классификация:

- тип: `transient runtime state`
- source of truth: shared store + server responses
- persisted anchors:
  `SqlConsoleStateSnapshot` и execution history загружаются из `ui-server` persisted state
- не является source of truth для:
  active SQL execution ownership, persisted preferences, workspace persistence

### 5.2. Module editor state

- Контракты:
  [ModuleEditorModels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorModels.kt)

Классификация:

- тип: `transient runtime state`
- source of truth: current editor session response + локальные draft mutations пользователя
- recovery:
  page refresh пересобирает state из server-side catalog/session loading, а не из client-side persisted snapshot
- cleanup:
  route switch, reload session, explicit save/publish/discard, page close

### 5.3. Module runs state

- Контракты:
  [ModuleRunsModels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsModels.kt)

Классификация:

- тип: `transient runtime state`
- source of truth: server responses `session/history/details` + local filter/selection state
- recovery:
  page refresh загружает данные заново; selected run не считается persisted state

### 5.4. Module sync state

- Контракты:
  [ModuleSyncModels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_sync/ModuleSyncModels.kt)

Классификация:

- тип: `transient runtime state`
- source of truth: current sync state, run list and selected details from server
- recovery:
  page refresh запрашивает текущее состояние заново; local selections/messages не восстанавливаются отдельно

### 5.5. Run history cleanup state

- Контракты:
  [RunHistoryCleanupModels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/run_history_cleanup/RunHistoryCleanupModels.kt)

Классификация:

- тип: `transient runtime state`
- source of truth: preview/result responses from server + local safeguard toggles
- recovery:
  preview/result screen state не считается persisted history и не восстанавливается после refresh

### 5.6. Kafka explorer state

- Контракты:
  [KafkaModels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaModels.kt)

Классификация:

- тип: `transient runtime state`
- source of truth: server responses `info/topics/topicOverview/messages/settings` + local page drafts для message browser, produce и settings UI
- route-owned recovery anchors:
  `clusterId`, `topic`, `query`, `pane`, `scope`, `mode`, `partition`
- не является source of truth для:
  Kafka cluster catalog, TLS material, secrets и connection properties
- recovery:
  refresh восстанавливает route-driven selection и заново запрашивает metadata/config from `ui-server`;
  drafts чтения сообщений, produce и settings-editing не считаются persisted state и не должны молча переживать restart
- cleanup:
  route switch, page reload, explicit config save/reload, page close

## 6. Правила восстановления и очистки

### 6.1. Что должно переживать перезапуск

Должно переживать restart:

- run history;
- uploaded credentials;
- SQL console workspace/library/preferences state;
- SQL console execution history;
- editable UI config.

### 6.2. Что не должно переживать перезапуск как active state

Не должно автоматически восстанавливаться как active runtime state:

- active SQL execution ownership;
- pending SQL control-path;
- DB active run registry;
- browser-local selected tab, local page messages, local filters без отдельного persisted boundary.

### 6.3. Legacy migration policy

- combined SQL console state допустим только как migration-only слой;
- legacy uploaded credentials inside `run-state.json` допустимы только как temporary migration input;
- новые combined JSON blobs, которые смешивают preferences, draft, execution и history, запрещены.

## 7. Практические правила для следующих изменений

Любое новое значимое состояние обязано отвечать на вопросы:

1. Это `persisted local state`, `transient runtime state`, `cache`, `UI preference`, `operational history` или `operational config`?
2. Где его source of truth?
3. Что переживает restart, а что нет?
4. Как это состояние очищается?
5. Не смешивает ли оно разные категории в одном persisted контракте?

Если изменение меняет ответы на эти вопросы, нужно обновить:

- [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md)
- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- и соответствующий пункт в [BACKLOG.md](/Users/kwdev/DataPoolLoader/BACKLOG.md), если меняется архитектурный этап
