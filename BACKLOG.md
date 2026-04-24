# DataPoolLoader Backlog

Рабочий backlog держит только активные и незакрытые stream-ы.

Все завершенные секции, closure review и исторические implementation packages вынесены в:

- [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md)

Связанные документы:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md)
- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)
- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)
- [KAFKA_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/KAFKA_FAILURE_SCENARIOS.md)

## Правила приоритизации

- `P0` — build/runtime/safety regressions и нарушения архитектурных инвариантов.
- `P1` — активные subsystem и reliability stream-ы.
- `P2` — quality и test strategy follow-up после стабилизации `P1`.
- `P3` — отложенные product/UX-задачи, которые не должны обгонять текущие архитектурные потоки.

## Активные приоритеты

1. [17. Kafka follow-up после завершенного redesign](/Users/kwdev/DataPoolLoader/BACKLOG.md)
2. [9. Операционная надежность long-running операций](/Users/kwdev/DataPoolLoader/BACKLOG.md)
3. [11. Repo-level архитектурная дисциплина](/Users/kwdev/DataPoolLoader/BACKLOG.md)
4. [16. Тестовая стратегия](/Users/kwdev/DataPoolLoader/BACKLOG.md)

## P0

Активных `P0`-задач сейчас нет.

Новый `P0` открывается только если появляется:

- build/runtime regression;
- safety-break в SQL/Kafka/long-running flow;
- нарушение архитектурного инварианта из [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md).

## P1

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

Что остается:

1. выровнять lifecycle-модели между подсистемами;
2. убрать неочевидные pending-состояния;
3. закрепить rollback / cleanup / recovery policy;
4. усилить scenario-level и server-side проверки именно на long-running контрактах.

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

### 11. Зафиксировать и поддерживать repo-level архитектурную дисциплину

Статус:

- частично реализовано

Что уже закреплено:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [AGENTS.md](/Users/kwdev/DataPoolLoader/AGENTS.md)
- [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md)

Что остается:

1. держать repo-level правила синхронизированными с новыми closure review и subsystem decisions;
2. не допускать расхождения между кодом, backlog и архитектурными `.md`;
3. после каждого большого этапа делать отдельный архитектурный review и, если нужно, корректировать backlog и правила;
4. не открывать повторно закрытые cleanup-wave без concrete сигнала из [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md).

История закрытых rule-sync пакетов вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

## P2

### 16. Усилить тестовую стратегию под архитектурную программу

Статус:

- частично реализовано

Что уже есть:

- bounded server-side coverage gate `>= 80%` для `:ui-server`;
- committed visual baseline wave `16.1–16.16`, вынесенная в архив;
- browser smoke и visual harness для основных экранов и ключевых состояний.

Что остается:

1. расширять test strategy уже после стабилизации текущих `P1` stream-ов, а не параллельно с большим subsystem redesign;
2. следующий релевантный visual/testing follow-up:
   - новые visual baseline-ы под завершенный Kafka redesign;
   - дополнительные scenario-level coverage пакеты для long-running reliability stream;
   - targeted store/server tests для новых bounded subsystem changes.

История завершенных test packages вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

## P3

### 20. Product и UX-задачи, не влияющие на архитектурную программу

Статус:

- отложено

Сюда относятся все задачи, которые:

- не повышают архитектурный порядок;
- не улучшают надежность;
- не уменьшают structural debt;
- не помогают cleanup legacy;
- не входят в активные subsystem stream-ы `P1/P2`.
