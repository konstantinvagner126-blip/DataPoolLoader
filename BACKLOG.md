# DataPoolLoader Backlog

## Правила приоритизации

- `P0` — ближайшие задачи, которые дают максимальную пользовательскую ценность и должны идти первыми.
- `P1` — важные задачи следующего этапа после `P0`.
- `P2` — среднесрочное развитие.
- `P3` — долгосрочные улучшения и техдолг.

## P0

### 1. Визуальный редактор конфигурации модуля

Статус:

- частично реализовано

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

Выполнено:

- `2026-04-15`: добавлена вкладка `Форма` рядом с `YAML` на странице файлового модуля;
- `2026-04-15`: добавлены backend endpoint'ы:
  - `POST /api/config-form/parse`;
  - `POST /api/config-form/update`;
- `2026-04-15`: реализована двусторонняя синхронизация `YAML <-> форма` для основных разделов:
  - общие параметры;
  - SQL;
  - `sources`;
  - `quotas`;
  - `target`;
- `2026-04-15`: добавлено сохранение состояния секций и экспандеров по модулям в UI storage.
- `2026-04-17`: визуальная форма подключена и к DB-режиму:
  - вкладка `Настройки модуля` добавлена на страницу DB-модулей;
  - форма синхронизируется с `application.yml` DB-модуля в реальном времени;
  - состояние секций формы сохраняется отдельно для DB-модулей в UI storage.

Осталось:

- сократить количество fallback-сценариев, когда форма уходит в режим "временно недоступна" из-за сложной структуры YAML.

### 2. Улучшение экрана истории запусков

Статус:

- частично реализовано

Что нужно сделать:

- сделать историю запусков более заметной и удобной;
- показать основные атрибуты запуска:
  - модуль;
  - время;
  - статус;
  - merged rows;
  - target status;
- добавить быстрый переход к результатам конкретного запуска.

Выполнено:

- `2026-04-15`: история файловых запусков переведена на список с фильтрами и выбором конкретного запуска;
- `2026-04-15`: для файлового режима добавлены заметные статусы, summary-блок и timeline выполнения;
- `2026-04-17`: для DB-режима добавлен отдельный список запусков модуля с выбором конкретного run и детальным просмотром.
- `2026-04-17`: UX истории выровнен между `FILES` и `DB`:
  - общий layout `История запусков / Текущий запуск`;
  - фильтры `Все / Активные / Успешные / С ошибкой` и для DB-режима;
  - детали DB-запуска разнесены по тем же крупным панелям: summary, event-log, source results, artifacts.

Осталось:

- добавить более явные переходы к output/result artifacts конкретного запуска.

### 3. Нормальный просмотр summary в UI

Статус:

- частично реализовано

Что нужно сделать:

- отображать `summary.json` в виде структурированных блоков, а не raw JSON;
- выделить:
  - merged rows;
  - merge mode;
  - target status;
  - successful sources;
  - failed sources;
  - source allocations.

Выполнено:

- `2026-04-15`: в файловом UI raw `summary.json` заменен на структурированный summary-блок;
- `2026-04-15`: summary показывает:
  - merged rows;
  - merge mode;
  - target status;
  - source allocations;
  - проблемные источники;
- `2026-04-17`: structured summary добавлен и для DB run-history detail-view.

Осталось:

- при необходимости вынести summary в отдельный экран/route, а не только в inline-панели запуска.

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

Выполнено:

- `2026-04-15`: уплотнены экраны summary/history и переработаны статусы выполнения;
- `2026-04-16`: улучшены подсказки и сообщения по `credential.properties`, включая недостающие placeholder-keys;
- `2026-04-17`: главная страница переработана:
  - блок `Загрузка дата пулов`;
  - карточки `Файловый режим` и `DB режим`;
  - slider режима только на главной;
  - route-guard для страниц файлового и DB-режима.
- `2026-04-17`: editor shell для `FILES` и `DB` выровнен:
  - одинаковая верхняя структура редактора;
  - одинаковый порядок основных панелей после редактора;
  - draft/status-подача для DB-редактора;
  - общий `credential.properties` panel и общий `credentials` controller;
  - общие parameterized JS blocks для history/current, summary, event-log и info-panels вместо копипасты `file/db`.

Осталось:

- довести до единого стиля оставшиеся страницы и системные сообщения;
- убрать оставшиеся англоязычные подписи и технические формулировки из пользовательского UI.

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

Выполнено:

- `2026-04-15`: справка переписана в виде пользовательского help center;
- `2026-04-15`: добавлены отдельные крупные блоки:
  - модуль загрузки данных;
  - SQL-консоль;
  - `credential.properties`;
- `2026-04-15`: добавлены стартовые сценарии, пример `application.yml`, список defaults и частые ошибки.

Осталось:

- отдельно описать DB-режим:
  - import `files -> database`;
  - working copy / publish;
  - запуск DB-модуля и чтение DB-history.

## P1

### 6. Экспорт результатов SQL-консоли

Статус:

- реализовано

Цель:

- дать возможность сохранять результаты ручных `SELECT`-запросов.

Что нужно сделать:

- экспорт CSV по активному source;
- экспорт ZIP со всеми source-результатами;
- понятные кнопки скачивания в UI.

Выполнено:

- `2026-04-15`: добавлены backend endpoint'ы:
  - `POST /api/sql-console/export/source-csv`;
  - `POST /api/sql-console/export/all-zip`;
- `2026-04-15`: на странице SQL-консоли добавлены кнопки:
  - `Скачать CSV`;
  - `Скачать ZIP`.

### 7. Повторное использование SQL-запросов

Статус:

- частично реализовано

Цель:

- ускорить ручную работу в SQL-консоли.

Что нужно сделать:

- сохранить последние запросы;
- дать быстрый выбор ранее выполнявшихся запросов;
- предусмотреть базовый набор “избранных” SQL.

Выполнено:

- `2026-04-15`: SQL-консоль сохраняет `recentQueries` в отдельный state file;
- `2026-04-15`: добавлены UI-элементы:
  - выбор последнего запроса;
  - кнопка `Подставить`;
  - кнопка очистки истории.

Осталось:

- добавить отдельный список избранных SQL, независимый от истории последних запросов.

### 8. Guardrails для опасных SQL-команд

Статус:

- частично реализовано

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

Выполнено:

- `2026-04-15`: SQL-консоль анализирует тип команды и подсвечивает опасные SQL;
- `2026-04-15`: добавлены confirm-диалоги для потенциально деструктивных statement;
- `2026-04-15`: для опасных команд используется усиленный warning-state в UI.

Осталось:

- расширить список guardrails и критерии опасности;
- при необходимости добавить отдельный режим "строгой защиты".

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
- `2026-04-16`: добавлено live-состояние import-flow для DB-страниц:
  - endpoint `GET /api/db/sync/state`;
  - backend maintenance mode при `syncAllFromFiles`;
  - блокировка DB module endpoints на время массового импорта;
  - отображение active import на страницах `db-sync` и `db-modules`.
- `2026-04-17`: доведен UI runtime-mode control:
  - slider `FILES / DB` на главной странице;
  - `POST /api/ui/runtime-mode` без перезапуска UI;
  - блокировка недоступных карточек режима;
  - route-guard для `/modules`, `/db-modules`, `/db-sync`;
  - fallback в `FILES` с понятным сообщением пользователю.

Осталось:

- при необходимости расширить live DB-context на остальные экраны.

### 10. Flyway и схема `ui_registry`

Статус:

- реализовано

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
- `2026-04-15`: schema migration подключена в runtime resolution DB-режима через `UiRuntimeContextService`;
- `2026-04-16`: миграции и runtime rollout проверены на реальном PostgreSQL.

### 11. `DatabaseModuleStore` и lifecycle DB-модуля

Статус:

- реализовано

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
- `2026-04-15`: добавлен `Publish` personal `working copy`:
  - `POST /api/db/modules/{id}/publish`;
  - создание новой общей revision;
  - переключение `module.current_revision_id`;
  - удаление опубликованной working copy;
  - защита от публикации устаревшей working copy по `base_revision_id`.

### 12. Создание и удаление DB-модуля

Статус:

- частично реализовано

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

Выполнено:

- `2026-04-15`: добавлено создание DB-модуля:
  - `POST /api/db/modules`;
  - создание `module`;
  - создание первой `revision`;
  - создание personal `working copy`;
  - `module_origin_kind = CREATED_IN_UI`.
- `2026-04-15`: добавлено удаление DB-модуля:
  - `DELETE /api/db/modules/{id}`;
  - hard delete через FK cascade;
  - запрет удаления при активном `module_run`.
- `2026-04-15`: добавлены базовые UI-кнопки создания и удаления на странице DB-модулей.
- `2026-04-16`: удаление DB-модуля также блокируется при активном import/sync по модулю.

Осталось:

- заменить prompt-based создание на полноценную страницу/форму `Новый модуль`;
- довести baseline `Пустой модуль` до валидного стартового шаблона;

### 13. Импорт `files -> database`

Статус:

- частично реализовано

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

Выполнено:

- `2026-04-15`: добавлена страница `/db-sync` для импорта файловых модулей;
- `2026-04-15`: добавлены endpoint'ы:
  - `POST /api/db/sync/all`;
  - `POST /api/db/sync/one`.
- `2026-04-15`: добавлен `ModuleSyncService`:
  - чтение `application.yml`;
  - чтение UI-метаданных из `ui-module.yml`;
  - запись `module_sync_run` и `module_sync_run_item`;
  - `content_hash` по `application.yml` и SQL assets.
- `2026-04-15`: добавлен перенос SQL assets при импорте:
  - `commonSqlFile`;
  - `source.sqlFile`;
  - `classpath:` резолвится в `src/main/resources`;
  - SQL assets попадают в initial revision и personal working copy.
- `2026-04-15`: добавлено заполнение нормализованной revision-модели:
  - runtime-поля `module_revision` берутся из `application.yml`;
  - `module_revision_source` заполняется по `app.sources`;
  - `module_revision_target` заполняется по `app.target`;
  - `module_revision_quota` заполняется по `app.quotas`;
  - поддержаны `commonSql`, `commonSqlFile`, `source.sql`, `source.sqlFile`.
- `2026-04-15`: зафиксирована conflict policy:
  - существующий DB-модуль с тем же `moduleCode` не перезаписывается;
  - для отличающегося файлового модуля возвращается `SKIPPED_CODE_CONFLICT`.
- `2026-04-15`: добавлены PostgreSQL advisory locks для import-flow:
  - `syncAllFromFiles` берет exclusive global lock;
  - `syncOneFromFiles` берет shared global lock и exclusive per-module lock;
  - разные single-import по разным модулям могут идти параллельно;
  - full-import несовместим с любым single-import;
  - повторный single-import того же модуля отклоняется с понятным сообщением.
- `2026-04-16`: добавлен maintenance mode для full sync:
  - `syncAllFromFiles` создает `RUNNING` запись в `module_sync_run`;
  - состояние доступно через `GET /api/db/sync/state`;
  - страницы DB-модулей и импорта показывают активный full sync и отключают действия;
  - backend блокирует DB module operations, пока выполняется массовый импорт.
- `2026-04-16`: добавлен `mtime/size` precheck перед пересчетом `content_hash`:
  - быстрый filesystem fingerprint хранится в `module_sync_run_item.details`;
  - при совпадении fingerprint сервис пропускает модуль без пересчета канонического hash;
  - precheck не применяется поверх предыдущего `FAILED` результата.
- `2026-04-16`: доведен multi-user UX для active single-module import:
  - `GET /api/db/sync/state` отдает список активных `single sync`;
  - страница DB-модулей помечает импортируемые модули и блокирует запись только для выбранного занятого модуля;
  - страница импорта показывает активные `single sync` и не дает повторно импортировать тот же модуль;
  - backend блокирует `save/discard/publish/delete` только для модуля, который сейчас импортируется.

Осталось:

- при необходимости расширить отображение истории/деталей import-run на UI.

### 14. Запуск DB-модулей и история запусков

Статус:

- частично реализовано

Цель:

- запускать DB-модули и хранить их историю независимо от file-history.

Фичи:

- snapshot-based запуск, общий для `files` и `database`;
- core runner, работающий от runtime snapshot, а не только от `configPath`;
- интерфейсы/порты ядра для основных механизмов выполнения;
- отдельные реализации источника запуска:
  - `FilesModuleExecutionSource`
  - `DatabaseModuleExecutionSource`
- запуск DB-модуля по:
  - `working copy`
  - или `current revision`
- обязательный `execution_snapshot`;
- таблицы run-history:
  - `module_run`
  - `module_run_source_result`
  - `module_run_event`
  - `module_run_artifact`
- детальный просмотр истории запусков DB-модуля;
- structured summary;
- timeline;
- source results;
- execution snapshot как технический блок.

Архитектурная подзадача для этого этапа:

- перестать привязывать запуск только к файловому `configPath`;
- ввести единый runtime-контракт запуска, чтобы раннер не знал, пришла конфигурация из `apps` или из PostgreSQL;
- отделить orchestration-логику выполнения от concrete PostgreSQL/JDBC-реализаций.

Предлагаемый runtime-контракт:

- `ModuleExecutionSource`:
  - готовит immutable runtime snapshot для запуска;
  - в `files`-режиме читает snapshot из файлового модуля;
  - в `database`-режиме собирает snapshot из `working copy` или `current revision` и сохраняет `execution_snapshot` в БД.
- `RuntimeModuleSnapshot`:
  - `moduleCode`
  - `moduleTitle`
  - `configYaml`
  - `sqlFiles`
  - `appConfig`
  - `launchSourceKind`
  - `executionSnapshotId` для DB-режима или `null` для файлового режима
  - дополнительные runtime-метаданные, нужные для истории запусков.

Какие порты нужны в `core`:

- `SourceExporter`
- `ResultMerger`
- `TargetSchemaValidator`
- `TargetImporter`

Что должно измениться в раннере:

- `ApplicationRunner` должен уметь работать от `RuntimeModuleSnapshot`, а не только от `Path`;
- старый сценарий запуска по `configPath` можно сохранить как adapter для CLI и обратной совместимости;
- `ApplicationRunner` не должен знать, получен snapshot из файлов или из PostgreSQL.

Порядок реализации внутри этого этапа:

1. Ввести порты `core` для выполнения.
2. Перевести `ApplicationRunner` на snapshot-based контракт.
3. Добавить `FilesModuleExecutionSource` как адаптер текущего файлового запуска.
4. Добавить `DatabaseModuleExecutionSource` с созданием `execution_snapshot`.
5. Поверх этого реализовать запуск DB-модулей и сохранение DB run-history.

Выполнено:

- `2026-04-16`: в `core` введены порты:
  - `SourceExporter`
  - `ResultMerger`
  - `TargetSchemaValidator`
  - `TargetImporter`
- `2026-04-16`: `ApplicationRunner` переведен на работу от `RuntimeModuleSnapshot`;
  - старый `run(configPath)` сохранен как adapter для CLI и текущего file-flow;
  - concrete PostgreSQL-реализации подключаются через порты.
- `2026-04-16`: добавлен `FilesModuleExecutionSource`;
  - `RunManager` больше не запускает file-модули напрямую через `configPath`;
  - file-flow готовит `RuntimeModuleSnapshot` и передает его в раннер.
- `2026-04-17`: добавлен `DatabaseModuleExecutionSource`;
  - DB-модуль можно подготовить к запуску из `CURRENT_REVISION` или `WORKING_COPY`;
  - перед запуском сохраняется `execution_snapshot`;
  - runtime snapshot для DB-режима собирается независимо от `apps`.
- `2026-04-17`: добавлен `DatabaseModuleRunService` и базовая запись DB run-history:
  - запуск DB-модуля доступен через `POST /api/db/modules/{id}/run`;
  - список последних запусков доступен через `GET /api/db/modules/{id}/runs`;
  - пишутся `module_run`, `module_run_source_result`, `module_run_event`, `module_run_artifact`;
  - source/merge/target/run события и артефакты сохраняются в registry;
  - на странице DB-модуля добавлена кнопка запуска и базовый блок последних запусков.
- `2026-04-17`: добавлен detail-view выбранного DB-запуска:
  - endpoint `GET /api/db/modules/{id}/runs/{runId}`;
  - UI показывает structured summary, source results, timeline событий и артефакты;
  - в детальном блоке доступны `runId`, `executionSnapshotId`, target status и raw `summary.json`.

Осталось:

- при необходимости вынести историю DB-запусков в отдельную страницу вместо текущей детальной панели на странице модуля;
- расширить фильтрацию и навигацию по длинной истории запусков.

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

Статус:

- частично реализовано

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

Что уже сделано:

- `2026-04-15`: для файловых модулей добавлено чтение `ui-module.yml`:
  - `title`;
  - `description`;
  - `tags`;
  - `hiddenFromUi`;
- `2026-04-15`: metadata файловых модулей выводится в каталог и детали модуля;
- `2026-04-15`: metadata переносится в DB-режим при import `files -> database`;
- `2026-04-15`: DB-модули хранят и отдают `description`, `tags`, `hiddenFromUi`.

Осталось:

- формализовать descriptor как отдельный устойчивый контракт;
- добавить удобное редактирование metadata в UI без ручного YAML/JSON.

### 19. Валидация модулей при старте UI

Статус:

- частично реализовано

Цель:

- сразу видеть проблемы конфигов и ресурсов.

Что нужно сделать:

- проверять наличие `application.yml`;
- проверять SQL-ресурсы;
- показывать статус валидности модуля в UI.

Что уже сделано:

- `2026-04-15`: `ModuleRegistry` проверяет:
  - наличие `application.yml`;
  - корректность YAML;
  - наличие SQL-ресурсов;
  - дубли имен `sources`;
- `2026-04-15`: в UI появились:
  - `validationStatus`;
  - `validationIssues`;
  - предупреждения и ошибки по модулю в каталоге и деталях.

Осталось:

- добавить агрегированную диагностику на уровне старта UI;
- выровнять правила валидации между файловым и DB-режимом.

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
- `ui/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunManager.kt`
- `ui/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt`
- `ui/src/main/kotlin/com/sbrf/lt/platform/ui/module/DatabaseModuleStore.kt`

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

### 23. Структурирование DTO и `data class`

Статус:

- частично реализовано

Цель:

- убрать хаотичное размещение DTO/model-классов внутри сервисов, controllers и больших файлов;
- сделать модель данных читаемой, предсказуемой и удобной для сопровождения.

Правило:

- каждый `data class` должен лежать в отдельном `.kt`-файле;
- классы должны быть сгруппированы по осмысленным пакетам, а не по случайному месту использования;
- DTO/request/response/projection/result-классы не должны находиться внутри service/controller/store-классов или внизу крупных implementation-файлов;
- у каждого `data class` должен быть русский KDoc-комментарий с кратким описанием:
  - что это за модель;
  - где используется;
  - что означает ключевая бизнес-сущность, если она не очевидна.

Предварительная группировка пакетов:

- `com.sbrf.lt.platform.ui.model.module` — модели файловых UI-модулей;
- `com.sbrf.lt.platform.ui.model.db` — модели DB-модулей и registry;
- `com.sbrf.lt.platform.ui.model.sync` — модели импорта `files -> database`;
- `com.sbrf.lt.platform.ui.model.run` — модели запусков и истории;
- `com.sbrf.lt.platform.ui.model.sqlconsole` — модели SQL-консоли;
- `com.sbrf.lt.platform.ui.model.config` — модели UI-конфига и runtime context.

Что нужно сделать:

- провести инвентаризацию всех `data class` в `ui` и `core`;
- вынести `data class` из:
  - `DatabaseModuleStore`;
  - `ModuleSyncService`;
  - `Server`;
  - крупных service/store файлов;
- разнести текущий большой `UiModels.kt` по тематическим файлам;
- добавить русские KDoc-комментарии;
- обновить импорты и тесты;
- зафиксировать правило в документации проекта, чтобы новые DTO не добавлялись обратно в implementation-файлы.

Что уже сделано:

- `2026-04-15`: `data class` вынесены из `DatabaseModuleStore` в отдельные файлы;
- `2026-04-16`: `data class` вынесены из `ModuleRegistry`, `ModuleSyncService`, `RunManager`, `RunStateStore`, `SqlConsoleQueryManager`, `SqlConsoleStateStore`.
- `2026-04-17`: вынесены модели DB run-history detail-view и связанные response-классы в отдельные `.kt`-файлы.

Осталось:

- разнести текущий большой `UiModels.kt` по тематическим пакетам;
- довести группировку DTO до целевого package layout из этого backlog-пункта.

### 24. Интерфейсы для основных механизмов ядра

Статус:

- частично реализовано

Цель:

- уменьшить связность `core`;
- сделать orchestration-слой зависимым от контрактов, а не от concrete JDBC/CSV/PostgreSQL-реализаций;
- упростить unit-тестирование без `open class`, частичных моков и технических обходов;
- подготовить архитектуру к появлению альтернативных реализаций, не ломая сценарий текущего запуска;
- подготовить ядро к запуску UI-модулей как из файлов, так и из PostgreSQL через единый snapshot-based контракт.

Принцип:

- не вводить интерфейс на каждый класс подряд;
- вводить интерфейсы только на устойчивых архитектурных границах;
- pure-utility классы, простые value/model классы и локальные helper-компоненты не оборачивать в интерфейсы без явной пользы;
- `ApplicationRunner` и другие orchestrator-компоненты должны зависеть от портов;
- конкретные реализации должны подключаться на уровне composition root.

Где интерфейсы действительно нужны:

- `SourceExporter`:
  - контракт выгрузки одного источника;
  - текущая реализация: `PostgresSourceExporter` на базе JDBC.
- `ResultMerger` или набор merge-стратегий:
  - контракт объединения результатов;
  - либо один `ResultMerger`, либо отдельные стратегии по `mergeMode`.
- `TargetImporter`:
  - контракт загрузки merged-результата в target;
  - текущая реализация: `PostgresTargetImporter`.
- `TargetSchemaValidator`:
  - контракт проверки структуры target-таблицы перед импортом.
- `CredentialsResolver` / `PlaceholderResolver`:
  - обсуждаемый кандидат;
  - вводить только если действительно нужно отделить runtime-resolution от файловой инфраструктуры.

Где интерфейсы пока не нужны:

- DTO, `data class`, response/request модели;
- `ConfigLoader`, если он остается тонкой оберткой над Jackson/YAML;
- мелкие stateless helper-компоненты;
- utility/object классы, у которых нет вариативных реализаций и которые не участвуют в ключевой orchestration-цепочке.

Предварительный целевой контур:

- `ApplicationRunner` знает только про порты:
  - `SourceExporter`
  - `ResultMerger`
  - `TargetSchemaValidator`
  - `TargetImporter`
- источник runtime-конфигурации остается вне `ApplicationRunner` и передает ему уже подготовленный immutable snapshot;
- default wiring собирает текущие реализации PostgreSQL/CSV;
- тесты `ApplicationRunner` и смежных orchestrator'ов используют простые fake/stub-реализации интерфейсов.

Что нужно сделать:

- провести инвентаризацию core-компонентов и определить реальные архитектурные границы;
- спроектировать минимальный набор портов без дублирования ответственности;
- спроектировать snapshot-based контракт запуска для `FILES/DB`;
- переименовать concrete-реализации так, чтобы из имени было видно инфраструктурную специфику:
  - `PostgresExporter` -> `PostgresSourceExporter`;
  - `PostgresImporter` -> `PostgresTargetImporter`;
- перевести `ApplicationRunner` на зависимость от интерфейсов;
- сделать это подзадачей этапа `Запуск DB-модулей и история запусков`, а не отдельным изолированным рефакторингом;
- при необходимости выделить merge-стратегии по режимам;
- обновить тесты;
- зафиксировать правило в документации:
  - интерфейсы вводятся по архитектурной границе, а не "по привычке".

Что уже сделано:

- `2026-04-16`: добавлены порты `SourceExporter`, `ResultMerger`, `TargetSchemaValidator`, `TargetImporter`;
- `2026-04-16`: `ApplicationRunner` переведен на зависимости от портов;
- `2026-04-16`: добавлен базовый `RuntimeModuleSnapshot` как единый runtime-контракт ядра.
- `2026-04-16`: file-based подготовка запуска вынесена в `FilesModuleExecutionSource`.
- `2026-04-17`: DB-запуск переведен на тот же snapshot-based контракт через `DatabaseModuleExecutionSource` и `DatabaseModuleRunService`.

Что считаем неправильным:

- делать интерфейс для каждого класса без бизнес-причины;
- плодить пары `Xxx` / `DefaultXxx`, если альтернативных реализаций и тестовой ценности нет;
- скрывать в интерфейсах чисто внутренние алгоритмические детали;
- смешивать в одном интерфейсе orchestration и инфраструктурные детали JDBC/PostgreSQL.

## Рекомендуемый порядок выполнения

1. Выровнять визуальный каркас `FILES/DB` editor UX и свести различия к mode-specific действиям.
2. Закрыть remaining gaps визуального редактора конфига после выравнивания экранов.
3. Дочистить историю запусков и summary UX в файловом и DB-режимах.
4. Реализовать cleanup DB run-history.
5. Добить справку и общий UX до консистентного пользовательского уровня.
6. Завершить SQL-консоль:
   - избранные запросы;
   - расширенные guardrails.
7. Metadata и валидация модулей как единый слой.
8. Retention output-каталогов.
9. Структурирование DTO и `data class`.
10. Дальнейший refactoring крупных классов и стабилизация внутренних API.
11. Launcher-скрипты и onboarding.
12. `multi-statement SQL`.
