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

1. [18. SQL-консоль: модернизация интерфейса под рабочий IDE/tool experience](/Users/kwdev/DataPoolLoader/BACKLOG.md)
2. [19. Kafka UI modernization mockups](/Users/kwdev/DataPoolLoader/BACKLOG.md)
3. [20. Главный экран: modernization mockup и launcher layout](/Users/kwdev/DataPoolLoader/BACKLOG.md)
4. [9. Операционная надежность long-running операций](/Users/kwdev/DataPoolLoader/BACKLOG.md)
5. [11. Repo-level архитектурная дисциплина](/Users/kwdev/DataPoolLoader/BACKLOG.md)
6. [16. Тестовая стратегия](/Users/kwdev/DataPoolLoader/BACKLOG.md)

## P0

Активных `P0`-задач сейчас нет.

Новый `P0` открывается только если появляется:

- build/runtime regression;
- safety-break в SQL/Kafka/long-running flow;
- нарушение архитектурного инварианта из [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md).

## P1

### 18. SQL-консоль: модернизация интерфейса под рабочий IDE/tool experience

Статус:

- основной UI реализован, ожидает визуального подтверждения; `18.10` отложено для отдельного обсуждения

Контекст:

- по итогам визуального review SQL-консоли текущий экран все еще ощущается как смесь landing/tool-dashboard и рабочего IDE-инструмента;
- SQL-консоль должна восприниматься как основной рабочий инструмент для повторяемой ручной инженерной работы, а не как демонстрационная страница;
- в scope входит только SQL-консоль и связанные SQL-экраны:
  - main SQL workspace;
  - source navigator;
  - query editor;
  - result pane;
  - execution/status/transaction states;
  - SQL execution history screen;
  - DB objects / inspector / editor navigation links, если они влияют на SQL-console flow;
- safety contracts из [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md) не меняются и не ослабляются;
- Monaco/autocomplete contracts из [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md) считаются частью текущей функциональности и не должны деградировать.
- детальный contract переноса макета в product UI зафиксирован в [SQL_CONSOLE_MODERNIZATION_IMPLEMENTATION_GUIDE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MODERNIZATION_IMPLEMENTATION_GUIDE.md).

Целевые требования:

1. первый экран SQL-консоли должен сразу показывать рабочую область:
   - source navigator;
   - SQL editor;
   - execution controls;
   - result pane;
2. верхний hero должен стать компактным tool header, без маркетингового визуального веса;
3. editor и results должны быть главными визуальными объектами страницы;
4. вторичные блоки `Шаблоны`, `Быстрые действия`, `Избранное`, `История запусков` не должны конкурировать с editor;
5. source navigator должен читаться как tree/list pane, а не как набор nested cards;
6. transaction/safety state должен быть заметен, но не раздувать layout;
7. текстовый слой должен быть единообразным:
   - преимущественно русский UI;
   - английские technical terms только там, где они являются доменными терминами;
   - убрать visible labels вроде `Tool window`, если они не помогают действию;
8. empty/loading/error/result states должны быть компактными и предсказуемыми;
9. mobile/tablet не являются главным сценарием, но layout не должен разваливаться;
10. visual tests пока не являются обязательным gate для redesign, но после стабилизации нужно вернуть targeted visual baselines.

Non-goals первой волны:

- не менять SQL execution lifecycle;
- не менять server/store state model без отдельной задачи;
- не трогать owner-token/heartbeat/rollback safety stream в рамках чистого UI redesign;
- не внедрять полноценный SQL LSP;
- не возвращать большой execution history block на основной экран.

#### 18.1. SQL console UI modernization requirements and target layout

Статус:

- реализовано, ожидает визуального подтверждения

Что нужно сделать:

1. зафиксировать финальный target layout для main SQL-console workspace;
2. определить, какие блоки остаются always visible, а какие уходят в secondary/collapsible zones;
3. описать ожидаемый порядок визуальной важности:
   - source navigator;
   - editor;
   - execution controls;
   - result pane;
   - status/transaction line;
   - secondary actions;
4. зафиксировать copy-language rules для SQL-консоли;
5. подготовить review-картинки для основных SQL screens до правок product UI;
6. зафиксировать, что вместо hero-шапки нужен компактный tool header с собственной SQL tool icon;
7. зафиксировать result display model:
   - result pane поддерживает три режима просмотра:
     - `Общий grid`;
     - `По источникам`;
     - `Diff`;
   - основной/default grid показывает объединенный результат по всем выбранным источникам;
   - колонка `source` обязательна для результата по нескольким источникам;
   - рядом должны быть статусы/фильтр по sources, чтобы не терять диагностику;
   - режим `По источникам` должен оставаться полноценным раздельным просмотром с выбором source;
   - режим `Diff` должен сохраниться как отдельный compare-view, а не смешиваться с обычной таблицей;
   - текущую функциональную таблицу результатов нужно сохранить как основу result grid:
     - per-source table view;
     - pagination;
     - wrap / nowrap;
     - autosize колонок;
     - ручное расширение/сужение колонок;
     - copy cell / copy row / copy column;
     - активная ячейка, строка и колонка;
     - row index column;
     - horizontal scroll для широких результатов;
   - result export должен быть доступен из result pane:
     - `Экспорт общий` выгружает один объединенный файл с обязательной колонкой `source`;
     - `Экспорт по sources` выгружает отдельный файл для каждого source;
     - до появления результата export actions видимы, но недоступны;
     - финальная export-семантика отложена в отдельное обсуждение: экспорт должен уметь выгружать полный набор данных, а не только ограниченный отображаемый result по `maxRowsPerShard`;
8. после согласования считать этот пункт входным design contract для следующих задач.
9. перед изменением product UI сверяться с [SQL_CONSOLE_MODERNIZATION_IMPLEMENTATION_GUIDE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MODERNIZATION_IMPLEMENTATION_GUIDE.md), чтобы не потерять существующие execution, Monaco, result grid, export и source-selection сценарии.

#### 18.2. Compact SQL tool header with dedicated tool icon

Статус:

- реализовано, ожидает визуального подтверждения

Контекст:

- пользователь после review подтвердил, что большой hero лучше убрать;
- при этом инструменту нужен сильный визуальный якорь, чтобы экран не стал сухим;
- SQL-консоль должна выглядеть как рабочий IDE/tool screen, а не landing/dashboard.

Что нужно сделать:

1. заменить hero-шапку SQL-консоли на compact tool header:
   - `Load Testing Data Platform`;
   - `SQL-консоль по источникам`;
   - navigation actions `На главную / Объекты БД / SQL-консоль`;
   - выбранные sources / workspace / autocommit / connection status;
2. не переносить execution controls в hero;
3. добавить красивую векторную SQL tool icon вместо текущего `SqlConsoleHeroArt`;
4. не добавлять новый competing header внутри workspace;
5. ниже шапки сделать плотный SQL workspace без лишнего повторения title/copy;
6. проверить, что editor и result pane остаются хорошо видимыми на первом viewport после compact header.

Что сделано:

1. основной `/sql-console` больше не использует большой `PageScaffold` hero;
2. добавлен компактный `SqlConsoleToolHeader`:
   - navigation actions `На главную / Объекты БД / SQL-консоль`;
   - `Load Testing Data Platform`;
   - `SQL-консоль по источникам`;
   - chips по выбранным sources, workspace, transaction/autocommit mode и connection status;
3. текущий `SqlConsolePageContent`, source selection, Monaco editor, execution callbacks и result pane оставлены без изменения;
4. старый hero art удален из основного SQL workspace в пользу компактной CSS-векторной SQL tool icon;
5. CSS ограничен SQL-specific styles и не меняет server/store contracts.

#### 18.3. Rebalance workspace hierarchy around editor and result pane

Статус:

- реализовано, ожидает визуального подтверждения

Контекст:

- над editor сейчас слишком много secondary UI;
- `Tool window`, шаблоны, quick actions и selector blocks визуально спорят с editor.

Что нужно сделать:

1. сделать editor и result pane основным вертикальным flow;
2. демотировать шаблоны, quick actions и query-library в secondary compact zone;
3. убрать лишние card shells вокруг controls, если они не несут отдельной ответственности;
4. оставить frequently used execution actions доступными без скролла;
5. не ломать текущие actions:
   - execute;
   - explain;
   - transaction;
   - export;
   - favorite/recent query flows.

Что сделано:

1. execution toolbar вынесен из query library и расположен над Monaco editor;
2. editor и result pane стали основным вертикальным flow workspace;
3. query library, favorite objects и Monaco shortcuts перенесены в secondary-зону ниже result pane;
4. settings toggles `Read-only / Autocommit` оставлены доступными над toolbar;
5. visible label `Tool Window` удален из SQL workspace secondary blocks;
6. существующие callbacks для execute, explain, transaction, export, recent/favorite queries и favorite objects сохранены.

#### 18.4. Source navigator as dense IDE-like tree/list pane

Статус:

- реализовано, ожидает визуального подтверждения

Контекст:

- source navigator уже отделен как pane, но визуально остается слишком card-heavy;
- группы и источники занимают много вертикального места, особенно при нескольких groups.

Что нужно сделать:

1. перевести groups/sources в более плотную tree/list model;
2. уменьшить nested card feeling и большие rounded containers;
3. сделать selected/partial/unchecked states читабельными без лишнего текста на каждой строке;
4. сохранить действия проверки connection и per-source status;
5. убрать загрузку `credential.properties` из рабочего source pane:
   - не показывать `credential.properties` или отдельный credentials block в рабочем SQL workspace;
   - при проблемах с credentials показывать только per-source warning/status и ссылку на экран настроек sources;
   - не показывать значения credentials и имя credentials file в рабочем SQL workspace;
6. source state messages показывать только когда они действительно важны:
   - error;
   - warning;
   - explicit runtime result.

Что сделано:

1. source navigator уплотнен в tree/list style без card-heavy shell вокруг каждой группы;
2. group selection labels сокращены до `Все / Часть / Нет`, source rows показывают status text только после явного статуса;
3. стандартный текст `Не проверено` и idle explanation про проверку подключений убраны из списка;
4. runtime connection-check summary оставлен компактным сообщением только после явной проверки;
5. блок загрузки `credential.properties` убран из рабочего SQL workspace;
6. текст `credential.properties` удален из общего not-configured сообщения SQL-консоли.

#### 18.5. Execution controls and transaction state redesign

Статус:

- реализовано, ожидает визуального подтверждения

Контекст:

- action toolbar сейчас выглядит как набор маленьких карточек;
- transaction/autocommit/safety state должен быть заметным, но не дробить рабочую область.

Что нужно сделать:

1. сгруппировать execution actions в один compact toolbar;
2. отделить primary actions от secondary actions;
3. привести visual language кнопок к tool UI:
   - меньше карточек;
   - больше toolbar controls;
   - понятные icon/text buttons только для реальных command actions;
4. transaction state вынести в compact status strip или dedicated safety strip;
5. не менять safety semantics manual transaction / commit / rollback.

Что сделано:

1. execution toolbar переведен из card-like action groups в compact tool row;
2. primary actions отделены от secondary actions:
   - `Весь script`;
   - `Текущий`;
   - `Выделение`;
   - `Стоп`;
3. secondary groups `EXPLAIN / Транзакция` остались отдельными compact controls без прежней card-heavy подачи;
4. command buttons получили readable text labels вместо icon-only affordance для основных действий;
5. `CommandGuardrail` и `ExecutionStatusStrip` объединены визуально в compact safety/status stack;
6. disabled conditions и callbacks для run/explain/commit/rollback не менялись;
7. export actions перенесены из execution toolbar в result pane в рамках 18.6.

#### 18.6. Result pane and empty states densification

Статус:

- реализовано, ожидает визуального подтверждения

Контекст:

- result pane уже стал лучше после предыдущей visual wave, но empty/result states еще выглядят как отдельные panels внутри panels;
- SQL-консоли нужен более строгий data-grid/tool-window вид.

Что нужно сделать:

1. уплотнить result toolbar и режимы просмотра:
   - `Общий grid`;
   - `По источникам`;
   - `Diff`;
2. сделать empty state компактным, без ощущения большой пустой card;
3. убедиться, что combined grid / per-source grid / diff / status views имеют общий visual grammar;
4. сохранить result navigator и per-source/per-statement semantics;
5. отдельно проверить long result/table overflow;
6. не удалить и не деградировать существующий diff режим.
7. не заменить текущую таблицу результатов на упрощенную mock-table:
   - сохранить feature parity текущего `SqlConsoleDataResultSections` / `SqlConsoleResultGridSupport`;
   - redesign может менять shell/toolbar placement, но не должен терять wrap/nowrap, autosize, resize, copy cell/row/column, active selection, row index, pagination и horizontal scroll;
   - если вводится combined grid, он должен использовать тот же table interaction model, добавляя только обязательную diagnostic column `source`;
8. вернуть export actions в result pane:
   - `Экспорт общий` -> single combined file with `source` column;
   - `Экспорт по sources` -> separate file per source;
   - финальный full-data export contract пока не реализовывать и не привязывать к текущему limited result snapshot;
   - для empty/running state кнопки должны быть disabled или явно недоступны.

Что сделано:

1. result data view переведен на явные режимы `combined/source/diff` с совместимостью старого `grid`;
2. default view для SQL result pane стал `Общий grid`;
3. `Общий grid` использует тот же table interaction model, что и source-grid, и добавляет diagnostic column `source`;
4. `По источникам` сохраняет текущую таблицу результатов: wrap/nowrap, autosize, resize, copy cell/row/column, active selection, row index, pagination и horizontal scroll;
5. `Diff` сохранен отдельным режимом без деградации текущей логики;
6. result navigator показывает режимы `Общий grid / По источникам / Diff`, source selector только в per-source режиме и pagination для combined/source;
7. export actions перенесены в result pane:
   - `Source CSV` для активного source snapshot;
   - `Экспорт по sources` для ZIP по source snapshot;
   - `Экспорт общий` показан disabled до отдельного full-data export contract из 18.10;
8. status bar и keyboard shortcuts синхронизированы с новыми режимами.

#### 18.7. SQL-related screens consistency pass

Статус:

- реализовано, ожидает визуального подтверждения

Контекст:

- SQL flow связан не только с `/sql-console`, но и с:
  - `/sql-console-objects`;
  - object inspector;
  - SQL execution history;
  - editor-native object navigation.

Что нужно сделать:

1. проверить, что DB objects и inspector визуально сочетаются с новым SQL-console shell;
2. сохранить inspector direct-load behavior;
3. проверить SQL execution history screen:
   - отдельный screen остается отдельным;
   - он не возвращается на main workspace;
   - list остается dense и readable;
4. унифицировать copy и action labels между связанными SQL screens;
5. не смешивать object catalog redesign с main workspace redesign без отдельного bounded package.

Что сделано:

1. SQL history и SQL objects приведены к единому `MLP Platform` branding без старого `Load Testing Data Platform`;
2. execution history screen сохранен отдельным экраном, без возврата списка запусков в main workspace;
3. history list уплотнен визуально, action labels унифицированы:
   - `Подставить SQL`;
   - `Повторить запуск`;
4. object inspector direct-load behavior сохранен: mismatch/search-result states остались, изменены только пользовательские тексты;
5. англо-русские UI-подписи на связанных SQL screens очищены там, где они не являются technical identifiers;
6. objects screen стал визуально ближе к новому SQL shell: меньше card-heavy shadow/radius, компактнее object cards и inspector tabs;
7. отдельный redesign object catalog не начинался, чтобы не расширять scope 18.7.

История выполненных packages `18.8–18.9` и `18.11` вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

#### 18.10. SQL export full-data contract discussion

Статус:

- отложено для отдельного обсуждения

Контекст:

- отображение результата в SQL-консоли ограничено настройками вроде `maxRowsPerShard`;
- пример: пользователь может поставить лимит `500` строк, но export должен выгружать полный набор данных;
- текущий result snapshot не должен считаться достаточным source of truth для финального export contract;
- вопрос нельзя решать как простую кнопку download в UI, потому что full export может быть long-running операцией с отдельными safety, timeout, streaming и file-size правилами.

Что нужно обсудить отдельно:

1. какие export modes нужны:
   - full combined export with `source` column;
   - full per-source export;
   - export only displayed rows как отдельный secondary action, если он нужен;
2. должен ли full export повторно выполнять SQL на сервере или использовать отдельный server-side cursor/streaming path;
3. какие лимиты нужны для full export:
   - timeout;
   - max rows;
   - max bytes;
   - disk temp file lifecycle;
4. как full export работает с manual transaction и safety mode;
5. какие статусы показывать в UI для long-running export;
6. какие server-side tests обязательны для больших результатов.

До решения:

- не считать current limited result snapshot финальной моделью export;
- в UI redesign можно зарезервировать место для export actions, но не реализовывать новую full-data export semantics.

### 19. Kafka UI modernization mockups

Статус:

- частично реализовано

Контекст:

- Kafka baseline уже реализован и включает cluster catalog, topics, topic overview, messages, produce, create topic, consumer groups, brokers и settings UI;
- текущий UI функционален, но после SQL-console modernization нужно аналогично проверить Kafka tool как рабочий IDE-like экран;
- визуальный ориентир остается `kafka-ui`, но с учетом локального характера инструмента и уже принятых safety contracts.
- согласованный HTML-макет находится в [design/kafka-modernization/index.html](/Users/kwdev/DataPoolLoader/design/kafka-modernization/index.html).
- implementation contract находится в [KAFKA_MODERNIZATION_IMPLEMENTATION_GUIDE.md](/Users/kwdev/DataPoolLoader/KAFKA_MODERNIZATION_IMPLEMENTATION_GUIDE.md).

Non-goals первой дизайн-волны:

- не менять Kafka server/store contracts;
- не менять `ui.kafka` source of truth;
- не добавлять delete topic, ACL, reset offsets, schema registry, live tailing или background consumers;
- не ослаблять `readOnly` block для produce/create-topic;
- не менять bounded message read contract `assign + seek / no commit`.

#### 19.1. Kafka modernization HTML mockups

Статус:

- согласовано

Что нужно сделать:

1. подготовить reviewable HTML-макет в [design/kafka-modernization/index.html](/Users/kwdev/DataPoolLoader/design/kafka-modernization/index.html);
2. показать основные Kafka screens:
   - cluster overview / topics catalog;
   - topic overview;
   - messages browser;
   - produce;
   - create topic;
   - consumer groups;
   - brokers;
   - cluster settings;
3. сохранить в макете обязательные функции:
   - cluster selector;
   - topic filter;
   - table-first topic catalog;
   - table-first messages view с details pane;
   - all partitions / selected partition controls;
   - read mode `LATEST / OFFSET / TIMESTAMP`;
   - read result summary `DONE / ms / bytes / messages consumed`;
   - JSON syntax highlighting только для валидного JSON;
   - plain-text fallback для non-JSON/invalid JSON;
   - structured produce editor;
   - create topic form;
   - TLS settings file picker affordances для JKS/PEM/cert/key fields;
4. не писать product UI code до review макетов.

Что зафиксировано:

- макет принят как целевое направление для следующего этапа;
- стартовый экран: `file:///Users/kwdev/DataPoolLoader/design/kafka-modernization/index.html?screen=topics`;
- review screens:
  - `?screen=topics`;
  - `?screen=topic`;
  - `?screen=messages`;
  - `?screen=produce`;
  - `?screen=create`;
  - `?screen=groups`;
  - `?screen=brokers`;
  - `?screen=settings`;
  - `?screen=empty`.

#### 19.2. Kafka compact tool shell and cluster rail

Статус:

- реализовано

Контекст:

- текущий `PageScaffold` и sidebar работают, но визуально Kafka tool должен стать плотнее и ближе к `kafka-ui`;
- cluster selector, section navigation и recent topics должны быть видимы без ощущения набора больших cards.

Что нужно сделать:

1. заменить hero-like Kafka header на compact tool header:
   - tool identity;
   - current cluster;
   - protocol;
   - bootstrap;
   - read limit/status chips;
2. переработать left rail:
   - cluster catalog;
   - cluster navigation;
   - recent/selected topics;
   - settings action;
3. сохранить route semantics:
   - selected cluster;
   - selected section;
   - selected topic;
   - active pane;
   - message read draft controls;
4. не переносить server/store orchestration в web components.

Что сделано:

1. `PageScaffold`/hero-like оболочка Kafka страницы заменена на compact product shell;
2. добавлен Kafka tool header:
   - breadcrumbs;
   - текущий cluster;
   - bootstrap;
   - protocol/read-write/max-read/bounded-read chips;
3. left rail переработан в плотный `Clusters / Navigation / Selected topic` layout;
4. сохранены существующие route semantics и callbacks;
5. изменения ограничены `ui-compose-web` page/components/styles, без изменения server/store contracts.

#### 19.3. Kafka topics catalog table-first view

Статус:

- реализовано

Контекст:

- current topics catalog уже table-first, но визуально остается card-shell heavy;
- макет фиксирует более плотный catalog с фильтром, status chips и row-click navigation.

Что нужно сделать:

1. выровнять `Topics` screen к согласованному table-first виду;
2. сохранить:
   - topic filter;
   - explicit reload;
   - internal topic badge;
   - selected topic badge;
   - partition/replication/cleanup/retention columns;
3. create-topic action оставить на topics screen, но не смешивать с settings;
4. не менять topic catalog API без отдельной причины.

Что сделано:

1. Topics screen вынесен из общего card-heavy shell в собственную compact table-first panel;
2. добавлены toolbar/status chips:
   - total topics;
   - user topics;
   - internal topics;
   - refreshing state;
3. сохранены topic filter draft и explicit `Обновить`;
4. сохранены create-topic action и inline create-topic form на topics screen;
5. таблица сохранила row/link navigation и колонки:
   - topic;
   - partitions;
   - replication;
   - cleanup;
   - retention;
6. server/store/API contracts не менялись.

#### 19.4. Kafka topic details and tabs redesign

Статус:

- реализовано

Контекст:

- topic details должен читаться как рабочая карточка topic, а не отдельная landing-section;
- tab set `Overview / Messages / Consumers / Settings / Produce` уже есть и должен сохраниться.

Что нужно сделать:

1. переработать topic header:
   - breadcrumbs back to topics;
   - topic name;
   - cluster/protocol/bootstrap meta;
   - refresh action;
2. сохранить tabs:
   - overview;
   - messages;
   - consumers;
   - settings;
   - produce;
3. overview сделать плотным:
   - summary metrics;
   - partition table;
   - partition load/offset side panel;
4. не маскировать partial consumer metadata errors как topic failure.

Что сделано:

1. topic header перепакован в compact panel:
   - breadcrumbs `Back to topics`;
   - topic name;
   - cluster/protocol/bootstrap meta;
   - partition/replication/cleanup chips;
   - `Refresh topic` action;
2. tabs `Overview / Messages / Consumers / Settings / Produce` сохранены и переведены на новый compact tab style;
3. overview заменен на плотный layout:
   - summary metrics;
   - partition table;
   - side panel `Partition load` с latest offsets by partition;
4. consumer metadata status показывается section-level chip и не блокирует topic overview;
5. server/store/API contracts не менялись.

#### 19.5. Kafka messages browser redesign

Статус:

- реализовано

Контекст:

- message browser является основной рабочей частью Kafka tool;
- его нельзя превращать в декоративный viewer: safety contract важнее визуального упрощения.
- визуальный ориентир для экрана чтения сообщений — Provectus Kafka UI:
  - table-first layout;
  - filters/seek controls above the table;
  - consume summary before result table;
  - selected message раскрывается внутри таблицы, а не отдельной карточной лентой.

Что нужно сделать:

1. сохранить bounded read semantics:
   - `assign + seek`;
   - no commit;
   - no background consumer session;
2. сохранить draft controls:
   - `selected partition / all partitions`;
   - `LATEST / OFFSET / TIMESTAMP`;
   - limit;
   - offset;
   - timestamp;
3. изменение controls не должно запускать read и не должно скрывать last successful result до явного `Читать сообщения`;
4. отрисовать result summary в стиле:
   - `DONE`;
   - duration ms;
   - bytes;
   - messages consumed;
5. сохранить table-first messages view:
   - expander column;
   - offset;
   - partition;
   - timestamp;
   - key;
   - value preview;
   - headers count;
6. selected message показывать через inline expanded row / inspector:
   - tabs `Value / Key / Headers / Metadata`;
   - pretty/raw/copy actions for payload;
   - metadata block with timestamp, partition and offset;
7. не возвращаться к карточному отображению списка сообщений;
8. JSON highlighting применять только при valid JSON;
9. non-JSON и invalid JSON показывать plain text без runtime error.

Что сделано:

1. message browser переведен на отдельный compact panel без общего card-heavy shell;
2. controls сохранены как draft-only:
   - scope;
   - partition;
   - mode;
   - limit;
   - offset;
   - timestamp;
   - explicit `Читать сообщения`;
3. result summary оформлен как Kafka UI-like строка:
   - `DONE`;
   - elapsed time;
   - consumed bytes;
   - messages consumed;
   - контекст последнего successful read;
4. messages table получила expander-колонку и preview-колонки:
   - offset;
   - partition;
   - timestamp;
   - key preview;
   - value preview;
   - headers count;
5. selected message раскрывается inline внутри таблицы;
6. inspector получил tabs:
   - `Value`;
   - `Key`;
   - `Headers`;
   - `Metadata`;
7. для payload сохранены `Pretty / Raw / Copy` actions;
8. valid JSON продолжает подсвечиваться, plain text и invalid JSON остаются plain text fallback;
9. server/store/API contracts не менялись.

История выполненных packages `19.6–19.8` вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

#### 19.9. Kafka modernization implementation guide

Статус:

- реализовано

Контекст:

- перед переносом макета в product code нужен короткий implementation contract, аналогичный SQL-console guide;
- цель: не потерять текущие Kafka behavior/safety contracts при visual rewrite.

Что нужно сделать:

1. создать `KAFKA_MODERNIZATION_IMPLEMENTATION_GUIDE.md`;
2. перечислить текущие компоненты и контракты:
   - `KafkaPage`;
   - `KafkaPageContent`;
   - `KafkaShellSections`;
   - topics/details/messages/produce/settings sections;
   - shared store support;
   - server routes/services;
3. зафиксировать phased migration plan;
4. добавить regression checklist;
5. связать guide с [KAFKA_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/KAFKA_FAILURE_SCENARIOS.md) и этой backlog section.

Что сделано:

1. создан [KAFKA_MODERNIZATION_IMPLEMENTATION_GUIDE.md](/Users/kwdev/DataPoolLoader/KAFKA_MODERNIZATION_IMPLEMENTATION_GUIDE.md);
2. зафиксированы текущие component/store/server boundaries;
3. описаны migration phases:
   - compact shell and rail;
   - topics catalog and topic overview;
   - messages browser;
   - produce and create-topic;
   - consumer groups, brokers and settings;
   - visual regression and cleanup;
4. добавлены state boundaries, file-level implementation map, regression checklist и stop criteria.

### 20. Главный экран: modernization mockup и launcher layout

Статус:

- реализовано

Контекст:

- главный экран должен быть входной точкой локальной инженерной платформы, а не маркетинговым landing page;
- текущая структура из трех групп остается правильной:
  - `Нагрузочное тестирование / Датапулы`;
  - `Работа с данными / Инструменты`;
  - `Справка`;
- после модернизации SQL и Kafka главный экран должен визуально соответствовать этим tool screens;
- HTML-макет для review находится в [design/home-modernization/index.html](/Users/kwdev/DataPoolLoader/design/home-modernization/index.html).

Что нужно сделать:

1. подготовить reviewable HTML-макет главного экрана до правок product UI;
2. заменить `Load Testing Data Platform` на осмысленное русское название платформы:
   - рабочий вариант: `Платформа инструментов тестирования микросервисов`;
   - не показывать отдельный описательный абзац под названием на главном экране;
   - не показывать hero-метки вроде `локальный режим`, `SQL`, `Kafka`, `датапулы`;
3. сохранить три строки/группы launcher:
   - `Нагрузочное тестирование / Датапулы`;
   - `Работа с данными / Инструменты`;
   - `Справка`;
4. сделать группы одинаковыми по визуальной модели:
   - compact group header;
   - в group header показывать только метку и название, без подробного описания;
   - равномерная сетка карточек;
   - понятная primary action;
   - без растянутой одной карточки и компактных остальных;
5. сохранить runtime mode semantics:
   - файловый режим;
   - DB режим;
   - переключатель режима должен находиться только в группе `Нагрузочное тестирование / Датапулы`;
   - понятное disabled-state для недоступного режима;
6. не добавлять на главный экран execution history, Kafka internals или SQL result details;
7. не менять product UI code до согласования макета.

Что зафиксировано:

- макет принят как целевое направление для реализации главного экрана;
- стартовый экран: `file:///Users/kwdev/DataPoolLoader/design/home-modernization/index.html?screen=home`;
- название платформы: `Платформа инструментов тестирования микросервисов`;
- верхняя зона без описательного текста и hero-меток;
- группы:
  - `Нагрузочное тестирование / Датапулы`;
  - `Работа с данными / Инструменты`;
  - `Справка`;
- переключатель runtime mode относится только к группе `Нагрузочное тестирование / Датапулы`.

Что сделано в product UI:

1. старый `Load Testing Data Platform` hero заменен compact title-card с названием `Платформа инструментов тестирования микросервисов`;
2. описательный hero-текст и hero-метки удалены;
3. главный экран перестроен в три launcher-группы:
   - `Нагрузочное тестирование / Датапулы`;
   - `Работа с данными / Инструменты`;
   - `Справка`;
4. runtime mode switch оставлен только в группе `Нагрузочное тестирование / Датапулы`;
5. карточки `Файловые модули`, `DB-модули`, `Очистка истории`, `SQL-консоль`, `Kafka-инструмент`, `Справка`, `О проекте` приведены к единой visual model;
6. изменения ограничены `ui-compose-web` home components/styles, без новых server/state contracts.

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

История реализованных пакетов `9.1–9.27` вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

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

История закрытых rule-sync пакетов `11.1–11.3` вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

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
   - дополнительные scenario-level coverage пакеты для long-running reliability stream;
   - targeted store/server tests для новых bounded subsystem changes.

История завершенных test packages `16.1–16.18` вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

## P3

### 21. Product и UX-задачи, не влияющие на архитектурную программу

Статус:

- отложено

Сюда относятся все задачи, которые:

- не повышают архитектурный порядок;
- не улучшают надежность;
- не уменьшают structural debt;
- не помогают cleanup legacy;
- не входят в активные subsystem stream-ы `P1/P2`.
