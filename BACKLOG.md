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

#### 18.8. SQL source catalog settings screen

Статус:

- выполнено

Контекст:

- текущий SQL workspace позволяет выбирать уже настроенные sources, но не добавлять и не редактировать подключения;
- source catalog сейчас приходит из `ui.sqlConsole.sourceCatalog` в редактируемом `ui-application.yml`;
- source of truth для SQL sources должен остаться в `ui-application.yml`, а экран настроек должен быть редактором этого конфига;
- после сохранения изменений SQL-консоль должна перечитать runtime catalog без рестарта приложения;
- загрузку `credential.properties` как runtime-content через рабочий SQL screen нужно убрать;
- credentials path и placeholder diagnostics должны жить на экране настроек sources;
- выбор/загрузка файла `credential.properties` переносится на экран настроек sources, чтобы источник credentials управлялся там же, где редактируются подключения;
- режим добавления source вручную без `credential.properties` нужен, но должен идти через secure secret provider:
  - пользователь может ввести `jdbcUrl`, `username`, `password` руками;
  - password по умолчанию маскируется в UI;
  - допускается явный toggle `Показать пароль` только для текущего ввода;
  - после сохранения password не возвращается обратно в browser response;
  - в основном UI показывается человекочитаемый статус вроде `saved in System keychain`, а не `${secret:...}`;
  - конкретный provider определяется платформой:
    - macOS -> Keychain;
    - Windows -> Credential Manager;
    - Linux -> Secret Service / libsecret-compatible storage;
  - если системный provider недоступен, UI должен явно показать unavailable-state и предложить fallback: `credential.properties`, session-only password или explicit restricted local secret store;
  - secret key и config placeholder показываются только в technical details;
  - в `ui-application.yml` сохраняется ссылка на secret, а не raw password.

Что нужно сделать:

1. добавить отдельный экран настроек sources, не смешивая его с рабочим SQL workspace;
   - основной экран должен быть без обучающих подсказок и длинных explanatory note-блоков;
   - показывать только поля, статусы, ошибки и действия;
   - technical details держать свернутыми или переносить в help/docs;
2. поддержать операции:
   - добавить source;
   - редактировать source;
   - удалить source с проверкой references из groups;
   - проверить подключение для одного source через явную кнопку `Тест подключения` в форме source;
   - проверить все sources через явную кнопку на панели списка sources;
3. поддержать редактирование groups:
   - name;
   - список sources;
   - предупреждения по пустым/битым references;
4. хранить изменения через отдельный server-side config persistence boundary, а не через route handler;
5. поддержать настройку credentials file как config-backed path:
   - разместить блок `credential.properties` на экране настроек sources, а не в рабочем SQL workspace;
   - редактировать `ui.defaultCredentialsFile`;
   - выбирать файл через local file chooser;
   - дать явные действия `Выбрать файл` и `Проверить`;
   - не дублировать credentials file status на рабочем SQL workspace;
   - показывать required / resolved / missing placeholder keys;
   - не показывать resolved secret values;
6. не показывать raw password values:
   - `jdbcUrl`, `username`, `password` должны поддерживать placeholders;
   - UI может показывать placeholder name, но не resolved secret;
7. после сохранения перечитывать SQL console info и обновлять source navigator без restart приложения:
   - текущий draft SQL не теряется;
   - active `RUNNING` / `PENDING_COMMIT` execution session не мутируется;
   - удаленный выбранный source помечается как unavailable до явного выбора другого source;
8. реализовать отдельный secure contract для режима `manual secret input` без `credential.properties`:
   - в форме source должен быть явный selector `Credentials mode`:
     - `System keychain` как default;
     - `Placeholders`;
   - набор credential fields меняется в зависимости от выбранного режима;
   - одновременно показываются только поля выбранного режима, без preview-карточек остальных режимов;
   - общими вне режима остаются только `name`, `driver` и `comment`;
   - `jdbcUrl`, `username`, `password`, `provider` и placeholder-status принадлежат конкретному режиму и не должны рендериться для неактивных режимов;
   - пользователь вводит `jdbcUrl`, `username`, `password` руками;
   - password field по умолчанию masked;
   - toggle `Показать пароль` влияет только на текущий draft input до сохранения;
   - UI не должен возвращать сохраненный password обратно в браузер;
   - source form после сохранения показывает только `password saved / missing / replace`, без значения;
   - preferred storage: system keychain через platform-specific provider;
   - поддержать provider abstraction:
     - `macos-keychain`;
     - `windows-credential-manager`;
     - `linux-secret-service`;
     - optional explicit fallback `restricted-local-secret-store`;
   - в обычной форме показывать `Password storage: System keychain`, `Provider: <platform provider>` и `Secret key: sqlConsole.sources.<source>.password`;
   - technical config value `${secret:...}` показывать только в collapsed advanced/details block;
   - в `ui-application.yml` сохранять ссылку вида `${secret:sqlConsole.sources.<source>.password}`;
   - raw password / plain-text credentials в `ui-application.yml` не поддерживать как UI-режим;
9. добавить targeted server/store tests на config persistence, hot reload и validation.

Что сделано:

1. добавлен первый vertical slice экрана `/sql-console-sources`:
   - отдельный Compose screen;
   - навигация из SQL workspace, history и objects;
   - формы редактирования `sources`, `groups` и `ui.defaultCredentialsFile`;
   - local file chooser для выбора `credential.properties`;
2. добавлен server-side boundary `UiSqlConsoleSourceSettingsService`:
   - route handlers оставлены thin;
   - сохранение идет через `UiConfigPersistenceService`;
   - после сохранения выполняется hot reload runtime SQL catalog через `SqlConsoleOperations.updateConfig`;
3. raw password values не возвращаются в browser response:
   - placeholder password показывается как reference;
   - non-placeholder/hidden password показывается только статусом `passwordConfigured`;
   - при сохранении hidden password можно оставить без изменений;
4. добавлены targeted tests:
   - persistence update для `sourceCatalog/groups/defaultCredentialsFile`;
   - скрытие non-placeholder password;
   - сохранение hidden password и hot reload SQL catalog;
5. добавлен второй vertical slice диагностики:
   - endpoint и UI action `Проверить` для `credential.properties`;
   - required / available / missing placeholder keys без раскрытия secret values;
   - endpoint и UI action `Проверить все sources` для draft-настроек;
   - single-source test теперь учитывает draft `ui.defaultCredentialsFile`;
   - targeted test на missing placeholder keys без утечки значений;
6. добавлен secure contract для режима `System keychain`:
   - server-side `UiSecretProvider` abstraction;
   - macOS Keychain provider через системную команду `security`;
   - explicit unavailable-state для Windows Credential Manager, Linux Secret Service и unsupported OS;
   - runtime resolution `${secret:...}` без сохранения raw password в `ui-application.yml`;
   - manual password input используется только как draft value и не возвращается в browser response после сохранения;
   - `Credentials mode` selector показывает только поля активного режима;
   - режим `Plain text` как отдельный UI/storage mode не поддерживается;
   - targeted tests на secret placeholder resolution и сохранение `${secret:...}` вместо plaintext;
7. улучшен UX source references и проверки подключений:
   - source card показывает группы, где используется source;
   - удаление source очищает references из groups в draft state, чтобы не оставлять битые ссылки;
   - результат `Проверить все sources` дополнительно показывается на соответствующих source cards;
   - добавлен shared-store test на удаление source и очистку group references;
8. добавлен collapsed `technical details` block для `System keychain`:
   - обычный UI остается без `${secret:...}`;
   - secret key и config value доступны только по явному раскрытию блока;
   - secret value / password не отображается.

#### 18.9. Restore targeted visual coverage after SQL redesign stabilizes

Статус:

- выполнено

Контекст:

- Playwright visual suite временно не используется как gate во время активной UI-перестройки;
- после стабилизации SQL redesign нужны targeted baselines именно для SQL flow.

Что нужно сделать:

1. вернуть targeted visual checks для:
   - main SQL console shell;
   - result pane empty/result state;
   - DB objects inspector;
   - SQL source catalog settings screen;
   - SQL execution history screen;
2. не обновлять snapshots на промежуточном нестабильном layout;
3. добавить только те baselines, которые защищают реальные UI contracts;
4. не использовать visual suite как блокер до завершения задач `18.2–18.8`.

Что сделано:

1. добавлен отдельный SQL-only Playwright visual suite:
   - `tools/sql-console-browser-smoke/tests/sql-console-visual.targeted.spec.mjs`;
   - suite не зависит от широких Kafka/Home/module visual baselines;
2. зафиксированы targeted baselines для:
   - main SQL console shell;
   - result pane empty state;
   - result pane combined grid;
   - result pane per-source grid;
   - result pane diff view;
   - DB objects inspector;
   - SQL source catalog settings screen;
   - SQL execution history screen;
3. API в visual suite замоканы, чтобы baselines не зависели от локальной БД и runtime данных;
4. добавлено ожидание внешних visual styles перед screenshot, чтобы Bootstrap CDN не давал нестабильные снимки;
5. добавлен npm shortcut:
   - `npm --prefix tools/sql-console-browser-smoke run test:sql-visual`;
6. проверка выполнена командой:
   - `SQL_CONSOLE_SMOKE_SKIP_DB_SETUP=1 PLAYWRIGHT_TEST_ARGS='tests/sql-console-visual.targeted.spec.mjs' ./scripts/run-sql-console-browser-smoke.sh`;
7. результат проверки:
   - `8 passed`.

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

#### 19.6. Kafka produce and create-topic tool forms

Статус:

- выполнено

Контекст:

- produce и create-topic уже реализованы функционально;
- redesign должен улучшить форму, не меняя write safety.

Что нужно сделать:

1. выровнять produce form:
   - partition override;
   - key;
   - structured headers rows;
   - высокий payload editor;
   - delivery result summary;
2. сохранить readOnly block до Kafka client call;
3. выровнять create-topic form:
   - topic name;
   - partitions;
   - replication factor;
   - cleanup policy;
   - retention.ms;
   - retention.bytes;
4. сохранить явные failure cases:
   - duplicate topic;
   - invalid partitions;
   - invalid replication;
   - authorization failure;
5. не добавлять delete topic / alter topic / reset offsets в этот пакет.

Что сделано:

1. produce form переведена на Kafka UI-like tool panel:
   - сохранены partition override;
   - сохранен key input;
   - сохранены structured headers rows;
   - payload editor увеличен по высоте;
   - delivery result summary отображается отдельной success-карточкой;
2. readOnly cluster продолжает блокировать produce UI до действия отправки;
3. create-topic form выровнена как compact tool form:
   - topic name;
   - partitions;
   - replication factor;
   - cleanup policy;
   - retention.ms;
   - retention.bytes;
4. failure cases и Kafka admin/produce contracts не менялись;
5. delete topic / alter topic / reset offsets не добавлялись.

#### 19.6.1. Kafka message expansion and full-width modernization shells

Статус:

- выполнено

Контекст:

- message browser должен вести себя как таблица с независимыми раскрываемыми строками;
- текущая механика одного selected message сворачивает предыдущую строку при раскрытии следующей;
- утвержденные модернизируемые экраны не должны ограничиваться max-width с большими боковыми полями.

Что нужно сделать:

1. заменить single-selected expansion на независимый набор раскрытых сообщений:
   - можно раскрыть несколько сообщений одновременно;
   - раскрытие нового сообщения не сворачивает уже открытые;
   - любое раскрытое сообщение можно свернуть обратно;
2. сохранить inline inspector внутри таблицы сообщений;
3. проверить, что изменение сообщения/режима чтения сбрасывает expansion только для нового result set;
4. убрать `max-width` ограничение у модернизируемых shell:
   - Kafka page;
   - Home page;
   - применять тот же full-width подход для следующих модернизируемых экранов;
5. увеличить высоту payload editor на produce screen;
6. не менять Kafka read API и bounded read semantics.

Что сделано:

1. message browser теперь хранит UI-only set раскрытых сообщений:
   - можно раскрыть несколько сообщений одновременно;
   - раскрытие нового сообщения не закрывает уже раскрытые;
   - каждое раскрытое сообщение сворачивается повторным нажатием;
2. inline inspector сохранен внутри таблицы;
3. expansion state сбрасывается при смене result set через ключ набора records;
4. Kafka и Home modernization shell переведены на full-width без `max-width: 1440px`;
5. produce payload editor увеличен до 620px на desktop и 420px на узких экранах;
6. обновлены cache-busters для `styles.css`, `05-home.css` и `45-kafka.css`;
7. Kafka read API и bounded read semantics не менялись.

#### 19.6.2. Kafka produce JSON highlighting

Статус:

- выполнено

Контекст:

- message inspector уже подсвечивает valid JSON при просмотре сообщений;
- produce payload editor должен подсвечивать JSON внутри самого редактора;
- отдельный preview увеличивает информационную нагрузку и не нужен;
- invalid JSON и plain text должны оставаться редактируемыми и не должны менять отправляемый payload.

Что нужно сделать:

1. заменить plain textarea payload на один JSON-aware editor:
   - подсветка должна быть внутри самого editor;
   - не показывать отдельный preview-блок;
   - invalid JSON и plain text должны оставаться редактируемыми;
2. добавить явную кнопку `Форматировать JSON`:
   - кнопка активна только для валидного JSON;
   - pretty-format применяется только по нажатию пользователя;
   - автоматом payload не форматируется;
3. не изменять фактический payload, который отправляется в Kafka, кроме явного действия форматирования;
4. сохранить высоту и рабочий размер payload editor;
5. не менять produce API и write safety.

Что сделано:

1. produce payload переведен на один Monaco JSON editor без отдельного preview;
2. подсветка JSON отображается внутри editor;
3. invalid JSON и plain text остаются редактируемыми в том же editor;
4. добавлена явная кнопка `Форматировать JSON`;
5. pretty-format применяется только по нажатию и только для валидного JSON;
6. produce API и write safety не менялись.

#### 19.7. Kafka consumer groups and brokers screens consistency

Статус:

- выполнено

Контекст:

- cluster-level `Consumer Groups` и `Brokers` должны визуально совпадать с новым Kafka shell;
- metadata может быть частично недоступна, и UI должен показывать это section-level status.

Что нужно сделать:

1. переработать consumer groups screen:
   - group list;
   - selected group details;
   - topic/partition lag table;
   - status badges for lag/metadata;
2. переработать brokers screen:
   - cluster summary;
   - broker cards/table;
   - controller role;
   - rack/endpoint metadata;
3. сохранить reload actions;
4. не трактовать partial ACL/metadata failure как полный cluster failure.

Что сделано:

1. Consumer Groups screen переведен на Kafka metadata panel:
   - compact header с reload action;
   - summary metrics по groups/members/topics/lag/warnings;
   - dense table с status badges;
   - inline detail view по group topics;
   - partition lag chips внутри detail table;
2. Brokers screen переведен на Kafka metadata panel:
   - compact header с reload action;
   - cluster summary metrics;
   - broker cards;
   - dense broker table;
   - controller/follower role badges;
3. reload actions сохранены;
4. partial metadata warnings остаются section/group-level UI state;
5. Kafka API и store contracts не менялись.

#### 19.8. Kafka settings UI redesign

Статус:

- выполнено

Контекст:

- settings UI уже редактирует `ui.kafka` config-backed catalog;
- после redesign он должен остаться properties-first editor, а не вторым state source.

Что нужно сделать:

1. выровнять settings screen с макетом:
   - compact toolbar;
   - cluster cards;
   - PLAINTEXT/SSL mode;
   - readOnly;
   - test connection;
2. сохранить config source of truth:
   - `ui.kafka` в `ui-application.yml`;
   - без hidden browser state;
3. сохранить TLS file picker affordances:
   - JKS truststore/keystore paths;
   - PEM CA certificate;
   - PEM client certificate;
   - PEM private key;
   - `.crt / .cer / .pem / .key / .jks / .p12 / .pfx` semantics;
4. PEM fields должны сохраняться как `${file:/abs/path}` placeholders;
5. raw secret values не должны попадать в browser-local state как отдельный source of truth;
6. показывать только поля выбранного material format:
   - для `JKS` показывать только соответствующие truststore/keystore file fields;
   - для `PEM` показывать только соответствующие certificate/key file fields;
   - для `Не задано` не показывать file fields;
7. material file fields не должны иметь ручной ввод path:
   - путь задается только через системный file chooser;
   - выбранное значение отображается read-only;
   - очистка значения выполняется явной кнопкой `Очистить`;
8. не показывать `default` как режим Kafka SSL material.

Что сделано:

1. SSL settings UI разбит на две компактные секции:
   - `Trust material`;
   - `Client material`;
2. для каждой секции есть только явный выбор material format:
   - `Не задано`;
   - `JKS / PKCS12`;
   - `PEM files`;
3. показываются только поля выбранного формата:
   - JKS показывает только соответствующий truststore/keystore file;
   - PEM показывает только соответствующие certificate/key files;
   - `Не задано` скрывает file fields;
4. material file fields стали read-only display:
   - выбрать файл можно только через системный file chooser;
   - ручной ввод path удален;
   - добавлена явная кнопка `Очистить`;
5. store нормализует hidden stale SSL fields перед save/test connection;
6. `default` как SSL material mode не отображается.

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

История реализованных пакетов `9.1–9.8` вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

#### 9.9. SQL async final snapshot control-path cleanup

Статус:

- выполнено

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

- выполнено

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

- выполнено

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

- выполнено

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

- выполнено

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

- выполнено

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

### 21. Product и UX-задачи, не влияющие на архитектурную программу

Статус:

- отложено

Сюда относятся все задачи, которые:

- не повышают архитектурный порядок;
- не улучшают надежность;
- не уменьшают structural debt;
- не помогают cleanup legacy;
- не входят в активные subsystem stream-ы `P1/P2`.
