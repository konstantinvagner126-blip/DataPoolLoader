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

- реализовано

Контекст:

- после предыдущего выравнивания home screen все еще читался как горизонтальный ряд из трех крупных групповых блоков `1 2 3`;
- пользовательский target layout для этих групп — три явные строки одна под другой;
- внутренние карточки внутри групп `SQL и Kafka` и `Справка и проект` должны остаться компактными.

Что нужно сделать:

1. транспонировать главный grid-контракт home screen так, чтобы три group-card шли в три строки;
2. растянуть каждый group-card на полную ширину home shell;
3. сохранить desktop-иерархию внутренних section-grid для вложенных карточек;
4. обновить visual baseline главной страницы уже после стабилизации UI-волны.

Что сделано:

1. главный `home-grid` переведен в одноколоночный layout, так что три group-card теперь идут тремя явными строками;
2. каждый group-card растянут на полную ширину home shell;
3. внутренние desktop grids внутри блоков `SQL и Kafka` и `Справка и проект` сохранены без изменений;
4. visual baseline сознательно не обновлялся: он остается отложенным до стабилизации текущей UI-волны, по действующему временному контракту.

#### 15.9. Kafka UI redesign toward `kafka-ui` structural model

Статус:

- реализовано

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

Итог первой волны:

- cluster-first navigation реализована;
- topic details переведены в explicit tab shell;
- cluster-level screens `Topics / Consumer Groups / Brokers` собраны как отдельные bounded entry points;
- дальнейшее развитие Kafka UI после этой точки должно идти уже как отдельные пакеты:
  - visual baseline reset;
  - second-wave cleanup удаления deprecated fragments;
  - новые product features вне baseline redesign.

#### 15.9.1. Cluster landing и left navigation shell

Статус:

- реализовано

Что нужно сделать:

1. перевести Kafka screen с current single-page explorer на cluster-first shell;
2. ввести явную левую навигацию уровня кластера:
   - `Topics`;
   - `Consumer Groups`;
   - `Brokers`;
3. сделать entry-screen устойчивым и читаемым как при одном, так и при нескольких кластерах;
4. не превращать cluster landing в dashboard с лишними counters и noise-blocks.

Что сделано:

1. Kafka screen переведен на cluster-first shell с отдельным left sidebar вместо прежнего верхнего cluster strip;
2. в route/state введен явный `clusterSection` contract:
   - `topics`;
   - `consumer-groups`;
   - `brokers`;
3. auto-select первого topic убран:
   - если topic не передан в route, экран остается на cluster landing и не прыгает сразу в topic overview;
4. левый sidebar теперь держит:
   - список кластеров;
   - cluster-level navigation;
   - отдельный вход в `Настройки`;
5. `Consumer Groups` и `Brokers` получили честные shell placeholders для следующих bounded пакетов, без fake dashboard counters;
6. существующие topic/messages/produce/settings flows не сломаны и продолжают работать внутри нового shell.

#### 15.9.2. Topics screen redesign к table-first модели

Статус:

- реализовано

Что нужно сделать:

1. заменить текущую card-heavy topic catalog подачу на плотный table/list screen, ближе к `kafka-ui`;
2. сохранить search/filter и cluster-aware navigation;
3. сделать row-click navigation в topic details screen;
4. оставить metadata компактной:
   - partitions;
   - replication;
   - cleanup policy;
   - internal flag.

Что сделано:

1. topics catalog перестроен в плотный table-first screen вместо старой hybrid/card подачи;
2. сохранены cluster-aware filtering и route-driven navigation по текущему выбранному кластеру;
3. выбор topic теперь работает как row-click navigation в topic details `overview`, а не только как маленькая inline-ссылка в первой ячейке;
4. topic metadata сжата до компактного набора прямо в catalog:
   - partitions;
   - replication;
   - cleanup policy;
   - retention;
   - internal flag;
5. catalog получил screen-level header с count/filter/status контекстом, без возврата к dashboard counters и noise-blocks.

#### 15.9.3. Topic details page и tab shell

Статус:

- реализовано

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

Что сделано:

1. topic details больше не живут как inline continuation topics catalog:
   - при выбранном topic экран переключается в отдельный details shell;
2. введен явный tab contract на странице topic:
   - `Overview`;
   - `Messages`;
   - `Consumers`;
   - `Settings`;
   - `Produce`;
3. cluster-level settings отделены от topic tab model через отдельный pane `cluster-settings`, чтобы `settings` больше не был перегруженным route/state значением;
4. текущие `overview/messages/produce` переведены в новый details shell без потери уже реализованных bounded/safety ограничений;
5. topic-scoped `Consumers` вынесены из overview в отдельную tab, а `Settings` получили read-only topic configuration shell без admin actions;
6. route contract остался page-driven:
   - `cluster`;
   - `topic`;
   - `pane/tab`;
   при этом topic tabs теперь нормализуются только при реально выбранном topic.

#### 15.9.4. Consumer Groups screen и topic-consumers drill-down

Статус:

- реализовано

Что нужно сделать:

1. сделать отдельный cluster-level screen `Consumer Groups`;
2. сохранить topic-scoped вкладку `Consumers` как filtered/drill-down view;
3. показывать:
   - `group id`;
   - `state`;
   - `member count`;
   - `lag`;
4. partial-access и authorization failures должны оставаться section-level, а не валить весь Kafka shell.

Что сделано:

1. добавлен отдельный cluster-level screen `Consumer Groups` с собственным read-only metadata endpoint и table-first UI;
2. topic-scoped вкладка `Consumers`, введенная в `15.9.3`, сохранена как drill-down цель для cluster-level screen;
3. cluster-level rows теперь показывают:
   - `group id`;
   - `state`;
   - `member count`;
   - `topics count`;
   - `total lag`;
4. у каждой consumer group есть expandable topic summary с переходом в `topic -> Consumers` tab;
5. partial-access path сделан section-level:
   - list/offset failures не валят весь Kafka shell;
   - authorization/timeout errors сохраняются как local warning state в consumer-groups screen;
6. server/page routing синхронизированы:
   - добавлен `/api/kafka/consumer-groups`;
   - `/kafka` redirect теперь корректно прокидывает `section`.

#### 15.9.5. Brokers screen и read-only cluster operations shell

Статус:

- реализовано

Что сделано:

1. добавлен cluster-level read-only screen `Brokers`;
2. broker metadata теперь включает:
   - broker id;
   - host/port endpoint;
   - controller flag;
   - rack, если он доступен из Kafka node metadata;
3. отдельный `/api/kafka/brokers` добавлен без admin actions и destructive операций;
4. после `15.9.1–15.9.5` deprecated считаются:
   - pre-redesign placeholder branch для `Brokers`;
   - любые новые cluster-level summary/dashboard fragments внутри `Topics` или topic `Overview`, которые дублируют dedicated screens `Topics / Consumer Groups / Brokers`;
   - любые новые Kafka UI additions вне shell `KafkaShellSections / KafkaTopicDetailsSections / KafkaClusterConsumerGroupsSections / KafkaClusterBrokersSections`.

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
