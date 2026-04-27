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

### 18. SQL-консоль: completed modernization follow-ups

Статус:

- частично архивировано из рабочего backlog

Архивные переносы:

- `2026-04-27`: packages `18.8–18.9`, `18.11`.

#### 18.8. SQL source catalog settings screen

Статус:

- выполнено

Ключевой результат:

- добавлен отдельный экран `/sql-console-sources` для редактирования SQL sources, groups и `ui.defaultCredentialsFile`;
- source of truth остался в `ui-application.yml`, сохранение идет через server-side config persistence boundary, а runtime SQL catalog перечитывается без рестарта приложения;
- загрузка `credential.properties` перенесена из рабочего SQL workspace на экран настроек sources;
- добавлены диагностика placeholder-ключей, проверка одного source и проверка всех sources без раскрытия secret values;
- raw password values не возвращаются в browser response;
- режим ручного пароля реализован через `System keychain` / placeholder contract, без plain-text UI/storage mode;
- fields формы меняются по выбранному credential mode, а технические `${secret:...}` details скрыты в collapsed block;
- добавлены targeted tests на persistence, hidden password, hot reload, placeholder diagnostics, secret resolution и очистку group references при удалении source.

#### 18.9. Restore targeted visual coverage after SQL redesign stabilizes

Статус:

- выполнено

Ключевой результат:

- добавлен SQL-only Playwright visual suite `tools/sql-console-browser-smoke/tests/sql-console-visual.targeted.spec.mjs`;
- зафиксированы targeted baselines для main SQL shell, result pane empty/combined/per-source/diff, DB objects inspector, source settings и execution history;
- API в suite замоканы, чтобы snapshots не зависели от локальной БД и runtime данных;
- добавлено ожидание внешних visual styles перед screenshot;
- добавлен npm shortcut `npm --prefix tools/sql-console-browser-smoke run test:sql-visual`;
- проверка: `SQL_CONSOLE_SMOKE_SKIP_DB_SETUP=1 PLAYWRIGHT_TEST_ARGS='tests/sql-console-visual.targeted.spec.mjs' ./scripts/run-sql-console-browser-smoke.sh` — `8 passed`.

#### 18.11. SQL source settings connection status normalization

Статус:

- выполнено

Контекст:

- при выборе `credential.properties` и проверке подключения одного source UI может показать противоречивое сообщение:
  - `Не удалось подключиться к source 'db1'. Подключение установлено.`;
- причина: source-settings test flow считает успешным только статус `OK`;
- core/JDBC checker для SQL-консоли возвращает successful status как `SUCCESS` и message `Подключение установлено.`;
- основной SQL connection status layer уже считает `SUCCESS` и `OK` успешными, а source-settings flow должен быть с ним согласован.

Что нужно сделать:

1. нормализовать success detection в single-source test connection:
   - `SUCCESS` и `OK` считаются успешным подключением;
   - `FAILED` и `ERROR` считаются ошибкой;
2. нормализовать aggregate `Проверить все sources`:
   - `SUCCESS` должен попадать в successful count;
   - summary не должен показывать `0 OK` при успешном `SUCCESS`;
3. выровнять визуальный статус на странице source settings, чтобы `SUCCESS` не подсвечивался как failed;
4. добавить targeted regression tests на source-settings service;
5. не менять core/JDBC status contract и не менять формат connection check DTO.

Что сделано:

1. single-source test connection теперь считает `SUCCESS` и `OK` успешными статусами;
2. aggregate `Проверить все sources` считает `SUCCESS` в successful count и не показывает `0 OK` при успешном подключении;
3. страница source settings подсвечивает `SUCCESS` как успешный статус, а не как failed;
4. добавлены targeted regressions в `UiSqlConsoleSourceSettingsServiceTest`:
   - single-source `SUCCESS` после `credential.properties` resolution;
   - aggregate `SUCCESS` summary;
5. core/JDBC status contract и DTO формат не менялись.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.UiSqlConsoleSourceSettingsServiceTest'` — `BUILD SUCCESSFUL`.

### 11. Repo-level архитектурная дисциплина: rule-sync packages `11.1–11.3`

Статус:

- частично архивировано из рабочего backlog

Архивные переносы:

- `2026-04-24`: package `11.1`;
- `2026-04-27`: packages `11.2–11.3`.

#### 11.2. SQL terminal control-path architecture invariant

Статус:

- выполнено

Контекст:

- пакеты `9.9–9.17` закрепили terminal behavior для SQL async/manual transaction execution;
- это уже не локальная тестовая деталь, а архитектурный invariant SQL control-path;
- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md) должен явно запрещать successful no-op для stale terminal control actions.

Что нужно сделать:

1. добавить architecture review note после reliability wave;
2. зафиксировать, что terminal snapshots не должны публиковать active control-path metadata;
3. зафиксировать, что stale terminal actions должны отвечать conflict, а не successful no-op;
4. сохранить distinction между active `RUNNING/PENDING_COMMIT` и terminal states.

Что сделано:

1. в [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md) добавлен review note `Review After SQL Terminal Control-Path Wave`;
2. зафиксировано, что terminal snapshots не публикуют `ownerToken`, `ownerLeaseExpiresAt`, `pendingCommitExpiresAt`;
3. зафиксировано, что stale `release`, `heartbeat`, `cancel`, повторные `commit/rollback` должны быть state conflict;
4. distinction между active `RUNNING/PENDING_COMMIT` и terminal observable history сохранен.

Проверка:

- `git diff --check` — без замечаний.

#### 11.3. SQL failure-scenarios terminal control-path sync

Статус:

- выполнено

Контекст:

- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md) описывает owner token, heartbeat, rollback и safety model;
- после `9.9–9.17` нужно явно описать terminal control-path cleanup и stale action behavior;
- это защищает будущие UI/store правки от возврата terminal owner metadata.

Что нужно сделать:

1. добавить в failure-scenarios doc terminal control-path invariant;
2. описать expected behavior для stale `/heartbeat`, `/cancel`, `/release`, `/commit`, `/rollback`;
3. зафиксировать, что active control-path metadata допустима только в active states;
4. не менять runtime contracts.

Что сделано:

1. в [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md) добавлен раздел `Terminal control-path invariant`;
2. описано expected behavior для stale `/heartbeat`, `/cancel`, `/release`, повторных `/commit` и `/rollback`;
3. зафиксировано, что owner token и lease metadata относятся только к active `RUNNING/PENDING_COMMIT`;
4. runtime contracts не менялись.

Проверка:

- `git diff --check` — без замечаний.

### 19. Kafka UI modernization: completed implementation packages `19.6–19.8`

Статус:

- частично архивировано из рабочего backlog

Архивные переносы:

- `2026-04-27`: packages `19.6–19.8`.

#### 19.6. Kafka produce and create-topic tool forms

Статус:

- выполнено

Ключевой результат:

- produce form переведена на Kafka UI-like tool panel с partition override, key input, structured headers, высоким payload editor и delivery result summary;
- readOnly cluster продолжает блокировать produce UI до действия отправки;
- create-topic form выровнена как compact tool form с topic name, partitions, replication factor, cleanup policy, retention.ms и retention.bytes;
- failure cases и Kafka admin/produce contracts не менялись;
- delete topic, alter topic и reset offsets не добавлялись.

#### 19.6.1. Kafka message expansion and full-width modernization shells

Статус:

- выполнено

Ключевой результат:

- message browser хранит UI-only set раскрытых сообщений, поэтому можно раскрыть несколько строк одновременно;
- повторное нажатие сворачивает конкретную раскрытую строку;
- inline inspector сохранен внутри таблицы;
- expansion state сбрасывается только при смене result set;
- Kafka и Home modernization shell переведены на full-width без `max-width: 1440px`;
- produce payload editor увеличен до 620px на desktop и 420px на узких экранах;
- Kafka read API и bounded read semantics не менялись.

#### 19.6.2. Kafka produce JSON highlighting

Статус:

- выполнено

Ключевой результат:

- produce payload переведен на один Monaco JSON editor без отдельного preview;
- подсветка JSON отображается внутри editor;
- invalid JSON и plain text остаются редактируемыми в том же editor;
- добавлена явная кнопка `Форматировать JSON`;
- pretty-format применяется только по нажатию и только для валидного JSON;
- produce API и write safety не менялись.

#### 19.7. Kafka consumer groups and brokers screens consistency

Статус:

- выполнено

Ключевой результат:

- Consumer Groups screen переведен на Kafka metadata panel с compact header, reload action, summary metrics, dense table, status badges и inline detail view по group topics;
- Brokers screen переведен на Kafka metadata panel с compact header, reload action, cluster summary metrics, broker cards, dense broker table и controller/follower role badges;
- reload actions сохранены;
- partial metadata warnings остаются section/group-level UI state;
- Kafka API и store contracts не менялись.

#### 19.8. Kafka settings UI redesign

Статус:

- выполнено

Ключевой результат:

- SSL settings UI разбит на компактные секции `Trust material` и `Client material`;
- для каждой секции доступны только material formats `Не задано`, `JKS / PKCS12`, `PEM files`;
- показываются только поля выбранного формата;
- material file fields стали read-only display, path задается только через системный file chooser;
- добавлена явная кнопка `Очистить`;
- store нормализует hidden stale SSL fields перед save/test connection;
- `default` как SSL material mode не отображается.

## P2

### 16.17. Kafka targeted visual coverage after modernization

Статус:

- реализовано

Контекст:

- Kafka redesign по `19.2–19.8` завершен функционально;
- старый широкий `ui-visual.smoke.spec.mjs` смешивает Home, SQL, module и Kafka baselines и не должен становиться блокером после каждой локальной правки;
- нужен отдельный Kafka-only visual suite, который защищает именно согласованные Kafka contracts.

Что нужно сделать:

1. добавить отдельный targeted Playwright visual suite для Kafka;
2. покрыть baselines:
   - topics catalog;
   - topic overview;
   - messages browser с inline expanded rows;
   - produce form с JSON editor;
   - create-topic form;
   - consumer groups;
   - brokers;
   - settings screen;
   - empty cluster catalog state;
3. замокать Kafka API responses, чтобы suite не зависел от локального Docker/Kafka runtime;
4. сохранить safety checks из [KAFKA_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/KAFKA_FAILURE_SCENARIOS.md):
   - message read только по явной кнопке;
   - last successful result не исчезает при изменении draft controls;
   - JSON highlighting не ломает plain/invalid payload;
   - readOnly/write state не маскируется;
5. добавить отдельный npm shortcut для запуска только Kafka visual suite.

Что сделано:

1. добавлен targeted Playwright visual suite [kafka-visual.targeted.spec.mjs](/Users/kwdev/DataPoolLoader/tools/sql-console-browser-smoke/tests/kafka-visual.targeted.spec.mjs);
2. зафиксированы 9 Kafka-only visual baselines:
   - topics catalog;
   - create-topic form;
   - topic overview;
   - messages browser с двумя одновременно раскрытыми inline rows;
   - produce form с Monaco JSON editor;
   - consumer groups;
   - brokers;
   - cluster settings;
   - empty cluster catalog;
3. Kafka API responses мокируются внутри suite, поэтому visual gate не зависит от Docker/Kafka runtime;
4. suite проверяет ключевые safety/UX contracts:
   - чтение сообщений происходит только после `Читать сообщения`;
   - смена draft `mode` после чтения не очищает last successful result;
   - одновременно раскрываются несколько сообщений;
   - mixed JSON/plain/invalid payload не ломает rendering;
   - readOnly/write states видимы в settings;
5. добавлен npm shortcut `test:kafka-visual`;
6. visual screenshots стабилизированы через explicit target frame и ожидание styled controls/table cells, чтобы baseline не зависел от скорости загрузки CSS.

Проверка:

- `SQL_CONSOLE_SMOKE_SKIP_DB_SETUP=1 PLAYWRIGHT_TEST_ARGS='tests/kafka-visual.targeted.spec.mjs' ./scripts/run-sql-console-browser-smoke.sh` — `9 passed`.

Основные даты:

- `2026-04-25`

### 16.18. Test strategy backlog sync after Kafka visual baselines

Статус:

- выполнено

Контекст:

- Kafka visual follow-up уже закрыт и перенесен в history как `16.17`;
- активная секция `16` не должна продолжать указывать завершенный Kafka visual baseline как следующий follow-up;
- следующий test focus должен оставаться на long-running scenario coverage и targeted tests для новых bounded changes.

Что нужно сделать:

1. убрать устаревший Kafka visual baseline follow-up из активной секции `16`;
2. оставить актуальные test strategy directions:
   - long-running scenario-level coverage;
   - targeted store/server tests для новых bounded subsystem changes;
3. не включать широкий Playwright suite обратно как обязательный gate до стабилизации UI.

Что сделано:

1. из активной секции `16` убран уже закрытый follow-up про Kafka visual baselines;
2. актуальные направления оставлены:
   - long-running scenario-level coverage;
   - targeted store/server tests для новых bounded subsystem changes;
3. широкий Playwright suite не возвращался как обязательный gate.

Проверка:

- `git diff --check` — без замечаний.

Основные даты:

- `2026-04-27`

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

### 2026-04-24. Архив реализованных задач active backlog: Kafka follow-up и recovery regressions

Статус:

- архивировано из рабочего backlog

Ключевой результат:

- активный [BACKLOG.md](/Users/kwdev/DataPoolLoader/BACKLOG.md) снова содержит только незавершенные stream-ы;
- completed Kafka follow-up packages `17.1–17.13` вынесены из активного backlog;
- completed long-running reliability packages `9.1–9.5` вынесены из активного backlog;
- незакрытый SQL-console modernization stream `18` остался первым активным приоритетом.

Что было перенесено из рабочего backlog:

### 17. Kafka follow-up после завершенного redesign

Статус:

- реализовано

Контекст:

- Kafka redesign wave формально закрыта и архивирована;
- новые Kafka-доработки после этого момента допускаются только как отдельные bounded follow-up пакеты.

#### 17.1. Message browser layout aligned with kafka-ui table-first view

Статус:

- реализовано

Контекст:

- текущий `Messages` tab рендерит каждую Kafka message как отдельную card;
- это визуально расходится с `kafka-ui`, где messages поданы table-first списком с выбором записи и отдельным details-view;
- server/store safety contract уже достаточен:
  - bounded read;
  - `assign + seek`;
  - no commit;
  - explicit `selected partition / all partitions`.

Что нужно сделать:

1. убрать card-stack presentation для message records;
2. перевести `Messages` tab на table-first list, ближе к `kafka-ui`;
3. добавить row selection и отдельный details-pane для выбранного сообщения;
4. не менять server API и bounded read contract без явной необходимости;
5. оставить текущие read controls и safety text, но выровнять presentation к более IDE/tool-like message browser.

Что сделано:

1. card-stack presentation для Kafka messages удалена;
2. `Messages` tab переведен на table-first list с columns:
   - `Offset`;
   - `Partition`;
   - `Timestamp`;
   - `Key`;
   - `Value`;
   - `Headers`;
3. добавлен row selection и отдельный details-pane для выбранного сообщения вместо набора самостоятельных cards;
4. server/store API и bounded read semantics не менялись:
   - `assign + seek`;
   - no commit;
   - explicit `selected partition / all partitions`;
5. текущие read controls и safety subtitle сохранены, но screen presentation выровнена ближе к `kafka-ui`.

#### 17.2. Create topic admin path

Статус:

- реализовано

Контекст:

- после завершенного redesign в Kafka UI остается явный functional gap: нельзя создать новый topic;
- cluster-level `Topics` screen уже стал основной точкой входа и именно там должен появиться create-topic flow;
- topic create нельзя смешивать с metadata read paths или settings UI: нужен отдельный узкий admin contract.

Что нужно сделать:

1. добавить отдельный `create topic` contract рядом с Kafka metadata/produce contracts;
2. реализовать server route и AdminClient-based service для create-topic path;
3. поддержать базовые поля:
   - `topic name`;
   - `partitions`;
   - `replication factor`;
   - optional `cleanup policy`;
   - optional `retention.ms`;
   - optional `retention.bytes`;
4. запретить create-topic path для `readOnly` cluster до Kafka client call;
5. вывести create-topic action на `Topics` screen, без прятанья в settings;
6. после успешного create обновлять topic catalog и открывать созданный topic без ручного refresh;
7. добавить targeted server/store regression coverage.

Что сделано:

1. добавлен отдельный `create topic` contract рядом с Kafka metadata/produce contracts;
2. реализованы server route и AdminClient-based service для create-topic path;
3. поддержаны поля:
   - `topic name`;
   - `partitions`;
   - `replication factor`;
   - optional `cleanup policy`;
   - optional `retention.ms`;
   - optional `retention.bytes`;
4. `readOnly` cluster блокируется до Kafka client call;
5. create-topic action выведен на `Topics` screen;
6. после успешного create topic catalog и topic details обновляются автоматически;
7. добавлены targeted server/store/service regressions.

#### 17.3. Produce form aligned with kafka-ui structured editor

Статус:

- реализовано

Контекст:

- current `Produce` tab функционально работает, но UI остается слишком примитивным:
  - raw textarea для headers;
  - слабая visual hierarchy;
  - форма не похожа на `kafka-ui`;
- после `17.1` messages уже выровнены к `kafka-ui`, produce path должен получить такую же плотную tool-like presentation.

Что нужно сделать:

1. убрать raw multiline `headers` textarea и заменить ее на structured header rows;
2. перестроить `Produce` tab в более плотный editor-like shell:
   - key;
   - partition override;
   - headers list;
   - payload editor;
   - delivery result block;
3. не менять существующий server produce contract без необходимости;
4. сохранить текущие safety invariants:
   - no background producer session state;
   - `readOnly` cluster block;
   - payload size guard;
5. добавить regression coverage на shared store mapping produce headers и server route/produce path.

Что сделано:

1. raw multiline `headers` textarea удален и заменен на structured header rows;
2. `Produce` tab перестроен в более плотный editor-like shell:
   - key;
   - partition override;
   - headers list;
   - payload editor;
   - delivery result block;
3. существующий server produce contract сохранен;
4. safety invariants сохранены:
   - no background producer session state;
   - `readOnly` cluster block;
   - payload size guard;
5. regression coverage расширена на shared store mapping produce headers и server route/produce path.

#### 17.4. Fix DOMTokenList runtime error in Kafka all-partitions message browser

Статус:

- реализовано

Контекст:

- при чтении Kafka messages в режиме `ALL_PARTITIONS` UI получал browser-side runtime error:
  - `SyntaxError: The string did not match the expected pattern`;
- фактическая причина была в рендере CSS-классов:
  - в message rows и broker badges в `classes(...)` передавался пустой токен через `if (...) "..." else ""`;
- ошибка проявлялась на message results render и ломала normal message browser flow, хотя server response уже был корректным.

Что нужно сделать:

1. убрать передачу пустых CSS-class token в Kafka web sections;
2. починить `Messages` tab для режима `ALL_PARTITIONS`, не меняя server/store contract;
3. убрать такой же latent defect из `Brokers` screen;
4. добавить targeted browser regression, который проверяет:
   - `ALL_PARTITIONS` read completes;
   - results render;
   - page errors отсутствуют.

Что сделано:

1. пустые CSS-class token убраны из Kafka message rows и broker role badge rendering;
2. `Messages` tab в режиме `ALL_PARTITIONS` больше не падает на DOMTokenList error;
3. latent broker-screen defect устранен тем же safe class rendering pattern;
4. добавлен targeted Playwright smoke regression на `ALL_PARTITIONS` message read без page errors.

#### 17.5. Local file chooser for Kafka TLS material paths in settings UI

Статус:

- реализовано

Контекст:

- Kafka settings UI уже умеет редактировать `JKS` и `PEM` path-like поля, но сейчас только как raw text inputs;
- для локального инструмента этого недостаточно:
  - при добавлении нового cluster пользователь должен иметь возможность выбрать certificate / keystore files из системы;
- browser `input type=file` здесь не подходит как source of truth, потому что Kafka config хранит path values, а не uploaded file content.

Что нужно сделать:

1. добавить local-only file chooser path через `ui-server`, а не через browser upload;
2. поддержать выбор файлов для Kafka TLS material полей:
   - `ssl.truststore.location`;
   - `ssl.truststore.certificates`;
   - `ssl.keystore.location`;
   - `ssl.keystore.certificate.chain`;
   - `ssl.keystore.key`;
3. для `JKS` полей возвращать обычный filesystem path;
4. для `PEM` полей возвращать config-ready placeholder вида `${file:/abs/path/to/file}`;
5. встроить `Browse` actions в Kafka settings UI рядом с соответствующими path fields;
6. сохранить текущий `ui.kafka` config contract:
   - source of truth остается `ui-application.yml`;
   - browser local state не становится вторым config source;
7. добавить targeted service/store/server coverage.

Что сделано:

1. добавлен отдельный local Kafka settings file-picker boundary в `ui-server`;
2. Kafka settings route получил bounded `pick-file` path для supported TLS material fields;
3. для `JKS` fields chooser возвращает raw absolute path;
4. для `PEM` fields chooser возвращает config-ready `${file:/...}` placeholder;
5. в Kafka settings UI добавлены `Выбрать файл` actions рядом с path-like TLS inputs;
6. source of truth остался прежним:
   - YAML сохраняет path values;
   - browser не хранит отдельные uploaded secrets;
7. добавлены targeted tests на picker/service/store/server contract.

#### 17.6. Increase Kafka produce payload editor height

Статус:

- реализовано

Контекст:

- после перестройки `Produce` tab payload editor остался слишком низким для реальной ручной работы;
- при наборе или вставке JSON/message body приходится слишком рано скроллить внутри textarea;
- это не требует изменения Kafka produce contract и относится только к ergonomics текущего editor shell.

Что нужно сделать:

1. зафиксировать отдельный bounded follow-up в backlog;
2. увеличить высоту payload textarea в `Produce` tab;
3. не менять server/store produce contract и structured editor layout сверх необходимого минимума.

Что сделано:

1. follow-up зафиксирован как отдельный backlog package;
2. `Payload` textarea в Kafka `Produce` tab стала выше и удобнее для ручной вставки message body;
3. produce contract, routing и structured form semantics не менялись.

#### 17.7. Kafka message read execution summary and all-partitions latency hardening

Статус:

- реализовано

Контекст:

- после чтения сообщений `kafka-ui` показывает короткий execution summary вида:
  - `DONE`;
  - `3 ms`;
  - `132 Bytes`;
  - `5 messages consumed`;
- в нашем Kafka screen такой summary сейчас отсутствует, хотя он полезен для наблюдаемости bounded read path;
- дополнительно текущий `ALL_PARTITIONS` read path работает медленнее, чем должен:
  - offsets и polls идут последовательно по partition;
  - это особенно заметно на topic с несколькими partition и малым числом сообщений.

Что нужно сделать:

1. добавить в Kafka message-read response summary metadata:
   - `status`;
   - `durationMs`;
   - `consumedBytes`;
   - `consumedMessages`;
2. вывести этот summary в `Messages` tab в стиле, близком к `kafka-ui`;
3. не менять bounded read safety contract:
   - `assign + seek`;
   - no commit;
   - explicit `selected partition / all partitions`;
4. ускорить `ALL_PARTITIONS` path без перехода к background consumer session:
   - уйти от последовательного per-partition read;
   - читать все целевые partition через один short-lived assign/seek session;
   - по возможности использовать bulk offset lookup вместо repeated single-partition calls;
5. добавить targeted service/server coverage на response summary и optimized all-partitions path.

Что сделано:

1. Kafka message-read response расширен summary metadata:
   - `status`;
   - `durationMs`;
   - `consumedBytes`;
   - `consumedMessages`;
2. `Messages` tab теперь показывает execution summary в формате, близком к `kafka-ui`;
3. bounded read safety contract сохранен без background session и без commit offsets;
4. `ALL_PARTITIONS` path переведен с последовательного per-partition read на единый short-lived assign/seek session с bulk offset lookup;
5. добавлены targeted regressions на service/server response contract и optimized all-partitions flow.

#### 17.8. Remove hidden Kafka message read cap and honor explicit UI limit

Статус:

- реализовано

Контекст:

- в текущем Kafka flow пользователь может указать `limit = 500`, но effective result все равно режется hidden server cap;
- это противоречит ожидаемому UX:
  - значение limit уже задается явно в интерфейсе;
  - именно оно и должно определять размер bounded read;
- hidden clamp делает поведение непредсказуемым и ломает ручную topic inspection.

Что нужно сделать:

1. зафиксировать follow-up в backlog;
2. убрать hidden clamp по `ui.kafka.maxRecordsPerRead` из message read service;
3. оставить `ui.kafka.maxRecordsPerRead` только как default/fallback для initial read, а bounded size определять explicit `limit` из интерфейса;
4. не запускать implicit reload и не менять semantics других read controls.

Что сделано:

1. follow-up зафиксирован как отдельный bounded package;
2. hidden server-side clamp по `ui.kafka.maxRecordsPerRead` убран;
3. `ui.kafka.maxRecordsPerRead` остался только default/fallback для initial read, а effective Kafka read limit теперь определяется значением из интерфейса;
4. bounded read semantics и explicit reload model сохранены.

#### 17.9. Keep last Kafka message result visible until explicit reload

Статус:

- реализовано

Контекст:

- сейчас при изменении `Scope`, `Mode`, `Partition` и других draft controls текущий message result исчезает еще до нажатия `Читать сообщения`;
- это создает ложное впечатление, что UI уже сделал reload, хотя фактически пользователь только меняет параметры следующего запроса;
- message browser должен оставаться в модели `draft controls + last executed result`.

Что нужно сделать:

1. перестать очищать `messages` в shared store при изменении draft controls для message read;
2. перестать скрывать current results только потому, что draft `Scope/Mode/Partition` уже отличаются от последнего executed request;
3. оставлять last successful result видимым до явного нажатия `Читать сообщения`;
4. сохранить очистку результатов только при реальной смене topic/cluster или после explicit reload.

Что сделано:

1. shared store больше не сбрасывает `messages` при изменении `Scope/Mode/Partition/Limit/Offset/Timestamp`;
2. `Messages` tab рендерит last executed result для текущего topic, а не фильтрует его по live draft controls;
3. результаты остаются видимыми до explicit read action;
4. topic-level reload по-прежнему очищает stale results только там, где это реально нужно.

#### 17.10. Clarify Kafka TLS settings UI semantics for certificate files and unspecified type

Статус:

- реализовано

Контекст:

- Kafka settings UI уже поддерживает certificate/key file picking для JKS и PEM-backed fields;
- технически certificate picker уже принимает `.crt`, `.cer` и `.pem`, но UI скрывает это за подписью `Выбрать PEM`;
- пустое значение `ssl.truststore.type` / `ssl.keystore.type` рендерится как `default`, хотя по смыслу это не отдельный режим Kafka, а просто `type` не задан.

Что нужно сделать:

1. зафиксировать follow-up в backlog;
2. убрать misleading label `default` из Kafka TLS settings UI и заменить его на явное `Не задано`;
3. переименовать certificate/key picker buttons так, чтобы они отражали роль файла, а не внутренний формат placeholder;
4. добавить helper text с допустимыми расширениями:
   - сертификат: `.crt / .cer / .pem`;
   - private key: `.key / .pem`;
   - keystore: `.jks / .p12 / .pfx`;
5. не менять backend `properties`-first contract и `${file:/...}` semantics без необходимости.

Что сделано:

1. follow-up зафиксирован как отдельный bounded package;
2. blank TLS type больше не рендерится как `default`, в UI используется `Не задано`;
3. browse actions переименованы в role-based labels:
   - `Выбрать truststore`;
   - `Выбрать keystore`;
   - `Выбрать CA сертификат`;
   - `Выбрать client certificate`;
   - `Выбрать private key`;
4. для TLS path fields добавлены helper texts с допустимыми расширениями;
5. backend contract не менялся:
   - certificate fields по-прежнему сохраняются как `${file:/...}`;
   - `.crt/.cer/.pem` для certificates и `.key/.pem` для private key остаются допустимыми.

#### 17.11. Keep Kafka settings usable when cluster catalog is empty and refresh shell after save

Статус:

- реализовано

Контекст:

- Kafka settings UI уже умеет добавлять и сохранять cluster drafts, но shell lifecycle остается неполным;
- при пустом `info.clusters` page-level empty-state перекрывает settings flow и мешает нормальному first-cluster onboarding;
- после сохранения нового cluster текущий shell может оставаться на stale `info.clusters`, из-за чего новый cluster не появляется в navigation до полного page reload.

Что нужно сделать:

1. зафиксировать bugfix в backlog;
2. не блокировать Kafka settings screen global empty-state, если пользователь открывает `cluster-settings`;
3. дать явный path в settings при `0` configured clusters;
4. после save settings refresh-ить shell-level cluster catalog и selected cluster state;
5. добавить regression coverage на first-cluster onboarding flow и post-save shell refresh.

Что сделано:

1. bugfix зафиксирован как отдельный bounded package;
2. `cluster-settings` screen больше не перекрывается global Kafka empty-state;
3. при отсутствии configured clusters page показывает явный переход в settings;
4. после save settings shell refresh-ит `info` и `selectedClusterId`, поэтому новый cluster появляется без full page reload;
5. regression coverage добавлена на settings save/update flow.

#### 17.12. Add JSON syntax highlighting in Kafka message details

Статус:

- реализовано

Контекст:

- после перехода messages на table-first layout details-pane все еще показывает JSON как plain text в темном `pre` block;
- payload уже приходит как pretty-printed JSON, но без визуального разделения ключей, строк, чисел и literal values его тяжело читать на больших сообщениях;
- это чисто web-level ergonomics follow-up и не требует изменения Kafka contracts, store semantics или server response shape.

Что нужно сделать:

1. зафиксировать follow-up в backlog;
2. добавить локальный JSON syntax highlighting для Kafka message details pane;
3. выделять хотя бы:
   - object/array punctuation;
   - property names;
   - string values;
   - numbers;
   - booleans;
   - `null`;
4. сохранить plain-text fallback для non-JSON payload;
5. не менять message browser server/store contract.

Что сделано:

1. follow-up зафиксирован как отдельный bounded package;
2. для Kafka message details добавлен локальный JSON tokenizer и span-based syntax highlighting в web renderer;
3. colored token rendering покрывает punctuation, keys, strings, numbers, booleans и `null`;
4. non-JSON payload по-прежнему отображается как plain text;
5. server/store contracts не менялись.

#### 17.13. Preserve safe plain-text fallback for non-JSON and invalid JSON message payloads

Статус:

- реализовано

Контекст:

- после добавления JSON syntax highlighting в Kafka message details важно зафиксировать safety contract:
  - valid JSON должен подсвечиваться;
  - plain text и invalid JSON не должны ломать read path или web renderer;
- текущий server contract уже формирует `jsonPrettyText` только после успешного parse, но без regression coverage это легко сломать следующими Kafka UI изменениями.

Что нужно сделать:

1. зафиксировать follow-up в backlog;
2. проверить и покрыть тестами сценарии:
   - plain text payload;
   - invalid JSON payload;
   - valid JSON payload;
3. явно сохранить fallback contract:
   - при parse failure payload остается plain text;
   - `jsonPrettyText` остается `null`;
   - message read не падает.

Что сделано:

1. follow-up зафиксирован как отдельный bounded package;
2. в Kafka message service добавлены targeted regressions на plain text и invalid JSON payload;
3. контракт зафиксирован в subsystem doc:
   - JSON highlighting применяется только при успешном parse;
   - non-JSON и invalid JSON остаются plain text и не ломают message browser.

### 9. Операционная надежность long-running операций: реализованные packages `9.1–9.21`

Статус:

- архивировано из рабочего backlog

Архивные переносы:

- `2026-04-24`: packages `9.1–9.5`;
- `2026-04-25`: packages `9.6–9.8`.
- `2026-04-25`: packages `9.9–9.14`.
- `2026-04-25`: packages `9.15–9.17`.
- `2026-04-27`: packages `9.18–9.20`.
- `2026-04-27`: package `9.21`.

#### 9.1. Write-through recovery interrupted FILES runs after restart

Статус:

- реализовано

Контекст:

- для FILES-run history уже есть recovery path `RUNNING -> FAILED` на старте UI;
- сейчас этот recovery выполняется только в памяти через `RunStateStore.load()`;
- `run-state.json` при этом может оставаться stale и продолжать хранить `RUNNING`, хотя текущий runtime уже показывает `FAILED`.

Что нужно сделать:

1. нормализовать `run-state.json` write-through после recovery interrupted runs;
2. не оставлять stale persisted `RUNNING` snapshots как hidden second state;
3. добавить regression coverage на сценарий `restart -> recovered FAILED -> run-state.json rewritten`;
4. синхронизировать state-model contract.

#### 9.2. Eager recovery orphan DB runs after restart

Статус:

- реализовано

Контекст:

- для DB-run history orphan recovery сейчас выполняется только лениво при следующем `startRun()` того же модуля;
- после рестарта `ui-server` stale `RUNNING` записи в registry могут висеть бесконечно, если пользователь больше не стартует этот модуль;
- это оставляет hidden second lifecycle state:
  process-local active registry уже очищен, а persisted DB history все еще выглядит как живой run.

Что нужно сделать:

1. вынести orphan DB run recovery в отдельный reusable support вместо дублирования ad-hoc логики;
2. выполнять eager recovery всех stale DB `RUNNING` runs на инициализации `DatabaseModuleRunService`;
3. сохранить текущий bounded recovery при `startRun()` для stale runs того же модуля, но перевести его на тот же shared support;
4. добавить regression coverage на сценарий `restart -> orphan DB run rewritten to FAILED before first manual action`;
5. синхронизировать state-model contract для active DB run registry и DB run history.

#### 9.3. Failure-side normalization DB run summary counters

Статус:

- реализовано

Контекст:

- в DB run-history failure path `module_run_source_result` уже нормализуется из `PENDING/RUNNING` в `FAILED`;
- при этом aggregate counters в `module_run` (`successful_source_count`, `failed_source_count`, `skipped_source_count`) не пересчитываются;
- из-за этого history list может показывать stale `0/0/0` или другие устаревшие значения для уже проваленного DB-run, хотя details/source results уже отражают фактический failure outcome.

Что нужно сделать:

1. нормализовать aggregate source counters в `module_run` внутри DB failure mutation path;
2. не оставлять расхождение между DB history list и detailed source results после `markRunFailed`;
3. добавить regression coverage на сценарий `markRunFailed -> sourceResults FAILED -> failedSourceCount updated`;
4. при необходимости синхронизировать state-model contract для DB run history summary.

#### 9.4. Server-level regression for startup recovery DB runs

Статус:

- реализовано

Контекст:

- пакеты `9.2–9.3` закрыли eager startup recovery orphan DB runs и нормализацию counters;
- сейчас эти invariants защищены unit/store-level тестами, но не зафиксированы через реальный `/api/module-runs/database/*` server flow;
- из-за этого route wiring или history adapter могут снова начать отдавать stale `RUNNING` status, даже если нижние support-слои уже нормализуют run.

Что нужно сделать:

1. добавить отдельный server-level regression test на сценарий `ui-server restart -> orphan DB run already visible as FAILED via module-runs API`;
2. проверить через HTTP-слой и history adapter, что `activeRunId` уже `null`, а status/details не показывают stale `RUNNING`;
3. вынести минимальный in-memory DB run test support в reusable test helper, чтобы не плодить ad-hoc дубли между server и run tests.

Что сделано:

1. добавлен отдельный [DatabaseModuleRunsRecoveryServerTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/server/DatabaseModuleRunsRecoveryServerTest.kt) на реальный `/api/module-runs/database/*` flow;
2. проверено, что после старта `ui-server` orphan DB run уже отдается как `FAILED`, а `activeRunId` равен `null`;
3. общий in-memory support вынесен в [DatabaseModuleRunTestSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/run/DatabaseModuleRunTestSupport.kt) и переиспользуется run/server тестами.

#### 9.5. Server-level regression for startup recovery FILES runs

Статус:

- реализовано

Контекст:

- после `9.1` FILES restart recovery уже write-through нормализует stale `RUNNING` snapshot в `FAILED`;
- этот invariant пока зафиксирован только на `RunManager`/persisted-state уровне;
- через реальный `/api/module-runs/files/*` flow отдельной регрессии пока нет, поэтому route wiring или history adapter могут снова начать показывать stale `RUNNING`, даже если state-store уже восстановил run.

Что нужно сделать:

1. добавить отдельный server-level regression test на сценарий `ui-server restart -> interrupted FILES run already visible as FAILED via module-runs API`;
2. проверить через HTTP-слой, что `activeRunId` уже `null`, а history/details не показывают stale `RUNNING`;
3. не раздувать existing giant [ServerTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/server/ServerTest.kt): вынести сценарий в отдельный targeted test file.

Что сделано:

1. добавлен отдельный [FilesModuleRunsRecoveryServerTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/server/FilesModuleRunsRecoveryServerTest.kt) на реальный `/api/module-runs/files/*` flow;
2. проверено, что после старта `ui-server` interrupted FILES run уже отдается как `FAILED`, а `activeRunId` равен `null`;
3. recovery message проверяется через HTTP-слой без раздувания [ServerTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/server/ServerTest.kt), что выровняло FILES/DB restart-contract на уровне server regressions.

#### 9.6. SQL manual transaction final snapshot cleanup

Статус:

- реализовано

Контекст:

- SQL manual transaction уже защищена owner token, heartbeat, release recovery window и pending commit TTL;
- после успешного `Commit` или явного `Rollback` control-path больше не должен выглядеть активным;
- публичный execution snapshot не должен сохранять `ownerLeaseExpiresAt` или `pendingCommitExpiresAt` после финального завершения транзакции.

Что нужно сделать:

1. добавить regression coverage для `Commit` и явного `Rollback`;
2. проверить, что после финализации транзакции snapshot:
   - имеет `COMMITTED` или `ROLLED_BACK`;
   - не содержит active `ownerLeaseExpiresAt`;
   - не содержит active `pendingCommitExpiresAt`;
   - не содержит public `ownerToken`;
3. исправить серверную финализацию без изменения owner-token проверок до действия;
4. не менять SQL execution lifecycle и transaction safety contracts.

Что сделано:

1. добавлена regression coverage в `SqlConsoleQueryManagerTransactionSafetyTest`:
   - successful `Commit` очищает public control-path metadata;
   - explicit `Rollback` очищает public control-path metadata;
2. `SqlConsoleQueryTransactionSupport` после финального `Commit/Rollback` очищает:
   - `ownerToken`;
   - `ownerLeaseExpiresAt`;
   - `pendingCommitExpiresAt`;
   - `ownerReleaseDeadline`;
3. owner-token проверка до выполнения действия сохранена без изменений;
4. SQL execution lifecycle, heartbeat, release recovery window и pending commit TTL не менялись.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest'` — `BUILD SUCCESSFUL`.

#### 9.7. SQL manual transaction final route response regression

Статус:

- реализовано

Контекст:

- `9.6` закрыл cleanup финального snapshot на уровне `SqlConsoleQueryManager`;
- нужен server-level regression, чтобы HTTP boundary `/commit` и `/rollback` не вернул наружу active control-path metadata после финализации;
- route handlers должны оставаться thin и не содержать отдельной lifecycle-логики.

Что нужно сделать:

1. добавить server-level regression в `SqlConsoleServerTest`;
2. проверить route responses после explicit `Commit` и explicit `Rollback`:
   - `transactionState` равен `COMMITTED` или `ROLLED_BACK`;
   - `ownerToken` отсутствует или `null`;
   - `ownerLeaseExpiresAt` отсутствует или `null`;
   - `pendingCommitExpiresAt` отсутствует или `null`;
3. не добавлять lifecycle-логику в route handlers;
4. не менять contracts, уже закрепленные в `9.6`.

Что сделано:

1. добавлен server-level regression в `SqlConsoleServerTest` для HTTP `/commit` и `/rollback`;
2. route responses проверяются на финальный `transactionState` и отсутствие active control-path metadata:
   - `ownerToken`;
   - `ownerLeaseExpiresAt`;
   - `pendingCommitExpiresAt`;
3. route handlers оставлены thin: lifecycle cleanup остается в `SqlConsoleQueryManager`;
4. JSON-проверка допускает `null` или отсутствие поля, чтобы тест не зависел от политики сериализации null.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest' --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest'` — `BUILD SUCCESSFUL`.

#### 9.8. SQL manual transaction rollback history final state regression

Статус:

- реализовано

Контекст:

- SQL execution history используется для диагностики long-running и manual transaction flow;
- при explicit `Rollback` history entry не должна оставаться в `PENDING_COMMIT`;
- это отдельный observability contract: пользователь должен видеть финальное состояние транзакции в истории.

Что нужно сделать:

1. добавить regression coverage для explicit `Rollback` в execution history;
2. проверить, что history entry для того же `executionId` обновляется с `PENDING_COMMIT` на `ROLLED_BACK`;
3. сохранить один entry на execution, без дублирования;
4. не менять формат persisted history без отдельной задачи.

Что сделано:

1. добавлен manager-level regression в `SqlConsoleQueryManagerTransactionSafetyTest`;
2. проверено, что после старта manual transaction history содержит один entry с `PENDING_COMMIT`;
3. после explicit `Rollback` тот же execution history entry обновляется до `ROLLED_BACK`;
4. формат persisted history не менялся, дублирование execution entry не вводилось.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest' --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest'` — `BUILD SUCCESSFUL`.

#### 9.9. SQL async final snapshot control-path cleanup

Статус:

- реализовано

Контекст:

- `9.6–9.8` закрыли cleanup финальных manual transaction states после explicit `Commit/Rollback`;
- обычный async SQL execution без pending transaction тоже является terminal state после `SUCCESS/FAILED/CANCELLED`;
- финальный public snapshot не должен выглядеть как active control-path session и не должен возвращать `ownerToken`.

Что нужно сделать:

1. добавить manager-level regression для обычного async SQL completion;
2. проверить terminal snapshot после `SUCCESS`:
   - `ownerToken` отсутствует;
   - `ownerLeaseExpiresAt` отсутствует;
   - `pendingCommitExpiresAt` отсутствует;
   - `transactionState` остается `NONE`;
3. исправить `SqlConsoleQueryStateSupport.storeCompletedExecution` без изменения running/heartbeat/cancel contracts;
4. не менять manual transaction pending/commit/rollback semantics.

Что сделано:

1. добавлена manager-level regression в `SqlConsoleQueryManagerExecutionTest`;
2. terminal `SUCCESS` snapshot для обычного async SQL execution теперь проверяется на:
   - `transactionState = NONE`;
   - `ownerToken = null`;
   - `ownerLeaseExpiresAt = null`;
   - `pendingCommitExpiresAt = null`;
3. `SqlConsoleQueryStateSupport.storeCompletedExecution` очищает public `ownerToken` для финального execution без pending transaction;
4. internal `ActiveExecution.ownerToken` не менялся, running/heartbeat/cancel и manual transaction contracts не затронуты.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerExecutionTest.completes query asynchronously' --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.async sql final route response clears control path metadata'` — `BUILD SUCCESSFUL`.

#### 9.10. SQL async final route response control-path regression

Статус:

- реализовано

Контекст:

- `9.9` должен очистить финальный snapshot на уровне manager/state support;
- нужен HTTP-boundary regression, чтобы `/api/sql-console/query/{id}` после обычного завершения не возвращал наружу active control-path metadata.

Что нужно сделать:

1. добавить server-level regression в `SqlConsoleServerTest`;
2. проверить route response после обычного async `SUCCESS`:
   - `status` равен `SUCCESS`;
   - `transactionState` равен `NONE`;
   - `ownerToken` отсутствует или `null`;
   - `ownerLeaseExpiresAt` отсутствует или `null`;
   - `pendingCommitExpiresAt` отсутствует или `null`;
3. route handlers оставить thin;
4. не менять DTO contract для running snapshots, где owner token нужен control-path.

Что сделано:

1. добавлен server-level regression в `SqlConsoleServerTest`;
2. HTTP `/api/sql-console/query/{id}` после обычного async `SUCCESS` проверяется на отсутствие active control-path metadata;
3. route handlers не менялись;
4. running snapshots по-прежнему возвращают `ownerToken`, необходимый для heartbeat/cancel control-path.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerExecutionTest.completes query asynchronously' --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.async sql final route response clears control path metadata'` — `BUILD SUCCESSFUL`.

#### 9.11. SQL system rollback final snapshot control-path cleanup

Статус:

- реализовано

Контекст:

- terminal snapshots после explicit `Commit/Rollback` и обычного async `SUCCESS` уже очищают public control-path metadata;
- system rollback states `ROLLED_BACK_BY_TIMEOUT` и `ROLLED_BACK_BY_OWNER_LOSS` тоже являются terminal states;
- public snapshot после system rollback не должен возвращать `ownerToken`, `ownerLeaseExpiresAt` или `pendingCommitExpiresAt`.

Что нужно сделать:

1. расширить regression coverage для system rollback terminal states:
   - pending commit TTL;
   - released pending commit owner-loss;
   - owner lease loss во время running manual transaction;
   - release running manual transaction recovery timeout;
2. проверить, что terminal snapshots очищают:
   - `ownerToken`;
   - `ownerLeaseExpiresAt`;
   - `pendingCommitExpiresAt`;
3. исправить `SqlConsoleQueryStateSupport` без изменения причин rollback и error messages;
4. не менять active `PENDING_COMMIT` snapshots, где owner token нужен для control-path.

Что сделано:

1. расширены regression checks в `SqlConsoleQueryManagerTransactionSafetyTest` для:
   - pending commit TTL rollback;
   - released pending commit owner-loss rollback;
   - owner lease loss во время running manual transaction;
   - release running manual transaction recovery timeout;
2. terminal system rollback snapshots теперь проверяются на отсутствие:
   - `ownerToken`;
   - `ownerLeaseExpiresAt`;
   - `pendingCommitExpiresAt`;
3. `SqlConsoleQueryStateSupport` очищает public `ownerToken` в terminal rollback snapshots;
4. причины rollback, `errorMessage`, active `PENDING_COMMIT` и control-path для running snapshots не менялись.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest.release pending commit auto rollbacks when recovery window expires' --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest.auto rollbacks pending commit when ttl expires' --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest.auto rollbacks completed manual transaction when owner lease was lost during running' --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest.release running manual transaction causes safe rollback after recovery window'` — `BUILD SUCCESSFUL`.

#### 9.12. SQL terminal release manager conflict

Статус:

- реализовано

Контекст:

- `releaseOwnership` является active control-path action для `RUNNING` и `PENDING_COMMIT`;
- после terminal state активного владельца больше нет, даже если stale client все еще знает старый owner token;
- manager-level contract должен запрещать successful release после завершения execution.

Что нужно сделать:

1. добавить manager-level regression для `releaseOwnership` после обычного async `SUCCESS`;
2. добавить manager-level regression для `releaseOwnership` после explicit `COMMITTED`;
3. проверить, что оба сценария возвращают `UiStateConflictException`;
4. не менять поведение release для active `RUNNING` и `PENDING_COMMIT`.

Что сделано:

1. добавлен manager-level regression в `SqlConsoleQueryManagerExecutionTest` для `releaseOwnership` после обычного async `SUCCESS`;
2. добавлен manager-level regression в `SqlConsoleQueryManagerTransactionSafetyTest` для `releaseOwnership` после explicit `COMMITTED`;
3. `SqlConsoleQueryStateSupport.releaseOwnership` теперь возвращает `UiStateConflictException`, если execution уже не `RUNNING` и не `PENDING_COMMIT`;
4. release для active `RUNNING` и `PENDING_COMMIT` не менялся.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerExecutionTest.rejects release after async query completed' --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest.keeps pending transaction until commit' --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.async sql final route response clears control path metadata and rejects release' --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.manual transaction final route responses clear control path metadata'` — `BUILD SUCCESSFUL`.

#### 9.13. SQL terminal release route conflict after async completion

Статус:

- реализовано

Контекст:

- stale browser tab может отправить `/release` после того, как async SQL execution уже завершился;
- HTTP boundary не должен отвечать `200 OK`, если control-path уже terminal.

Что нужно сделать:

1. добавить server-level regression для `/api/sql-console/query/{id}/release` после обычного async `SUCCESS`;
2. проверить `409 Conflict`;
3. проверить, что route handler остается thin и не добавляет lifecycle-логику;
4. не менять running snapshot DTO, где owner token нужен для release.

Что сделано:

1. server-level regression добавлен в `SqlConsoleServerTest`;
2. `/api/sql-console/query/{id}/release` после обычного async `SUCCESS` возвращает `409 Conflict`;
3. route handler не менялся: вся lifecycle-логика осталась в `SqlConsoleQueryStateSupport`;
4. running snapshot DTO и active release contract не менялись.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerExecutionTest.rejects release after async query completed' --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest.keeps pending transaction until commit' --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.async sql final route response clears control path metadata and rejects release' --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.manual transaction final route responses clear control path metadata'` — `BUILD SUCCESSFUL`.

#### 9.14. SQL terminal release route conflict after transaction finalization

Статус:

- реализовано

Контекст:

- explicit `Commit` и explicit `Rollback` финализируют manual transaction;
- после этого `/release` для старого owner token тоже не должен быть successful no-op.

Что нужно сделать:

1. расширить server-level regression для final `COMMITTED`;
2. расширить server-level regression для final `ROLLED_BACK`;
3. проверить, что `/release` после обоих финальных состояний возвращает `409 Conflict`;
4. не менять `Commit/Rollback` owner-token проверки до действия.

Что сделано:

1. server-level regression для final `COMMITTED` расширен проверкой `/release -> 409 Conflict`;
2. server-level regression для final `ROLLED_BACK` расширен проверкой `/release -> 409 Conflict`;
3. `Commit/Rollback` owner-token checks до действия не менялись;
4. final transaction snapshots по-прежнему очищают public control-path metadata.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerExecutionTest.rejects release after async query completed' --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest.keeps pending transaction until commit' --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.async sql final route response clears control path metadata and rejects release' --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.manual transaction final route responses clear control path metadata'` — `BUILD SUCCESSFUL`.

#### 9.15. SQL terminal heartbeat route regression

Статус:

- реализовано

Контекст:

- stale browser tab может продолжить отправлять heartbeat после terminal `SUCCESS`;
- manager уже должен отклонять heartbeat, если execution больше не `RUNNING` и не `PENDING_COMMIT`;
- HTTP boundary должен явно закрепить этот contract как `409 Conflict`.

Что нужно сделать:

1. добавить server-level regression для `/api/sql-console/query/{id}/heartbeat` после обычного async `SUCCESS`;
2. проверить `409 Conflict`;
3. проверить, что response body объясняет, что heartbeat больше не нужен;
4. не менять heartbeat для active `RUNNING` и `PENDING_COMMIT`.

Что сделано:

1. добавлена server-level regression в `SqlConsoleServerTest` для `/heartbeat` после terminal async `SUCCESS`;
2. проверяется `409 Conflict`;
3. проверяется diagnostic response body `больше не требует heartbeat`;
4. runtime-код не менялся: active heartbeat contract не затронут.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.async sql final route response clears control path metadata and rejects release'` — `BUILD SUCCESSFUL`.

#### 9.16. SQL terminal cancel route regression

Статус:

- реализовано

Контекст:

- stale browser tab может отправить `/cancel` после terminal `SUCCESS`;
- terminal execution уже нельзя отменить повторно;
- HTTP boundary должен явно закрепить этот contract как `409 Conflict`.

Что нужно сделать:

1. добавить server-level regression для `/api/sql-console/query/{id}/cancel` после обычного async `SUCCESS`;
2. проверить `409 Conflict`;
3. проверить, что response body сообщает, что запрос уже завершен;
4. не менять cancel для active `RUNNING`.

Что сделано:

1. добавлена server-level regression в `SqlConsoleServerTest` для `/cancel` после terminal async `SUCCESS`;
2. проверяется `409 Conflict`;
3. проверяется diagnostic response body `уже завершен`;
4. runtime-код не менялся: active cancel contract не затронут.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.async sql final route response clears control path metadata and rejects release'` — `BUILD SUCCESSFUL`.

#### 9.17. SQL terminal transaction action repeat route regression

Статус:

- реализовано

Контекст:

- после explicit `Commit` или explicit `Rollback` manual transaction финализирована;
- повторные `/commit` или `/rollback` со старым owner token не должны быть successful no-op;
- HTTP boundary должен закрепить это как terminal transaction conflict.

Что нужно сделать:

1. добавить server-level regression для повторного `/commit` после final `COMMITTED`;
2. добавить server-level regression для повторного `/rollback` после final `ROLLED_BACK`;
3. проверить `409 Conflict`;
4. не менять owner-token checks до первого successful `Commit/Rollback`.

Что сделано:

1. добавлены server-level regressions в `SqlConsoleServerTest` для повторного `/commit` после `COMMITTED`;
2. добавлены server-level regressions в `SqlConsoleServerTest` для повторного `/rollback` после `ROLLED_BACK`;
3. проверяется `409 Conflict`;
4. runtime-код не менялся: первая successful transaction action и owner-token checks не затронуты.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.manual transaction final route responses clear control path metadata'` — `BUILD SUCCESSFUL`.

#### 9.18. SQL pending commit TTL rollback history regression

Статус:

- выполнено

Контекст:

- `PENDING_COMMIT` auto-rollback по TTL уже проверяется по current snapshot;
- execution history является пользовательской диагностикой long-running/manual transaction flow;
- history entry не должна оставаться в `PENDING_COMMIT` после system rollback.

Что нужно сделать:

1. добавить manager-level regression для `PENDING_COMMIT -> ROLLED_BACK_BY_TIMEOUT`;
2. проверить, что history entry обновляется для того же `executionId`;
3. проверить, что entry не дублируется;
4. не менять persisted history format.

Что сделано:

1. добавлена manager-level regression в `SqlConsoleQueryManagerTransactionSafetyTest`;
2. проверяется переход history entry `PENDING_COMMIT -> ROLLED_BACK_BY_TIMEOUT`;
3. проверяется single-entry behavior для того же `executionId`;
4. persisted history format не менялся.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest'` — `BUILD SUCCESSFUL`.

#### 9.19. SQL released pending commit owner-loss rollback history regression

Статус:

- выполнено

Контекст:

- release pending commit recovery window уже приводит к `ROLLED_BACK_BY_OWNER_LOSS`;
- history должна показывать финальный rollback reason, а не stale `PENDING_COMMIT`;
- это нужно для диагностики закрытой вкладки или потерянного owner session.

Что нужно сделать:

1. добавить manager-level regression для released `PENDING_COMMIT -> ROLLED_BACK_BY_OWNER_LOSS`;
2. проверить, что history entry обновляется для того же `executionId`;
3. проверить, что entry не дублируется;
4. не менять release/heartbeat runtime contract.

Что сделано:

1. добавлена manager-level regression в `SqlConsoleQueryManagerTransactionSafetyTest`;
2. проверяется переход history entry `PENDING_COMMIT -> ROLLED_BACK_BY_OWNER_LOSS`;
3. проверяется single-entry behavior для того же `executionId`;
4. release/heartbeat runtime contract не менялся.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest'` — `BUILD SUCCESSFUL`.

#### 9.20. SQL running owner-loss rollback history regression

Статус:

- выполнено

Контекст:

- если owner lease потерян во время manual transaction `RUNNING`, успешное выполнение должно завершаться system rollback;
- current snapshot уже проверяется, но history должна показывать финальный `ROLLED_BACK_BY_OWNER_LOSS`;
- пользователь не должен видеть в истории ambiguous success без причины rollback.

Что нужно сделать:

1. добавить manager-level regression для owner lease loss while `RUNNING`;
2. проверить, что после completion history entry имеет `ROLLED_BACK_BY_OWNER_LOSS`;
3. проверить, что entry не дублируется;
4. не менять running execution lifecycle.

Что сделано:

1. добавлена manager-level regression в `SqlConsoleQueryManagerTransactionSafetyTest`;
2. проверяется, что после completion history entry имеет `ROLLED_BACK_BY_OWNER_LOSS`;
3. проверяется single-entry behavior для того же `executionId`;
4. running execution lifecycle не менялся.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManagerTransactionSafetyTest'` — `BUILD SUCCESSFUL`.

#### 9.21. SQL system rollback history route regression

Статус:

- выполнено

Контекст:

- manager-level tests уже проверяют, что execution history обновляется после system rollback;
- UI читает историю через `/api/sql-console/history`, поэтому route-level contract тоже должен быть закреплен;
- после TTL rollback или owner-loss rollback пользователь не должен видеть stale `PENDING_COMMIT` в history screen.

Что нужно сделать:

1. добавить server-level regression для `/api/sql-console/history` после `PENDING_COMMIT -> ROLLED_BACK_BY_TIMEOUT`;
2. добавить server-level regression для `/api/sql-console/history` после released `PENDING_COMMIT -> ROLLED_BACK_BY_OWNER_LOSS`;
3. проверить single-entry behavior для того же `executionId` через HTTP response;
4. не менять persisted history format и runtime safety contracts.

Что сделано:

1. добавлен server-level regression в `SqlConsoleServerTest`;
2. через HTTP route проверяется `PENDING_COMMIT -> ROLLED_BACK_BY_TIMEOUT` в `/api/sql-console/history`;
3. через HTTP route проверяется released `PENDING_COMMIT -> ROLLED_BACK_BY_OWNER_LOSS` в `/api/sql-console/history`;
4. single-entry behavior проверяется по количеству `executionId` в response;
5. persisted history format и runtime safety contracts не менялись.

Проверка:

- `./gradlew :ui-server:test --tests 'com.sbrf.lt.platform.ui.server.SqlConsoleServerTest.sql history route exposes system rollback terminal states'` — `BUILD SUCCESSFUL`.

Основные даты:

- `2026-04-24`
- `2026-04-25`
- `2026-04-27`

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
