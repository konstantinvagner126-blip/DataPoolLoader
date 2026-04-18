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

Что осталось:

- продолжить рефакторинг:
  - [ApplicationRunner.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/app/ApplicationRunner.kt)
  - [SqlConsoleService.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleService.kt)
  - [DatabaseModuleStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/module/DatabaseModuleStore.kt)
  - при необходимости [RunManager.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunManager.kt) и [DatabaseRunStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/DatabaseRunStore.kt)


## Рекомендуемый порядок выполнения

1. Добить `P0.0` по онлайн-прогрессу.
2. При необходимости закрыть остатки `P1.9` по runtime-aware DB-экранам.
3. Продолжить рефакторинг крупных классов.
4. Вернуться к `multi-statement SQL` как к отдельному большому этапу.
