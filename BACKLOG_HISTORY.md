# DataPoolLoader Backlog History

Архив закрытых задач, вынесенных из рабочего backlog.

## P0

### 1. DB-редактор: перегруппировка кнопок

Статус:

- реализовано

Ключевой результат:

- кнопки из блока `Администрирование` перенесены в блок `Модули из базы данных`;
- toolbar DB-редактора стал компактнее и логичнее.

Основные даты:

- `2026-04-19`

### 2. DB-sync: выборочная синхронизация из списка файловых модулей

Статус:

- реализовано

Ключевой результат:

- ручной ввод кода модуля убран;
- выборочная синхронизация теперь работает по списку файловых модулей с мультивыбором, поиском и bulk-actions.

Основные даты:

- `2026-04-19`

### 3. Отдельный экран очистки истории запусков для `FILES` и `DB`

Статус:

- реализовано

Ключевой результат:

- cleanup истории запусков вынесен с экрана импорта на отдельный service-screen;
- экран доступен из блока `Нагрузочное тестирование` на главной;
- cleanup работает по текущему режиму:
  - `DB`: очищает run-history и orphan `execution_snapshot`;
  - `FILES`: очищает persisted history файловых запусков;
- перед удалением показывается preview;
- экран импорта снова отвечает только за импорт модулей.

Основные даты:

- `2026-04-19`

### 0.1 DB-редактор: компактный блок `Модули из базы данных`

Статус:

- реализовано

Ключевой результат:

- блок `Модули из базы данных` в левой панели editor уплотнен;
- действия `Новый модуль`, `Удалить модуль` и `Импорт из файлов` переведены в компактные icon-кнопки;
- toolbar стал легче и визуально ближе к panel-actions.

Основные даты:

- `2026-04-19`

### 0.2 Editor: убрать дублирующую кнопку `История и результаты`

Статус:

- реализовано

Ключевой результат:

- на editor-экране оставлена только одна кнопка перехода на `История и результаты`;
- дублирующий action убран из compact-блока `Ход выполнения`;
- поведение выровнено для `FILES` и `DB`.

Основные даты:

- `2026-04-19`

### 3. SQL-консоль: отдельный экран просмотра объектов БД

Статус:

- реализовано

Ключевой результат:

- добавлен отдельный экран `Объекты БД`, связанный с SQL-консолью;
- поиск построен по search-first схеме:
  - без полной начальной загрузки каталога;
  - только по пользовательскому запросу;
- по найденным таблицам, индексам и представлениям показываются базовые метаданные:
  - схема;
  - имя;
  - тип;
  - колонки;
  - связанные индексы или index definition;
- добавлены отдельные route и backend API для metadata-search по выбранным sources.

Основные даты:

- `2026-04-19`

### 16. Retention output-каталогов

Статус:

- реализовано

Ключевой результат:

- добавлен controlled retention output-каталогов для `FILES` и `DB`;
- retention вынесен на отдельный screen `Обслуживание запусков` рядом с cleanup истории;
- есть preview и фактическая очистка;
- политика читается из `UiAppConfig`;
- по умолчанию:
  - `14` дней;
  - минимум `20` запусков на модуль.

Основные даты:

- `2026-04-19`

### 1. Визуальный редактор конфигурации модуля

Статус:

- реализовано

Ключевой результат:

- в UI есть визуальная форма конфигурации модуля с двусторонней синхронизацией `YAML <-> форма` для `FILES` и `DB`;
- состояние секций и экспандеров сохраняется по модулям;
- добавлен tolerant parsing и read-only fallback вместо пустого экрана.

Основные даты:

- `2026-04-15`
- `2026-04-17`

### 2. Улучшение экрана истории запусков

Статус:

- реализовано

Ключевой результат:

- общий экран `История и результаты` для `FILES` и `DB`;
- unified session/history/details contract;
- compact history на editor-экранах и full-анализ на отдельном route;
- limit, поиск, фильтры и live-обновление.

Основные даты:

- `2026-04-15`
- `2026-04-17`

### 3. Нормальный просмотр summary в UI

Статус:

- реализовано

Ключевой результат:

- raw `summary.json` заменен structured summary-блоком для `FILES` и `DB`.

Основные даты:

- `2026-04-15`
- `2026-04-17`

### 4. Улучшение UX главного UI

Статус:

- реализовано

Ключевой результат:

- главная страница и editor shell выровнены по UX;
- переработаны тексты, action bar, статусы и пользовательские формулировки;
- DB editor получил grouped actions и более чистый shell.

Основные даты:

- `2026-04-15`
- `2026-04-16`
- `2026-04-17`

### 5. Переработка страницы справки

Статус:

- реализовано

Ключевой результат:

- пользовательский help center;
- отдельная API-страница;
- сценарии по `FILES`, `DB`, import-flow и `credential.properties`.

Основные даты:

- `2026-04-15`
- `2026-04-17`

## P1

### 15. Kafka: redesign after baseline

Статус:

- реализовано

Ключевой результат:

- baseline Kafka-подсистемы и последующий UI redesign полностью завершены;
- Kafka screen переведен в `kafka-ui`-подобную structural model:
  - cluster-first navigation;
  - отдельные cluster-level screens `Topics / Consumer Groups / Brokers`;
  - topic details page с tab shell `Overview / Messages / Consumers / Settings / Produce`;
- home screen выровнен под итоговый Kafka placement:
  - карточки сгруппированы в три строки;
  - блок `SQL и Kafka` остался компактным внутри своей group-card;
- дальнейшие Kafka-изменения после этой точки считаются уже новым отдельным stream-ом, а не хвостом redesign-волны.

Основные даты:

- `2026-04-24`

### 10. Экран обслуживания запусков: отображение занимаемого объема

Статус:

- реализовано

Ключевой результат:

- экран обслуживания показывает текущий объем истории и output-каталогов в зависимости от выбранного режима UI;
- до cleanup/retention видны текущие объемы, охватываемый период, количество запусков и модулей;
- в preview видно ожидаемое освобождение места;
- добавлен топ самых тяжелых модулей по занимаемому объему для истории и output.

Основные даты:

- `2026-04-19`

### 10.1 Cleanup и укрощение толстых store-слоев: завершенные волны

Статус:

- частично архивировано из рабочего backlog

Ключевой результат:

- store-слои SQL-консоли выведены из статуса top-offender:
  - execution/settings/query/owner flow и object-browser state разрезаны на более узкие support-слои;
  - façade store больше не смешивает heavy orchestration и pure state mutations;
- `module_editor` прошел основную bounded cleanup-волну:
  - draft, SQL-resource, config-form, save, run, lifecycle и storage-aware boundaries уже разведены;
  - remaining workflow/store wiring после closure review выглядит как reviewable façade, а не как giant cluster;
- `module_runs`, `module_sync` и `run_history_cleanup` получили targeted splits по runtime, selection, action и loading-state policy;
- extraction-пакеты закреплены common tests, так что cleanup не остался без regression coverage.

Что это изменило:

- `ui-compose-shared` заметно меньше похож на mini-backend;
- следующие cleanup-волны теперь должны стартовать только от реального top-offender review, а не по инерции.

Основные даты:

- `2026-04-22`
- `2026-04-23`

### 12. Финализация boundary модульного редактора и storage contracts

Статус:

- архивировано из рабочего backlog

Ключевой результат:

- storage-aware `read / save / run` boundaries вынесены из общего workflow слоя;
- database lifecycle и working-copy lifecycle отделены от write-path и explicit wired через отдельные store contracts;
- обычные `save/run` сценарии переведены на unified workflow contract без storage branching в page execution layer;
- closure review подтвердил, что remaining `route.storage` checks относятся уже в основном к UI-capabilities и presentation, а не к knowledge-heavy workflow branching.

Основные даты:

- `2026-04-23`

## P2

### 17. Расширенный summary по длительностям

Статус:

- реализовано

Ключевой результат:

- на экране `История и результаты` показываются:
  - общая длительность запуска;
  - длительность `merge`;
  - длительность `target`;
  - длительность по каждому source;
- в structured summary выведены runtime-параметры запуска:
  - `parallelism`;
  - `fetchSize`;
  - `queryTimeout`;
  - `progressLogEveryRows`;
  - `target enabled`;
- compact summary editor-экрана тоже показывает длительность и ключевые runtime-параметры последнего или активного запуска.

Основные даты:

- `2026-04-19`

### 18. Metadata модулей

Статус:

- реализовано

Ключевой результат:

- metadata читается из `ui-module.yml` и переносится в DB;
- metadata редактируется в UI для `FILES` и `DB`;
- общий server/shared контракт вынесен в отдельный `descriptor`, который используется и в catalog, и в editor/session DTO;
- storage-слой и UI выровнены вокруг единой модели `title / description / tags / hiddenFromUi` без дублирования контрактов.

Основные даты:

- `2026-04-19`

### 19. Валидация модулей при старте UI

Статус:

- реализовано

Ключевой результат:

- единый `ModuleValidationService` в `core` используется и для `FILES`, и для `DB`;
- каталог модулей отдает агрегированную диагностику;
- editor показывает validation-state и пользовательские сообщения по текущему модулю;
- live-валидация теперь покрывает не только синтаксис и missing SQL, но и базовые business-rules конфигурации:
  - обязательный хотя бы один source;
  - непустые `name / jdbcUrl / username / password` у источников;
  - наличие SQL через `commonSql/commonSqlFile` или `source.sql/sqlFile`;
  - положительные `parallelism / fetchSize / queryTimeoutSec / progressLogEveryRows / maxMergedRows`;
  - обязательные поля `target`, если `target.enabled = true`.

Основные даты:

- `2026-04-19`

## P3

### 20. `multi-statement SQL` в SQL-консоли

Статус:

- реализовано

Ключевой результат:

- SQL-консоль умеет разбирать SQL-скрипт на несколько statement-ов;
- statement-ы выполняются последовательно по каждому выбранному shard/source;
- поддержаны обе runtime policy:
  - `STOP_ON_FIRST_ERROR`;
  - `CONTINUE_ON_ERROR`;
- добавлен transaction mode `TRANSACTION_PER_SHARD`:
  - все statement-ы одного shard выполняются в одной JDBC-транзакции;
  - при ошибке выполняется rollback;
  - режим совместим только с `STOP_ON_FIRST_ERROR`;
- результат SQL-консоли расширен до `statementResults`;
- UI умеет:
  - переключаться между statement-ами;
  - показывать данные и статусы по выбранному statement;
  - экспортировать CSV/ZIP для выбранного statement;
  - сохранять и менять execution policy и transaction mode.

Основные даты:

- `2026-04-19`

### 21. Refactoring крупных классов

Статус:

- реализовано

Ключевой результат:

- `Server.kt` разложен на runtime context и route-группы;
- DB-boundary переведены на явные интерфейсы:
  - `DatabaseModuleRegistryOperations`
  - `DatabaseModuleRunOperations`
  - `DatabaseRunExecutionStore / DatabaseRunQueryStore / DatabaseRunMaintenanceStore`
  - `FilesModuleRunOperations / FilesRunHistoryMaintenanceOperations`
  - `SqlConsoleOperations / SqlConsoleAsyncQueryOperations`
- `DatabaseModuleStore`, `DatabaseModuleRunService`, `DatabaseRunStore`, `RunManager`, `SqlConsoleService`, `SqlConsoleQueryManager` и `ApplicationRunner` превращены в тонкие orchestration/facade-слои;
- тяжелая логика вынесена в support-компоненты:
  - lifecycle/start/execution/query/event/cleanup/persistence;
- active-run состояние DB-run слоя больше не хранится в `companion object`, а живет в явном shared registry server runtime;
- рабочий техдолг по крупным целевым классам закрыт без изменения пользовательского контракта.

Основные даты:

- `2026-04-19`

### 6. Экспорт результатов SQL-консоли

Статус:

- реализовано

Ключевой результат:

- экспорт `CSV` по source и `ZIP` по всем source из SQL-консоли.

### 7. Повторное использование SQL-запросов

Статус:

- реализовано

Ключевой результат:

- recent queries и избранные запросы сохраняются и доступны из UI.

### 8. Guardrails для опасных SQL-команд

Статус:

- реализовано

Ключевой результат:

- анализ опасных SQL, confirm flow и режим `Строгая защита`.

### 10. Flyway и схема `ui_registry`

Статус:

- реализовано

Ключевой результат:

- введена схема `ui_registry` и миграции для registry, sync и run-history.

### 11. `DatabaseModuleStore` и lifecycle DB-модуля

Статус:

- реализовано

Ключевой результат:

- каталог DB-модулей, personal working copy, publish и discard.

### 12. Создание и удаление DB-модуля

Статус:

- реализовано

Ключевой результат:

- создание и удаление DB-модуля из UI, включая отдельный create-flow.

### 13. Импорт `files -> database`

Статус:

- реализовано

Ключевой результат:

- import-flow `syncAllFromFiles / syncOneFromFiles`, advisory locks, maintenance mode и история import-run.

### 14. Запуск DB-модулей и история запусков

Статус:

- реализовано

Ключевой результат:

- snapshot-based запуск DB-модулей;
- `execution_snapshot`;
- DB run-history;
- общий экран `История и результаты`.

### 15. Cleanup истории запусков DB-модулей

Статус:

- реализовано

Ключевой результат:

- добавлен `DatabaseRunHistoryCleanupService`;
- cleanup работает по всем DB-модулям с default policy `30 дней + минимум 30 запусков на модуль`;
- есть preview, execute и опция отключения safeguard;
- удаляются старая run-history и orphan `execution_snapshot`;
- позже cleanup вынесен на отдельный общий экран для `FILES` и `DB`.

Основные даты:

- `2026-04-19`

### 0. `ui-server` boundary thinning wave

Статус:

- архивировано из рабочего backlog

Ключевой результат:

- `ui-server` перестал держать route/startup/context/runtime branching в нескольких монолитных файлах;
- `Server.kt`, route groups, page routing, SQL-console routes и DB routes разрезаны на support/facade слои;
- `UiServerContext` и startup/composition root стали заметно тоньше и предсказуемее.

Основные даты:

- `2026-04-20`
- `2026-04-21`
- `2026-04-22`
- `2026-04-23`

### 2026-04-24. Архив completed sections from active backlog

Статус:

- архивировано из рабочего backlog

Ключевой результат:

- рабочий [BACKLOG.md](/Users/kwdev/DataPoolLoader/BACKLOG.md) сокращен до реально активных stream-ов;
- завершенные архитектурные, subsystem и testing-wave секции вынесены из рабочего файла в архив;
- активный backlog больше не смешивает открытые задачи с уже закрытыми implementation packages.

Что было перенесено из рабочего backlog:

- завершенные `P0`-волны:
  - `0. Архитектурная программа: вычистить ui-server как перегруженный boundary`;
  - `1. Архитектурная программа: нормализовать error model`;
  - `2. Архитектурная программа: нормализовать state model`;
  - `3. Архитектурная программа: разрезать giant UI files`;
  - `4. Архитектурная программа: разрезать styles.css`, включая пакеты `4.1–4.5` и closure review;
  - `5. Архитектурная программа: провести cleanup legacy и мусора`, включая пакеты `5.1–5.4` и closure review;
  - `6. SQL-консоль: единая программа развития`;
  - `8. Нормализовать build baseline на Java 21`;
- завершенные `P1`-волны:
  - `10. Cleanup и укрощение толстых store-слоев`, включая `10.1`;
  - completed rule-sync package `11.1`;
  - `12. Финализировать boundary модульного редактора и storage contracts`;
  - `13. SQL-консоль: visual IDE refinement`, включая пакеты `13.0–13.7`;
  - `14. Страница "О проекте": минимизация до карточек разработчиков`;
  - Kafka baseline `15.0–15.8`, включая home regrouping `15.1` и completed width-alignment follow-up;
- завершенные `P2`-волны:
  - `16.1–16.16` visual/server testing packages;
  - `17. Формализовать карту состояния проекта`.

Что осталось активным после архивации:

- `9. Операционная надежность long-running операций`;
- `11. Repo-level архитектурная дисциплина` как живой governance stream;
- `15.1.2` и `15.9.*` как новый Kafka UI redesign stream после baseline;
- `16` как следующий testing follow-up после стабилизации Kafka redesign;
- `20` как отложенный `P3` bucket.

Основные даты:

- `2026-04-24`

### 2. State model normalization wave

Статус:

- архивировано из рабочего backlog

Ключевой результат:

- persisted state SQL-консоли разрезан на `workspace / library / preferences` с legacy migration-only combined state;
- `run-state.json` очищен от uploaded credentials, credentials вынесены в отдельный persisted state;
- state-файлы и file-store helper-ы нормализованы, source-of-truth слои стали явнее.

Основные даты:

- `2026-04-20`
- `2026-04-21`
- `2026-04-22`
- `2026-04-23`

### 3. Giant UI/store file wave

Статус:

- реализовано

Ключевой результат:

- giant page/store files в `ui-compose-web` и `ui-compose-shared` разрезаны до reviewable shell/binding/effects/support состояния;
- введен stop-condition: второй круг распила без нового архитектурного эффекта запрещен;
- SQL console, module editor, module runs, module sync и cleanup screens переведены в более узкие section/store contracts.

Основные даты:

- `2026-04-20`
- `2026-04-21`
- `2026-04-22`
- `2026-04-23`

### 4. `styles.css` split wave

Статус:

- архивировано из рабочего backlog

Ключевой результат:

- giant `styles.css` переведен в ordered import-manifest;
- стили разнесены по logical chunks (`foundation`, `home/help`, `config/editor`, `sql console`, `run history`, `maintenance`, `sql results`);
- каскад сохранен, а CSS-долг перестал концентрироваться в одном файле.

Основные даты:

- `2026-04-20`
- `2026-04-21`

### 5. Legacy/dead code cleanup wave

Статус:

- архивировано из рабочего backlog

Ключевой результат:

- удалены legacy aliases, migration хвосты и thin wrappers, потерявшие архитектурную ценность;
- cleanup затронул persisted state SQL-консоли, run-state, UI config, feature-local support/helpers, runtime fallback duplication и старые adapter-слои;
- рабочий код стал короче, а historical compatibility paths отделены от production-flow.

Основные даты:

- `2026-04-21`
- `2026-04-22`
- `2026-04-23`

### 6. SQL-console baseline program closure

Статус:

- архивировано из рабочего backlog

Ключевой результат:

- SQL-консоль собрана в отдельную подсистему с завершенным baseline-пакетом;
- закрыты safety/recovery, multi-tab workspaces, group-first source selection и execution history per workspace;
- object browser доведен до search-first DBA-like inspector flow;
- главный экран консоли переведен в более IDE-like operational model;
- Monaco получил staged autocomplete, object navigation, statement markers и bounded IDE-like enhancements;
- browser-level smoke harness и разрезанный SQL-console test layer закрепили подсистему как отдельный инженерный поток.

Основные даты:

- `2026-04-22`
- `2026-04-23`

## P3

### 23. Структурирование DTO и `data class`

Статус:

- реализовано

Ключевой результат:

- DTO и модели разнесены по отдельным файлам и тематическим пакетам;
- общий `UiModels.kt` распилен.

### 24. Интерфейсы для основных механизмов ядра

Статус:

- реализовано

Ключевой результат:

- введены core-порты;
- `ApplicationRunner` переведен на snapshot-based контракт;
- concrete PostgreSQL-реализации приведены к явным инфраструктурным именам.

### 22. Launcher-скрипты и onboarding

Статус:

- реализовано

Ключевой результат:

- добавлены launcher-скрипты для `ui-server` и desktop-прототипа;
- README дополнен quick start для UI и рекомендуемым запуском из IDEA.

Основные даты:

- `2026-04-19`
