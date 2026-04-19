# DataPoolLoader Backlog

Актуальный backlog содержит только незавершенные задачи.

Архив выполненных пунктов:

- [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md)

## Правила приоритизации

- `P0` — ближайшие задачи с максимальной пользовательской ценностью.
- `P1` — следующий обязательный слой после `P0`.
- `P2` — среднесрочное развитие.
- `P3` — техдолг и долгосрочные улучшения.

## P0

### 0. Онлайн-прогресс выполнения запуска

Статус:

- частично реализовано

Цель:

- показывать пользователю живой прогресс выполнения запуска прямо во время работы, а не только после завершения выгрузки.

Что уже сделано:

- единый progress-widget со стадиями `Подготовка -> Источники -> Объединение -> Загрузка -> Завершение`;
- анимированный индикатор активности для `RUNNING` в блоке `Ход выполнения`;
- live-обновление:
  - `FILES` через `WebSocket`;
  - `DB` через polling активного запуска;
- после нажатия `Запустить` compact-блок `Ход выполнения` сразу перечитывает историю и переключается на активный запуск;
- для `DB` начали сохраняться `SOURCE_PROGRESS` события;
- убраны стартовые success-alert сообщения о запуске;
- на карточках модулей в каталоге появился индикатор активного выполнения;
- в compact-блоке редактора появился явный `Активный источник`;
- из compact-ленты убраны технические события типа `RUN_CREATED`.
- частота live-refresh для `FILES` выровнена между editor и экраном `История и результаты`;
- из compact-ленты убраны стартовые технические события `RUN_STARTED / SOURCE_STARTED / MERGE_STARTED / TARGET_STARTED`.
- в compact-блоке live-лента теперь умеет синтезировать уже существующее сообщение прогресса вида `Источник X: выгружено N строк` из `sourceResults`, если event еще не успел попасть в ленту.

Что осталось:

- финально проверить на длинных сценариях `FILES` и `DB`, что compact-блок стабильно показывает живой прогресс без ручного refresh;
- при необходимости дочистить только UX-подачу progress-сценария между `FILES` и `DB`, без нового backend-контракта.

Критичное требование:

- пользователь должен видеть, что запуск реально выполняется:
  - анимированный индикатор активности;
  - обновление стадий;
  - появление live-сообщений о прогрессе выгрузки;
  - обновление без ручного refresh страницы.

## P1

### 9. Режим `database`: foundation

Статус:

- частично реализовано

Цель:

- поддерживать полноценный режим работы UI с модулями из PostgreSQL без смешения с файловым режимом.

Что уже сделано:

- runtime context с fallback в `FILES`;
- live-состояние import-flow для DB-страниц;
- runtime-mode control без перезапуска UI;
- route-guard для `modules / db-modules / db-sync`;
- отдельный screen обслуживания запусков и output-retention уже работает в `DB`-режиме через общий runtime-aware backend.
- экран `История и результаты` тоже стал runtime-aware:
  - загружает `runtimeContext`;
  - не пытается грузить DB-историю вслепую при fallback;
  - показывает явное warning-сообщение о недоступности БД и активном fallback-режиме.
  - при live-refresh перечитывает `runtimeContext`;
  - при переходе `DATABASE -> FILES` очищает stale DB-историю и выбранный запуск, вместо показа устаревших данных.
- SQL-консоль тоже читает `runtimeContext` и показывает явный warning при fallback `DATABASE -> FILES`, вместо немой работы без контекста.

Что осталось:

- при необходимости расширить live DB-context на остальные экраны.

## P3

### 20. `multi-statement SQL` в SQL-консоли

Статус:

- отложено как отдельный большой этап

Что нужно сделать:

- разбор SQL-скрипта на statement;
- выполнение по каждому source по порядку;
- политика ошибок:
  - `stop on first error`;
  - `continue on error`;
- транзакционный режим;
- UI результата по `statement` и по `source`.

### 21. Refactoring крупных классов

Статус:

- частично реализовано

Что уже сделано:

- `Server.kt` декомпозирован на `UiServerContext` и route-группы.
- `DatabaseModuleStore` начал распиливаться на отдельные support-компоненты:
  - [DatabaseModuleStoreSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/module/DatabaseModuleStoreSupport.kt)
  - [DatabaseModuleStoreLifecycleSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/module/DatabaseModuleStoreLifecycleSupport.kt)
  - [DatabaseModuleStoreQuerySupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/module/DatabaseModuleStoreQuerySupport.kt)
  - [DatabaseModuleStoreMutationSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/module/DatabaseModuleStoreMutationSupport.kt)
  - [DatabaseModuleStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/module/DatabaseModuleStore.kt) теперь остался тонким фасадом над query/lifecycle/mutation support-слоями
- для DB-registry добавлен интерфейсный boundary:
  - [DatabaseModuleRegistryOperations.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/module/DatabaseModuleRegistryOperations.kt)
  - `DatabaseModuleBackend`, `DatabaseModuleRunService`, `DatabaseModuleSyncImporter` и `UiServerContext` теперь зависят от контракта, а не от concrete `DatabaseModuleStore`
- для DB-run service добавлен интерфейсный boundary:
  - [DatabaseModuleRunOperations.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/DatabaseModuleRunOperations.kt)
  - server-layer и `DatabaseModuleRunHistoryService` теперь зависят от контракта, а не от concrete `DatabaseModuleRunService`
- `SqlConsoleService` начал распиливаться на отдельные support-компоненты:
  - [SqlConsoleConfigSupport.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleConfigSupport.kt)
  - [SqlConsoleJdbcSupport.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleJdbcSupport.kt)
  - [SqlConsoleStateSupport.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleStateSupport.kt)
  - [SqlConsoleExecutionSupport.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleExecutionSupport.kt)
  - [SqlConsoleService.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleService.kt) теперь остался boundary-слоем над config/state/execution support-компонентами
- для SQL-консоли добавлен сервисный интерфейс:
  - [SqlConsoleOperations.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleOperations.kt)
  - server-layer и query manager теперь зависят от контракта, а не от concrete `SqlConsoleService`
- `ApplicationRunner` начал распиливаться на отдельные support-компоненты:
  - [ApplicationRunnerSupport.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/app/ApplicationRunnerSupport.kt)
  - [ApplicationRunnerPipelineSupport.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/app/ApplicationRunnerPipelineSupport.kt)
  - [ApplicationRunner.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/app/ApplicationRunner.kt) теперь остался boundary-слоем над config loading и pipeline execution support
- `DatabaseRunStore` начал делиться на узкие контракты:
  - [DatabaseRunExecutionStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/DatabaseRunExecutionStore.kt)
  - [DatabaseRunQueryStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/DatabaseRunQueryStore.kt)
  - [DatabaseRunMaintenanceStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/DatabaseRunMaintenanceStore.kt)
  - `DatabaseModuleRunService`, cleanup и retention теперь зависят от узких контрактов, а не от одного concrete `DatabaseRunStore`
  - внутренняя логика самого store вынесена в support-компоненты:
    - [DatabaseRunStoreExecutionSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/DatabaseRunStoreExecutionSupport.kt)
    - [DatabaseRunStoreQuerySupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/DatabaseRunStoreQuerySupport.kt)
    - [DatabaseRunStoreMaintenanceSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/DatabaseRunStoreMaintenanceSupport.kt)
    - [DatabaseRunStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/DatabaseRunStore.kt) теперь остался тонким фасадом над этими слоями
- `RunManager` тоже начал отделять orchestration от persisted history:
  - [RunManagerHistorySupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunManagerHistorySupport.kt)
  - [RunManagerExecutionSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunManagerExecutionSupport.kt)
  - [RunManagerStateSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunManagerStateSupport.kt)
  - [RunManager.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunManager.kt) теперь не держит в себе целиком восстановление persisted history, cleanup-планирование, UI-state assembly и credential-aware module details
- для файлового run-слоя добавлены интерфейсные boundaries:
  - [FilesModuleRunOperations.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/FilesModuleRunOperations.kt)
  - [FilesRunHistoryMaintenanceOperations.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/FilesRunHistoryMaintenanceOperations.kt)
  - `CommonRoutes`, `FilesModuleRunHistoryService`, `FilesRunHistoryCleanupService`, `FilesOutputRetentionService` и `UiServerContext` теперь зависят от контрактов, а не от concrete `RunManager`

Что осталось:

- продолжить рефакторинг:
  - [SqlConsoleService.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleService.kt)
  - при необходимости [RunManager.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunManager.kt) как orchestration-слой и дальнейшее упрощение внутренних support-слоев DB run-store


## Рекомендуемый порядок выполнения

1. Добить `P0.0` по онлайн-прогрессу.
2. При необходимости закрыть остатки `P1.9` по runtime-aware DB-экранам.
3. Продолжить рефакторинг крупных классов.
4. Вернуться к `multi-statement SQL` как к отдельному большому этапу.
