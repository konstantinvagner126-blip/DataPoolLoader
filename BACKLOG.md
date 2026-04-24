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

1. [9. Операционная надежность long-running операций](/Users/kwdev/DataPoolLoader/BACKLOG.md)
2. [11. Repo-level архитектурная дисциплина](/Users/kwdev/DataPoolLoader/BACKLOG.md)
3. [16. Тестовая стратегия](/Users/kwdev/DataPoolLoader/BACKLOG.md)

## P0

Активных `P0`-задач сейчас нет.

Новый `P0` открывается только если появляется:

- build/runtime regression;
- safety-break в SQL/Kafka/long-running flow;
- нарушение архитектурного инварианта из [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md).

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
