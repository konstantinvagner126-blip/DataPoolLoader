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

1. [15.9. Kafka UI redesign toward `kafka-ui` structural model](/Users/kwdev/DataPoolLoader/BACKLOG.md)
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

### 15. Kafka: redesign after baseline

Статус:

- частично реализовано

Контекст:

- baseline Kafka-подсистемы `15.0–15.8` закрыт и архивирован;
- текущий активный поток — redesign UI и навигации после baseline, без разрушения уже реализованных config/safety/runtime contracts.

Архив:

- completed baseline и home regrouping packages вынесены в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

#### 15.1.2. Транспонирование групповых карточек главной страницы в три строки

Статус:

- запланировано

Контекст:

- после предыдущего выравнивания home screen все еще читался как горизонтальный ряд из трех крупных групповых блоков `1 2 3`;
- пользовательский target layout для этих групп — три явные строки одна под другой;
- внутренние карточки внутри групп `SQL и Kafka` и `Справка и проект` должны остаться компактными.

Что нужно сделать:

1. транспонировать главный grid-контракт home screen так, чтобы три group-card шли в три строки;
2. растянуть каждый group-card на полную ширину home shell;
3. сохранить desktop-иерархию внутренних section-grid для вложенных карточек;
4. обновить visual baseline главной страницы уже после стабилизации UI-волны.

#### 15.9. Kafka UI redesign toward `kafka-ui` structural model

Статус:

- запланировано

Согласованный продуктовый контракт:

- Kafka остается отдельным `/kafka` screen и отдельной подсистемой;
- layout двигается к `kafka-ui`-подобной модели:
  - cluster-first navigation;
  - `Topics / Consumer Groups / Brokers` как основные cluster sections;
  - topic details page с tab model:
    - `Overview`;
    - `Messages`;
    - `Consumers`;
    - `Settings`;
    - `Produce`;
- `Messages` по умолчанию открываются в режиме `all partitions`, а partition filter остается вторичным drill-down;
- `Consumer Groups` нужны и как отдельный screen, и как topic-scoped tab `Consumers`;
- `Brokers` нужны в первой волне как read-only screen;
- `Produce` остается отдельной topic-tab, а не inline block на overview;
- `Create Topic`, ACLs, Schema Registry, Connect, reset offsets и destructive admin actions в эту волну не входят.

Временный testing contract на время redesign:

- browser-level Playwright visual suite временно не считается обязательным gate для Kafka UI пакетов;
- до стабилизации нового layout обязательный minimum:
  - compile;
  - server/store tests там, где меняется контракт;
  - targeted manual verification там, где меняется shell/navigation;
- после стабилизации shell/navigation структура должна быть заново зафиксирована новыми visual baseline-ами.

#### 15.9.1. Cluster landing и left navigation shell

Что нужно сделать:

1. перевести Kafka screen с current single-page explorer на cluster-first shell;
2. ввести явную левую навигацию уровня кластера:
   - `Topics`;
   - `Consumer Groups`;
   - `Brokers`;
3. сделать entry-screen устойчивым и читаемым как при одном, так и при нескольких кластерах;
4. не превращать cluster landing в dashboard с лишними counters и noise-blocks.

#### 15.9.2. Topics screen redesign к table-first модели

Что нужно сделать:

1. заменить текущую card-heavy topic catalog подачу на плотный table/list screen, ближе к `kafka-ui`;
2. сохранить search/filter и cluster-aware navigation;
3. сделать row-click navigation в topic details screen;
4. оставить metadata компактной:
   - partitions;
   - replication;
   - cleanup policy;
   - internal flag.

#### 15.9.3. Topic details page и tab shell

Что нужно сделать:

1. вынести topic details в отдельный page shell, а не держать его как inline continuation списка;
2. ввести tab model:
   - `Overview`;
   - `Messages`;
   - `Consumers`;
   - `Settings`;
   - `Produce`;
3. привести current overview/messages/produce/settings в новый tab contract без потери уже реализованных safety-ограничений;
4. держать page route-driven:
   - `cluster`;
   - `topic`;
   - `tab`.

#### 15.9.4. Consumer Groups screen и topic-consumers drill-down

Что нужно сделать:

1. сделать отдельный cluster-level screen `Consumer Groups`;
2. сохранить topic-scoped вкладку `Consumers` как filtered/drill-down view;
3. показывать:
   - `group id`;
   - `state`;
   - `member count`;
   - `lag`;
4. partial-access и authorization failures должны оставаться section-level, а не валить весь Kafka shell.

#### 15.9.5. Brokers screen и read-only cluster operations shell

Что нужно сделать:

1. добавить cluster-level read-only screen `Brokers`;
2. показывать минимально полезную broker metadata:
   - broker id;
   - host/port или advertised endpoint, если доступно;
   - controller flag;
   - rack, если доступен без лишнего усложнения;
3. не добавлять broker actions и admin operations;
4. зафиксировать, какие existing Kafka UI fragments после `15.9.1–15.9.5` считаются deprecated и подлежат удалению во второй волне.

## P2

### 16. Усилить тестовую стратегию под архитектурную программу

Статус:

- частично реализовано

Что уже есть:

- bounded server-side coverage gate `>= 80%` для `:ui-server`;
- committed visual baseline wave `16.1–16.16`, вынесенная в архив;
- browser smoke и visual harness для основных экранов и ключевых состояний.

Что остается:

1. расширять test strategy уже после стабилизации текущего Kafka UI redesign, а не параллельно с большим shell-переустройством;
2. после закрытия `15.9` вернуться к:
   - новым visual baseline-ам под Kafka redesign;
   - дополнительным scenario-level coverage пакетам;
   - targeted store/server tests для новых reliability stream-ов.

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
