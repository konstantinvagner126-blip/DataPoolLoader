# DataPoolLoader Backlog

## Правила приоритизации

- `P0` — ближайшие задачи, которые дают максимальную пользовательскую ценность и должны идти первыми.
- `P1` — важные задачи следующего этапа после `P0`.
- `P2` — среднесрочное развитие.
- `P3` — долгосрочные улучшения и техдолг.

## P0

### 1. Визуальный редактор конфигурации модуля

Статус:

- не реализовано

Цель:

- дать пользователю возможность настраивать модуль не только через raw `application.yml`, но и через визуальную форму.

Что нужно сделать:

- добавить в редакторе модуля вкладку `Форма` рядом с вкладкой `YAML`;
- отрисовывать основные поля конфига через визуальные компоненты:
  - checkbox;
  - radio button;
  - select;
  - text input;
  - number input;
- покрыть в первой версии основные поля:
  - `app.outputDir`
  - `app.mergeMode`
  - `app.parallelism`
  - `app.fetchSize`
  - `app.queryTimeoutSec`
  - `app.progressLogEveryRows`
  - `app.maxMergedRows`
  - `app.deleteOutputFilesAfterCompletion`
  - `app.target.enabled`
  - `app.target.table`
  - `app.target.truncateBeforeLoad`
- сделать основу для дальнейшего визуального редактирования `sources` и `quotas`.

Критичное требование:

- двусторонняя синхронизация в реальном времени:
  - изменение YAML сразу обновляет форму;
  - изменение формы сразу обновляет YAML.

Ожидаемый результат:

- пользователь может работать с конфигом как через YAML, так и через форму без рассинхронизации.

### 2. Улучшение экрана истории запусков

Статус:

- частично есть базовая история, но UX недостаточен

Что нужно сделать:

- сделать историю запусков более заметной и удобной;
- показать основные атрибуты запуска:
  - модуль;
  - время;
  - статус;
  - merged rows;
  - target status;
- добавить быстрый переход к результатам конкретного запуска.

### 3. Нормальный просмотр summary в UI

Статус:

- не реализовано как полноценный экран

Что нужно сделать:

- отображать `summary.json` в виде структурированных блоков, а не raw JSON;
- выделить:
  - merged rows;
  - merge mode;
  - target status;
  - successful sources;
  - failed sources;
  - source allocations.

### 4. Улучшение UX главного UI

Статус:

- частично реализовано

Что нужно сделать:

- сделать экраны компактнее и чище;
- улучшить отображение ошибок, warning и состояния выполнения;
- добавить короткие подсказки по:
  - `credential.properties`;
  - timeout;
  - output;
  - запуску модулей.

### 5. Переработка страницы справки

Статус:

- реализовано частично, но UX и подача пока слабые

Что нужно сделать:

- сделать справку ориентированной на пользователя, а не на внутреннюю структуру проекта;
- отдельно и понятно описать:
  - модуль загрузки данных;
  - SQL-консоль;
- объяснить:
  - как выглядит `application.yml`;
  - какие параметры обязательные;
  - какие значения используются по умолчанию;
  - как работает `credential.properties`;
  - как запускать сценарии через UI и напрямую;
- улучшить визуальную подачу:
  - меньше текста полотном;
  - больше понятных блоков, карточек и схем;
  - четкие сценарии "что сделать сначала / потом";
- не показывать в справке внутренние test-only сценарии и служебные детали, если они не нужны пользователю.

## P1

### 6. Экспорт результатов SQL-консоли

Цель:

- дать возможность сохранять результаты ручных `SELECT`-запросов.

Что нужно сделать:

- экспорт CSV по активному source;
- экспорт ZIP со всеми source-результатами;
- понятные кнопки скачивания в UI.

### 7. Повторное использование SQL-запросов

Цель:

- ускорить ручную работу в SQL-консоли.

Что нужно сделать:

- сохранить последние запросы;
- дать быстрый выбор ранее выполнявшихся запросов;
- предусмотреть базовый набор “избранных” SQL.

### 8. Guardrails для опасных SQL-команд

Цель:

- снизить риск случайных деструктивных операций.

Что нужно сделать:

- отдельные confirm-диалоги для опасных команд;
- визуально усиленные warnings для:
  - `DROP`
  - `TRUNCATE`
  - `ALTER`
  - других потенциально опасных DDL/DML;
- подумать над режимом “строгой защиты”.

### 9. Режим `database`: foundation

Статус:

- частично реализовано

Цель:

- добавить полноценный режим работы UI с модулями из PostgreSQL без смешения с файловым режимом.

Фичи:

- поддержка двух режимов:
  - `files`
  - `database`
- slider `FILES / DB` в UI;
- fallback в `FILES`, если PostgreSQL недоступна;
- отдельный DB-context backend:
  - доступность БД;
  - состояние `appsRoot`;
  - активный import;
  - actor;
- отдельные DB-страницы и DB-роутинг без смешения с file-flow.

Выполнено:

- `2026-04-15`: добавлен backend runtime context:
  - `ui.moduleStore.mode`;
  - PostgreSQL availability check;
  - actor detection;
  - fallback в `FILES`;
  - endpoint `GET /api/ui/runtime-context`.

Осталось:

- slider `FILES / DB` в UI;
- отдельные DB-страницы;
- отображение active import / DB-context в UI.

### 10. Flyway и схема `ui_registry`

Статус:

- частично реализовано

Цель:

- создать устойчивую DB-схему для registry, drafts, sync и истории запусков.

Фичи:

- `Flyway` для режима `database`;
- baseline migration:
  - `V1__create_ui_registry.sql`
- таблицы:
  - `module`
  - `module_revision`
  - `module_revision_source`
  - `module_revision_target`
  - `module_revision_quota`
  - `module_revision_sql_asset`
  - `module_working_copy`
  - `execution_snapshot`
  - `module_sync_run`
  - `module_sync_run_item`
  - `module_run`
  - `module_run_source_result`
  - `module_run_event`
  - `module_run_artifact`
- индексы, FK и `check`-ограничения;
- генерация `uuid` в приложении, а не в БД.

Выполнено:

- `2026-04-15`: подключены зависимости `Flyway` и PostgreSQL driver в `ui`;
- `2026-04-15`: добавлена baseline migration `V1__create_ui_registry.sql`;
- `2026-04-15`: добавлен `UiDatabaseSchemaMigrator`.

Осталось:

- проверить миграцию на реальном PostgreSQL;
- подключить schema migration к реальному `database` rollout-сценарию.

### 11. `DatabaseModuleStore` и lifecycle DB-модуля

Статус:

- частично реализовано

Цель:

- дать UI полноценный CRUD/lifecycle для DB-модулей.

Фичи:

- `DatabaseModuleStore`;
- загрузка каталога DB-модулей;
- загрузка `EditableModule` для DB-модуля;
- сохранение personal `working copy`;
- `Publish`;
- `Discard working copy`;
- отдельное поле `module_origin_kind`;
- сохранение канонического YAML/JSON для revision и working copy;
- actor-aware поведение для multi-user режима.

Выполнено:

- `2026-04-15`: добавлен первый `DatabaseModuleStore` для чтения каталога DB-модулей из `current_revision`;
- `2026-04-15`: добавлен endpoint `GET /api/db/modules/catalog`.
- `2026-04-15`: добавлена загрузка editable DB-модуля:
  - `GET /api/db/modules/{id}`;
  - чтение `current revision`;
  - чтение personal `working copy`, если она есть;
  - чтение SQL assets из `module_revision_sql_asset` / snapshot JSON.
- `2026-04-15`: добавлено сохранение personal `working copy`:
  - `POST /api/db/modules/{id}/save`;
  - upsert в `module_working_copy`;
  - сохранение YAML и SQL assets в snapshot JSON;
  - сохранение `STALE`-статуса при повторном save.
- `2026-04-15`: добавлено удаление personal `working copy`:
  - `POST /api/db/modules/{id}/discard-working-copy`;
  - удаление только draft текущего actor.

Осталось:

- `Publish`;
- runtime snapshot для запуска DB-модуля.

### 12. Создание и удаление DB-модуля

Статус:

- не реализовано

Цель:

- дать возможность жить DB-модулям как самостоятельным сущностям.

Фичи:

- отдельная страница `Новый модуль`;
- создание:
  - `module`
  - первой `revision`
  - personal `working copy`
- baseline `Пустой модуль`;
- `hiddenFromUi = true` по умолчанию;
- отдельная операция `Удалить модуль из БД`;
- hard delete DB-модуля;
- запрет удаления при активном run/import.

### 13. Импорт `files -> database`

Статус:

- не реализовано

Цель:

- загружать файловые модули в DB-реестр через явный import-flow.

Фичи:

- отдельная страница `Импорт моделей в БД`;
- `syncAllFromFiles`;
- `syncOneFromFiles`;
- диагностика `appsRoot`;
- `module_sync_run` и `module_sync_run_item`;
- `content_hash` + precheck по `mtime/size`;
- conflict policy по `moduleCode`:
  - `SKIPPED_CODE_CONFLICT`
  - без перезаписи существующего DB-модуля;
- advisory locks:
  - глобальный lock для full sync
  - per-module lock для single sync;
- maintenance mode для `syncAllFromFiles`;
- multi-user UX для активного import.

### 14. Запуск DB-модулей и история запусков

Статус:

- не реализовано

Цель:

- запускать DB-модули и хранить их историю независимо от file-history.

Фичи:

- запуск DB-модуля по:
  - `working copy`
  - или `current revision`
- обязательный `execution_snapshot`;
- таблицы run-history:
  - `module_run`
  - `module_run_source_result`
  - `module_run_event`
  - `module_run_artifact`
- отдельная страница истории запусков DB-модуля;
- structured summary;
- timeline;
- source results;
- execution snapshot как технический блок.

### 15. Cleanup истории запусков DB-модулей

Статус:

- не реализовано

Цель:

- очищать старую DB-run-history и артефакты контролируемо и безопасно.

Фичи:

- `ModuleRunHistoryCleanupService`;
- общая cleanup-операция по всем DB-модулям;
- default cleanup:
  - старше одного месяца
  - сохранять минимум `30` запусков на модуль;
- опция отключения safeguard;
- удаление run records, orphan snapshots и run artifacts;
- сервисный UI-блок cleanup на странице `Импорт моделей в БД`.

## P2

### 16. Retention output-каталогов

Цель:

- не накапливать старые результаты запусков бесконтрольно.

Что нужно сделать:

- хранить только последние `N` запусков;
- или удалять output старше `X` дней;
- добавить настройки retention в конфиг.

### 17. Расширенный summary по длительностям

Цель:

- улучшить наблюдаемость запуска.

Что нужно сделать:

- длительность по каждому source;
- длительность merge;
- длительность target import;
- фактический timeout и параметры запуска.

### 18. Metadata модулей

Цель:

- упростить работу UI со сценариями.

Что нужно сделать:

- ввести более формальный descriptor модуля;
- хранить:
  - `id`
  - `title`
  - `description`
  - `tags`
- уметь скрывать тестовые/служебные модули из UI.

### 19. Валидация модулей при старте UI

Цель:

- сразу видеть проблемы конфигов и ресурсов.

Что нужно сделать:

- проверять наличие `application.yml`;
- проверять SQL-ресурсы;
- показывать статус валидности модуля в UI.

## P3

### 20. `multi-statement SQL` в SQL-консоли

Статус:

- отложено как отдельный большой этап

Что нужно сделать:

- разбор SQL-скрипта на statement;
- выполнение по каждому source по порядку;
- политика ошибок:
  - stop on first error;
  - continue on error;
- транзакционный режим;
- UI для отображения результата по statement и по source.

### 21. Refactoring крупных классов

Кандидаты:

- `core/src/main/kotlin/com/sbrf/lt/datapool/app/ApplicationRunner.kt`
- `core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleService.kt`
- `ui/src/main/kotlin/com/sbrf/lt/datapool/ui/RunManager.kt`
- `ui/src/main/kotlin/com/sbrf/lt/datapool/ui/Server.kt`

Цель:

- снизить сложность сопровождения;
- выделить более узкие сервисы;
- упростить API между `core` и `ui`.

### 22. Launcher-скрипты и onboarding

Цель:

- упростить вход в проект для команды.

Что нужно сделать:

- launcher для UI;
- короткий quick start;
- инструкция, что делать после добавления нового модуля.

## Рекомендуемый порядок выполнения

1. Визуальный редактор конфигурации модуля с двусторонней синхронизацией.
2. История запусков.
3. Человеко-читаемый просмотр summary в UI.
4. Общая чистка и упрощение UX.
5. Переработка страницы справки.
6. Экспорт результатов SQL-консоли.
7. Guardrails и повторное использование запросов.
8. Retention output и metadata модулей.
9. `multi-statement SQL`.
