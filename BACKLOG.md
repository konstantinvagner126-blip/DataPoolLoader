# DataPoolLoader Backlog

Текущий backlog сформирован после ревизии всех `.md`-документов проекта.

Исторические планы, roadmap, черновики и отдельный review-документ схлопнуты в этот backlog и архитектурные правила. В рабочем виде оставлены только:

- архитектурные правила;
- архитектурный review;
- backlog и backlog history;
- отдельные safety/reference документы SQL-консоли.

Главный фокус ближайшего этапа:

- хорошая архитектура;
- надежность;
- расширяемость;
- снижение стоимости изменений;
- cleanup legacy и structural debt.

Связанные документы:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md)
- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)
- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)

## Правила приоритизации

- `P0` — архитектурные и операционные задачи, которые прямо влияют на надежность и maintainability.
- `P1` — обязательный следующий слой после `P0`, без которого архитектурный порядок останется незавершенным.
- `P2` — среднесрочное усиление качества и developer experience.
- `P3` — отложенные продуктовые и UX-задачи, которые не должны обгонять архитектурную программу.

## P0

Текущий наивысший приоритет внутри `P0`:

- сначала выполнить [8. Нормализовать build baseline на Java 21](/Users/kwdev/DataPoolLoader/BACKLOG.md);
- до завершения этого пакета не начинать новые feature- или cleanup-циклы, кроме изменений, которые прямо нужны для green build на `Java 21`.

### 0. Архитектурная программа: вычистить `ui-server` как перегруженный boundary

Статус:

- реализовано

Цель:

- сделать `ui-server` тоньше, предсказуемее и дешевле в поддержке;
- прекратить разрастание `composition root + routes + state orchestration` в один knowledge-heavy слой.

Что нужно сделать:

- пересмотреть роли [UiServerContext.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerContext.kt) и [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt);
- продолжить thinning route handlers:
  - [CommonRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/CommonRoutes.kt)
  - [DatabaseRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/DatabaseRoutes.kt)
  - [SqlConsoleRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleRoutes.kt)
- запретить новый orchestration-код в routes;
- вынести или сократить knowledge-rich runtime branching там, где это возможно;
- продолжить замещение concrete-связок контрактами и support-слоями.

Что уже сделано:

- основная route/startup/page-route decomposition уже выполнена;
- `Server.kt`, `UiServerContext`, route groups и page routing выведены из монолитного состояния и разрезаны на support/facade слои;
- SQL-console, DB и common route flows уже не держатся на single-file orchestration;
- подробная история выполненной волны вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

Критерий завершения:

- `ui-server` снова выглядит как boundary/orchestration слой, а не как второй `core`;
- новые сценарии не требуют разрастания routes и `UiServerContext`.

### 1. Архитектурная программа: нормализовать error model

Статус:

- частично реализовано

Цель:

- убрать грубую и нечестную модель ошибок;
- разделить ошибки пользователя, конфликт состояния и внутренние сбои.

Что нужно сделать:

- убрать практику `Throwable -> 400` из [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt);
- ввести минимум три класса ошибок:
  - validation/business error;
  - conflict/state error;
  - internal error;
- привести transport-слой к понятным и стабильным ответам;
- убрать места, где UI не может понять, это ошибка сценария или баг сервера.

Что уже сделано:

- transport-слой больше не маскирует все исключения под `400`;
- введены осмысленные `404 / 409 / 503 / 500` для ключевых server-path’ов;
- появились доменные not-found/conflict исключения вне `server`-пакета;
- для DB sync-run details и SQL-console export добавлены отдельные not-found сценарии вместо `500`;
- ключевые сценарии FILES / DB / SQL-console закреплены серверными тестами.

Критерий завершения:

- сервер перестает маскировать внутренние дефекты под пользовательские ошибки;
- UI может по типу ответа различать, что именно произошло.

### 2. Архитектурная программа: нормализовать state model

Статус:

- частично реализовано

Цель:

- развести runtime state, persisted history, UI preferences и operational config;
- сделать состояние проекта классифицированным и предсказуемым.

Что нужно сделать:

- провести ревизию persisted state моделей в `ui-server`;
- зафиксировать для каждого значимого state:
  - source of truth;
  - recoverable state;
  - cache;
  - UI preference;
  - operational history;
- убрать смешение UI preferences и operational state;
- запретить добавление новых глобальных mutable service без архитектурного justification;
- отдельно разобрать:
  - SQL console state;
  - run state;
  - cleanup/retention state;
  - runtime mode/config state.

Что уже сделано:

- run-state и credentials уже разведены по отдельным persisted моделям;
- persisted state SQL-консоли уже разрезан на `workspace / library / preferences`, а combined legacy state переведен в migration-only роль;
- source-of-truth для UI preferences и operational content стал заметно явнее;
- подробная история выполненной волны вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

Критерий завершения:

- новые и существующие состояния можно классифицировать без двусмысленности;
- у проекта появляется ясная карта источников истины.

### 3. Архитектурная программа: разрезать giant UI files

Статус:

- реализовано

Цель:

- снизить стоимость изменений во frontend;
- превратить крупные Compose-экраны в набор feature-блоков.

Что уже сделано:

- top-offender page/store/support файлы в `ui-compose-web` и `ui-compose-shared` переведены в shell/bindings/effects/content или facade/support модель;
- экраны `sql console`, `module editor`, `module runs`, `module sync`, `run history cleanup` и соседние shared stores доведены до reviewable-состояния;
- giant page/section/store файлы больше не являются основной точкой UI-долга;
- stop-condition этой волны зафиксирован: второй круг распила без нового архитектурного эффекта запрещен.

История:

- подробная хронология вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

Критерий завершения:

- новые экраны не растут как монолиты;
- текущие giant-файлы разбиты до состояния, когда их можно нормально ревьюить и менять локально.

### 4. Архитектурная программа: разрезать `styles.css`

Статус:

- частично реализовано

Цель:

- убрать giant CSS-file debt;
- сделать стили обозримыми и локализованными по подсистемам.

Что нужно сделать:

- провести инвентаризацию текущего [styles.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles.css);
- разделить стили минимум на логические группы:
  - foundation/base;
  - layout;
  - module editor;
  - module runs;
  - sql console;
  - home/landing;
  - maintenance screens;
- удалить мертвые и дублирующие классы;
- запретить добавление новых крупных блоков в один общий CSS-файл без явной причины.

Что уже сделано:

- giant [styles.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles.css) переведен в import-manifest;
- порядок каскада сохранен через ordered CSS-chunks:
  - [00-foundation.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/00-foundation.css)
  - [05-home-help.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/05-home-help.css)
  - [10-config-editor.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/10-config-editor.css)
  - [20-sql-console.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/20-sql-console.css)
  - [30-run-history.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/30-run-history.css)
  - [35-sync-maintenance.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/35-sync-maintenance.css)
  - [40-sql-results.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/40-sql-results.css);
- первые дубли селекторов и нелогичные cross-feature зависимости уже убраны;
- ресурсы проходят packaging и попадают в `ui-server` bundle.

История:

- подробная хронология вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

Критерий завершения:

- стили проекта разбиты по подсистемам;
- giant CSS-файл больше не является точкой концентрации UI-долга.

### 5. Архитектурная программа: провести cleanup legacy и мусора

Статус:

- частично реализовано

Цель:

- убрать из проекта код, который остался от старых решений, больше не нужен или дублирует актуальную логику.

Что нужно сделать:

- провести тщательный анализ dead code / legacy branches / старых helper-слоев;
- убрать мертвые компоненты, неиспользуемые модели, старые переходные адаптеры и дубли;
- отдельно проверить:
  - legacy куски в `ui-compose-web`;
  - неиспользуемые CSS-классы;
  - устаревшие persisted поля;
  - старые route / API ветки;
  - лишние support-слои, если они больше не дают ценности;
- cleanup делать только после явного анализа, а не слепым удалением.

Что уже сделано:

- legacy route/state/persistence хвосты уже сокращены:
  - migration-only и deprecated ветки SQL-консоли и runtime-state приведены к явной роли;
  - удалены пустые aliases, thin wrappers и transitional adapters;
  - общие file/path/state helper-ы вынесены в shared support-слои вместо параллельных реализаций.
- web/shared UI cleanup уже дал основной эффект:
  - убраны крупные duplicate helper-islands в `sql_console`, `module_editor`, `module_runs`, `module_sync`, `run_history_cleanup`;
  - удалены одноразовые support-файлы там, где они не давали архитектурной ценности;
  - screen/store/page layers больше не держат явный historical noise ради старых решений.
- server-side cleanup уже дошел до run/config/module/sqlconsole кластеров:
  - `ui.config`, `run manager`, `output retention`, `run history`, `db module`, `config form`, `module registry` и соседние слои разрезаны на более узкие facade/support responsibilities;
  - duplicated response/query/persistence/mutation code существенно сокращен;
  - основная structural debt этого блока уже вынесена в историю.
- cleanup-волна остается активной только для тех мест, где есть реальный duplicate/legacy debt, а не просто желание еще раз дробить уже нормализованные файлы.

История:

- подробная хронология вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

Критерий завершения:

- в проекте уменьшено число legacy-paths;
- кодовая база чище, проще и с меньшим количеством “исторических хвостов”.

### 6. SQL-консоль: единая программа развития

Статус:

- частично реализовано

Источник:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)
- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)

Цель:

- вести SQL-консоль как самостоятельную подсистему, а не как набор разрозненных экранных задач;
- выстроить единый порядок работ: сначала safety и ownership, затем object inspector и UI control-flow, затем Monaco и regression coverage;
- не смешивать в backlog архитектурную основу, recovery, object browser, Monaco и UX в несвязанные куски.

Что уже есть:

- отдельные документы по failure scenarios и Monaco autocomplete;
- async execution;
- manual `Commit / Rollback`;
- object browser;
- IDE-like UX;
- отдельные support/contract-слои в backend.
- server-side ownership contract:
  - `ownerSessionId`, `ownerToken`, lease metadata;
  - heartbeat в `RUNNING` и `PENDING_COMMIT`;
  - automatic rollback по owner loss и hard TTL;
  - refresh recovery, token fencing и explicit owner release.

Единая последовательность:

1. Фаза A. Execution safety и ownership recovery:
  - довести SQL execution lifecycle до состояния, где безопасность обеспечивается server-side механизмами, а не живой вкладкой браузера;
  - закрыть remaining recovery gaps вокруг `refresh / duplicate tab / network loss / server restart / release`;
  - закрепить route/manager tests на ownership, rollback и recovery как обязательную часть подсистемы.
2. Фаза A2. Multi-tab workspaces и parallel execution:
  - цель фазы: превратить SQL-консоль из single-workspace/single-execution режима в локальный multi-tab инструмент с изолированными рабочими пространствами и безопасным параллельным запуском;
  - эта фаза не считается чисто UI-доработкой: она требует новой execution/state model и отдельного recovery/safety контракта.
3. Фаза A3. Global source groups и selection model:
  - цель фазы: ввести глобальные группы источников как часть конфигурации приложения и перевести SQL-консоль на group-aware модель выбора source без потери явного source-level контроля.
4. Фаза B. `sql-console-objects` и object inspector:
  - цель фазы: превратить object browser из search-card экрана в полноценный DBA-like inspector flow без превращения его во вторую SQL-консоль.
5. Фаза C. Основной экран консоли: интерфейс, читаемость и control-flow UX:
  - после object inspector привести main SQL screen к более читаемой и безопасной operational model;
  - убрать дублирующие presentation-формы и слабые execution/status affordance.
6. Фаза D. Monaco hints и autocomplete:
  - только после стабилизации object/search contract и main control-flow;
  - развивать Monaco по staged модели без тяжелого LSP-стека.
7. Фаза E. SQL-console-specific regression coverage и invariants:
  - закрепить подсистему screen-level smoke coverage, server-side scenario tests и явными архитектурными инвариантами;
  - поддерживать SQL-консоль как отдельную инженерную программу, а не как временный набор UX-правок.
8. Фаза F. IDE-like enhancements:
  - после формального закрытия safety/recovery/object browser/main UX/Monaco baseline;
  - усиливать SQL-консоль только теми функциями, которые реально приближают ее к IDE/DBA-tool опыту без тяжелого LSP и без размывания execution architecture.

Фаза A. Execution safety и ownership recovery:

- owner session и owner token для execution session;
- heartbeat во время `RUNNING` и `PENDING_COMMIT`;
- lease timeout и hard TTL;
- automatic rollback при потере владельца;
- системные статусы rollback по timeout/lost owner;
- tests на:
  - close tab;
  - refresh;
  - network loss;
  - duplicate tab;
  - server restart.
- SQL execution ownership contract:
  - `Commit / Rollback / Cancel` не должны работать без подтвержденного owner token;
  - stale token и duplicate tab не должны сохранять control-path.
- SQL execution unload/release flow:
- browser-side best-effort release через `pagehide` / `sendBeacon` допустим только как дополнительная защита;
  - server-side safety должна работать и без него.

Фаза A2. Multi-tab workspaces и parallel execution:

- текущий single-execution/single-workspace режим нужно заменить на явную multi-tab модель без размывания safety-инвариантов;
- первой целевой формой multi-tab режима считаются именно отдельные browser tabs, а не внутренний tab-strip внутри одного page-shell;
- реализован первый пакет этой фазы:
  - `workspaceId` уже стал явной частью browser-tab workspace model;
  - action `Открыть новую вкладку консоли` клонирует текущий persisted context в новый `workspaceId` и открывает новую browser-вкладку;
  - `/sql-console` и `/sql-console-objects` теперь сохраняют и восстанавливают один и тот же tab-scoped workspace context по `workspaceId`, без потери состояния при переходе между экраном консоли и object browser;
  - selected groups / selected sources уже persistятся per workspace и не перетираются между browser-вкладками;
  - server-side execution model больше не singleton: `AUTO_COMMIT` execution могут идти параллельно;
  - для manual transaction на первом этапе введен safe single-flight policy:
    - нельзя запускать вторую manual transaction, пока другая manual transaction еще `RUNNING`;
    - нельзя запускать новую manual transaction, пока другая вкладка держит `PENDING_COMMIT`;
- ввести отдельное понятие `console workspace` / `tab workspace` для SQL-консоли:
  - draft SQL;
  - selected groups;
  - selected source set;
  - page size / transaction mode / strict safety context;
  - локальный UI execution context;
  - эти данные не должны больше безусловно перетираться между вкладками через один shared workspace-state файл;
- зафиксировать product contract новой вкладки:
  - action `Открыть новую вкладку консоли` открывает именно новую browser-вкладку с новым `workspaceId`;
  - новая вкладка по умолчанию клонирует текущий рабочий контекст:
    - `draft SQL`;
    - selected sources;
    - `transaction mode`;
    - `strict safety`;
  - новая вкладка не клонирует текущий execution, owner token и pending transaction;
  - сценарий `пустая новая вкладка` допустим только как отдельное явное follow-up расширение, а не как неявное поведение первой версии;
- разделить shared и isolated state:
  - tab-scoped: `draft SQL`, selected groups, selected sources, page size, current execution context;
  - shared between tabs: `recent queries`, `favorite queries`, `favorite objects`;
- перестроить server-side execution model:
  - вместо одного `activeExecution` нужен явный registry независимых execution session;
  - ownership, heartbeat, release, rollback TTL и recovery должны работать per execution session, а не на весь экран целиком;
  - transport/API contract должен уметь возвращать и адресовать несколько execution session без скрытой зависимости от singleton state;
- отдельно зафиксировать policy для параллельных manual transaction сценариев:
  - на первом этапе разрешить параллельные `AUTO_COMMIT` execution в разных вкладках;
  - на первом этапе не разрешать несколько `PENDING_COMMIT` одновременно;
  - если уже есть одна незавершенная manual transaction session, сервер обязан блокировать следующую явно и честно;
  - canonical user-facing conflict message для этого сценария:
    `В другой вкладке SQL-консоли есть незавершенная транзакция. Сначала выполните Commit или Rollback в той вкладке. Пока транзакция не завершена, запуск новой ручной транзакции недоступен.`
  - расширение до нескольких параллельных `PENDING_COMMIT` возможно только отдельным safety-review и отдельным backlog-пакетом;
- в web/shared слоях перейти от single current execution view к явному tab/workspace-scoped current execution contract;
- action `Открыть новую вкладку консоли` уже добавлена:
  - она открывает новый SQL-console tab/workspace, а не просто дублирует текущий URL;
  - текущий implementation contract: `clone current context` в новую browser-вкладку;
  - warning `Браузер заблокировал открытие новой вкладки SQL-консоли.` допустим только при реальном blocked/null результате открытия; успешное открытие новой вкладки не должно оставлять ложный banner в исходной вкладке;
- закрепить multi-tab сценарии отдельными tests:
  - different tabs with different drafts;
  - parallel auto-commit execution;
  - ownership isolation между вкладками;
  - refresh/reopen одного tab workspace;
  - close одной вкладки без потери control-path другой.
- дополнительный hardening этого пакета уже закреплен regression coverage:
  - route-level `409 Conflict` с canonical user-facing message для попытки открыть вторую manual transaction при чужом `PENDING_COMMIT`;
  - manager-level invariant: release/cancel завершения одной execution session не должны затрагивать другую execution session в соседнем workspace.
  - route-level same-tab recovery после `release` и route-level сценарий `close/release` одной вкладки без потери `cancel/control-path` у соседней вкладки;
  - manager-level owner-loss timeout в одном workspace не должен ломать `current snapshot` и `control-path` в другом workspace.

Фаза A3. Global source groups и selection model:

- текущая волна этой фазы реализована:
  - старый `flat sources + separate sourceGroups` контракт заменен на `group-first` модель;
  - YAML contract:
    - `groups`: список групп с `name` и списком `sources`;
    - `sourceCatalog`: канонический каталог всех источников с полным connection config;
  - полный source config нельзя дублировать внутри групп;
  - один source по-прежнему может входить в несколько групп;
- UI source selection теперь иерархический:
  - сначала показываются группы;
  - группа раскрывается и показывает входящие в нее source;
  - group checkbox работает как bulk-select/bulk-unselect для своей группы;
  - source checkbox внутри группы остается явным source-level control;
- source, не входящие ни в одну группу, отображаются в вычисляемой группе `Без группы`;
  - `Без группы` не нужно явно описывать в YAML;
  - это synthetic UI/runtime group для всех source из `sourceCatalog`, которые не упомянуты ни в одной группе;
- source selection UI не уходит в пустое состояние:
  - если runtime `groups` по какой-то причине пусты, экран должен хотя бы показать synthetic fallback из `sourceCatalog`;
  - если не настроен даже `sourceCatalog`, нужно показывать явный empty state, а не пустую панель;
- группы источников считаются частью глобальной конфигурации приложения, а не локальным persisted state SQL-консоли;
- конфигурация должна уметь явно описывать:
  - `group name`;
  - список `sourceNames`, входящих в группу;
- типовые группы вроде `dev`, `ift`, `lt` должны задаваться в конфиге и читаться сервером как runtime contract;
- один source может входить в несколько групп;
- selection model SQL-консоли должна поддерживать одновременно:
  - выбор одной или нескольких групп;
  - выбор отдельных source внутри групп;
  - выбор отдельных source вне групп, если это допустимо текущей конфигурацией;
- для multi-tab режима выбранные группы и выбранные source должны запоминаться индивидуально для каждой вкладки/workspace, а не как один глобальный SQL-console selection state;
- source of truth для execution по-прежнему остается список выбранных source, а не список выбранных групп;
- выбор нескольких групп должен означать union всех источников из этих групп;
- UI должен уметь честно показывать состояние группы:
  - `all selected`;
  - `partially selected`;
  - `not selected`;
- execution, connection status, diff и safety по-прежнему считаются по source, а не по группе;
- object browser и основной экран SQL-консоли должны использовать один и тот же group-aware source selection contract;
- tab/workspace-scoped persisted selection для `selectedGroups + selectedSources` уже реализован поверх `workspaceId` и отдельного workspace-state файла;
- working context strip теперь честно отражает:
  - выбранные группы;
  - ручные добавления source;
  - manual exclusions;
  - synthetic `Без группы` selection;
- первая версия этой модели не включает:
  - вложенные группы;
  - group-level permissions/policies;
  - auto-generated группы без явной конфигурации.
- отдельного внутреннего хвоста у `A3` больше нет; дальнейшее развитие групп теперь идет только в рамках `A2` multi-tab workspaces и execution model.

Фаза B. `sql-console-objects` и object inspector:

- первый implementation package этой фазы должен включать:
  - удаление heavy inline metadata из search results там, где она мешает scan-and-pick сценарию;
  - введение отдельного object inspector flow для выбранного объекта;
  - сохранение одной явной action-кнопки `Открыть SELECT в консоли` вместо встроенного data preview.
- уже реализован первый package этой фазы для текущего search contract:
  - search result cards больше не рендерят columns / indexes / definition inline;
  - выбранный объект открывается в отдельном inspector panel;
  - inspector уже оформлен как type-aware tabbed UI для metadata, которая реально приходит из текущего backend search;
  - `Открыть SELECT в консоли` оставлен как явное действие, а не встроенный preview block;
  - расширение inspector-а до полного DBeaver-like coverage и новых object types остается отдельным следующим пакетом, а не считается уже закрытым.
- реализован следующий package этой фазы:
  - search contract стал легким: heavy metadata больше не приезжает в `/objects/search`, а грузится отдельным inspector-запросом;
  - появился lazy route `/api/sql-console/objects/inspect` и отдельный shared/web/server inspector contract;
  - PostgreSQL object browser теперь умеет искать и инспектировать `TABLE`, `VIEW`, `MATERIALIZED_VIEW`, `INDEX`, `SEQUENCE`, `TRIGGER`, `SCHEMA`;
  - inspector стал реально type-aware по содержимому вкладок: `columns / indexes / constraints / triggers / sequence / schema / ddl` зависят от типа объекта и доступной metadata;
  - search results и inspector больше не смешивают ответственности в одном transport contract.
- оставшийся хвост этой фазы:
  - не добивать inspector вторым кругом purely cosmetic split-ами;
  - идти только в реально недостающую metadata глубину: `comments / dependencies / grants / richer DDL`, если это дает новый DBA-сигнал;
- после стабилизации object inspector переходить в Фазу C, а не бесконечно расширять объектный экран.
- объектный экран должен оставаться search-first инструментом, а не превращаться в тяжёлый каталог со стартовой полной загрузкой схем;
- source selection, search query, search action и summary последнего результата должны считываться из верхнего блока без прокрутки и без визуальной конкуренции со secondary panels;
- `Текущий объект` и `Избранные объекты` должны оставаться вторичным контекстом и не вытеснять основной search/result flow ниже первого экрана;
- grouping по source должен ясно показывать: сколько объектов найдено, был ли truncation, была ли source-level ошибка и есть ли exact match по deep-link;
- object actions должны иметь явную иерархию:
  - primary action для открытия object inspector или SQL-действия;
  - secondary action для вспомогательных действий вроде `COUNT(*)`;
  - tertiary action для favorites/metadata.
- object result presentation должен быть заточен под быстрый scan-and-pick сценарий: сначала identity и тип объекта, затем контекст, затем только действительно полезные details;
- source-level ошибка по одному источнику не должна перекрывать успешные результаты по другим источникам и не должна ломать общий search session UX;
- contract inspectable object types должен быть явным:
  - первая волна inspector support: `TABLE`, `VIEW`, `MATERIALIZED_VIEW`, `INDEX`, `SEQUENCE`, `TRIGGER`, `SCHEMA`;
- для `TABLE/VIEW/MATERIALIZED_VIEW` inspector должен уметь показывать их schema metadata и связанные object details;

## P2

### 20. Информационные страницы и project metadata

Статус:

- частично реализовано

Что нужно сделать:

- держать отдельные информационные страницы проекта как compose-screen routes, а не плодить новые static HTML-ветки без причины;
- добавить страницу `О проекте` с кратким описанием проекта и явным списком разработчиков;
- на карточках разработчиков добавить декоративные CSS-анимации двух черных спортивных мотоциклов, которые встают на заднее колесо и едут;
- обеспечить прямой route `/about` и видимый вход на страницу с главного экрана;
- список разработчиков должен быть частью page contract, а не скрытым текстом в footer.

Что уже сделано:

- есть отдельный экран справки `/help`, но он пока живет отдельно от compose-screen flow;
- home-screen уже является точкой входа для сервисных и справочных страниц.

Критерий завершения:

- у проекта есть доступная страница `О проекте`;
- на ней явно указаны разработчики и их engineering aliases;
- route и навигация покрыты server-side redirect test.
  - для `INDEX` inspector нужен как отдельный сценарий просмотра DDL и привязки к таблице/колонкам;
  - для `SEQUENCE` inspector нужен как отдельный сценарий просмотра DDL и параметров последовательности (`increment`, `min/max`, `start`, `cache`, `cycle`, ownership/dependency к таблице/колонке там, где это доступно);
  - для `TRIGGER` inspector нужен как отдельный сценарий просмотра DDL, target table/view, `timing`, `events`, `enabled/disabled` state, связанной trigger function/procedure и других trigger metadata там, где это реально доступно;
  - trigger metadata должно быть видно и внутри inspector-а таблицы/представления как связанный объект, и в самостоятельном inspector flow при прямом открытии trigger;
  - для `SCHEMA` inspector нужен как отдельный сценарий просмотра schema DDL, owner, comments, privileges и агрегированной metadata по содержимому схемы без полной eager-загрузки всех вложенных объектов;
  - `constraints/keys` в первой волне считаются частью inspector-а таблицы/представления, а не отдельным search object type;
  - отдельная поддержка `FUNCTION`, `PROCEDURE` и других object types идет только как отдельное расширение introspection/search contract, а не молчаливо.
- просмотр объекта должен быть ближе к DBeaver inspector flow, а не к длинной карточке в выдаче:
  - search results остаются легкими и не разворачивают полный metadata dump inline;
  - углубленный просмотр открывает отдельный inspector state для выбранного объекта;
  - inspector не является одним универсальным полотном: отображаемая информация и composition экрана должны зависеть от `objectType`;
  - основной visual container inspector-а должен быть tabbed UI в духе DBeaver, а не длинная вертикальная карточка;
  - набор вкладок должен быть type-aware: для разных object types показываются разные tab groups и разный порядок приоритета;
  - inspector как минимум показывает структурированные блоки `columns`, `DDL/definition`, `indexes`, `constraints/keys`;
  - inspector не показывает row data/sample data как часть object inspection сценария;
  - целевой ориентир для inspector-а: максимально полный schema metadata view в духе DBeaver, включая `comments`, `triggers`, `dependencies`, `grants/privileges` и прочие объектные metadata там, где это реально доступно через introspection;
  - DDL и длинные SQL definitions должны показываться в удобном read-only SQL viewer, предпочтительно Monaco-native там, где это улучшает чтение;
  - metadata inspector должен загружаться lazy/on-demand и не утяжелять search response для всех найденных объектов сразу;
  - deep-link на объект должен приводить именно к object inspector сценарию, а не только подсвечивать карточку в длинном списке;
  - для табличных данных достаточно одной явной action-кнопки `Открыть SELECT в консоли`; отдельный встроенный data preview для object browser не нужен.

Фаза C. Основной экран консоли: интерфейс, читаемость и control-flow UX:

- реализовано в первой волне:
  - source/shard status оставлен в одной primary presentation-форме: таблица без карточного дубля;
  - внешний page-блок `Script outline` удален; cursor-based flow для `Текущий` statement остался editor-native;
  - source connection indicators теперь обновляются не только после ручной проверки, но и по факту реального SQL execution на участвовавших source;
  - добавлен `working context strip`: source selection, transaction mode, strict safety, page size и credentials state читаются с одного взгляда, без возврата в sidebar;
  - toolbar переведен в явную action hierarchy `подготовка / выполнение / транзакция / экспорт`; `Run / Cancel / Commit / Rollback` больше не прячутся в визуально равноправном icon-only наборе;
  - execution status strip усилен для `pending commit`, `auto rollback by timeout/owner loss` и `cancel requested`, чтобы safety-состояния читались как operational state, а не как абстрактный summary;
  - query library теперь показывает summary по history/favorites/object shortcuts и подсказывает следующий шаг для выбранного query без перехода в editor/sidebar;
  - favorite objects переведены в более явную action hierarchy: primary SQL-действие, затем вспомогательные действия, затем inspector/remove;
  - statement/shard/page navigation собрана в один result navigator вместо разнесенных по экрану блоков и длинного pagination wall;
- shortcut contract вокруг Monaco стал явным: экран показывает, какие hotkeys живут editor-local и когда они реально активны;
- Monaco получил editor-local hotkeys для result navigation (`statement/source/page`, `data/status`) без глобальных browser handlers;
- library-actions `Подставить` теперь возвращают фокус в Monaco, а экран умеет явно показать и восстановить editor focus;
- action-panel под Monaco должен стать компактнее: icon-only кнопки вместо текстовых, с понятными hover tooltips и без лишнего визуального веса;
- action-panel больше не должен жить отдельным блоком под Monaco: быстрые действия и execution controls нужно держать внутри `Шаблоны и быстрые действия`, чтобы не дублировать точки управления;
- result table для `SELECT` должна выглядеть как IDE-like data grid: более явная сетка, визуально разделенные поля, hover/focus affordance и полезная cell-level click action вместо обычной bootstrap-таблицы;
- group expanders в source selection не должны выглядеть как текстовые utility-кнопки `Раскрыть/Свернуть`: нужен compact modern chevron-expander без текста, с hover tooltip и читаемым expanded/collapsed state;
- compact chevron-expander, введенный в SQL-консоли, нужно распространить на весь интерфейс как единый expandable-control pattern: новые и существующие `Раскрыть/Свернуть` utility-кнопки должны постепенно мигрировать на icon-only chevron с hover tooltip и ясным expanded/collapsed state;
- page-level success banner сразу под шапкой SQL-консоли не нужен: сообщения вроде `Запрос запущен` считаются рудиментом и не должны рендериться как верхний alert;
- read-only guardrail с сообщениями вида `Команда SELECT распознана как read-only.` не нужен: для безопасных команд этот отдельный информационный блок считается шумом и не должен рендериться;
- отдельный visual block `Текущий контекст выполнения` не нужен: он дублирует выбранные параметры из sidebar и должен быть полностью удален, а не замаскирован;
- remaining UX contract для shortcut-help:
  - блок горячих клавиш должен быть сворачиваемым;
  - по умолчанию он должен быть свернут;
  - кнопка `Вернуть фокус в Monaco` должна быть idempotent и не должна выбрасывать JS-ошибки при повторных нажатиях;
  - на первом этапе состояние expand/collapse не нужно делать persisted;

- четко развести source/settings, editor, execution state, result output и transaction controls;
- убрать визуальное смешение между рабочим контекстом и результатами запроса;
- сделать опасные и необратимые состояния очевидными без banner-spam и лишнего дублирования;
- улучшить presentation для pending/empty/error/success состояний;
- упростить навигацию по statement/shard/page/result views;
- для source/shard execution status не держать параллельно таблицу и карточки с одинаковыми данными: primary view должна оставаться таблица, карточный дубль нужно убрать;
- не прятать критические execution-инварианты в второстепенные блоки;
- индикатор подключения по source должен обновляться не только после явного `Проверить подключение`, но и по факту реального SQL execution на выбранных source: успешный run обновляет статус в `available/ok`, connection-level failure обновляет статус в `failed/unavailable`, неучаствовавшие source не должны менять состояние;
- сократить переключения контекста между recent queries, favorites, object browser и SQL editor;
- улучшить сценарии insert/open/inspect для объектов и запросов;
- удалить отдельный блок `Script outline` со списком SQL statement из page UI; если навигация по statement действительно нужна, она должна жить в Monaco-native форме, а не как параллельный внешний блок;
- формализовать hotkeys и focus behavior вокруг Monaco, execution actions и result navigation;
- исключить случайные конфликты browser shortcuts и editor actions.

Фаза D. Monaco hints и autocomplete:

- реализовано в первой волне:
  - добавлен local completion provider для PostgreSQL keywords;
  - добавлены local function completions и snippets для частых SQL-шаблонов;
  - добавлен lightweight hover по keywords/functions без backend-зависимости и без вмешательства в execution lifecycle;
  - добавлен metadata-aware autocomplete по объектам БД через текущий search-first backend, без нового stateful completion-session слоя;
  - autocomplete учитывает выбранные source, использует избранные объекты как zero-latency local source и держит только session-local bounded cache;
  - сценарий `schema.` теперь умеет подсказывать объекты схемы editor-native способом, без возврата внешнего `Script outline` или catalog preload;
  - exact object column lookup теперь тоже реализован в `search-first` форме:
    - добавлен readonly columns contract для exact object по выбранным source;
    - Monaco умеет подсказывать колонки в сценарии `schema.table.` через bounded session-local cache;
    - autocomplete колонок не тащит full inspector payload и не создает stateful backend completion-session;
  - добавлен следующий bounded слой editor intelligence:
    - простые `FROM / JOIN / UPDATE alias.` сценарии теперь тоже получают column autocomplete;
    - alias resolution живет локально в Monaco-helper как lightweight parser по текущему SQL prefix;
    - backend для этого не хранит отдельную completion-session и reuse-ит тот же readonly columns boundary;

- развивать Monaco постепенно, без тяжелого IDE/LSP-стека и без ущерба для архитектуры SQL-консоли;
- уровень 1:
  - local completion по keywords/functions/snippets;
  - hover hints;
- уровень 2:
  - metadata-aware autocomplete по schema/table/view/index names;
  - bounded search-first completion по объектам БД;
  - exact object column lookup там, где это оправдано;
- уровень 3:
  - deeper language intelligence только при доказанной необходимости.

Фаза E. SQL-console-specific regression coverage и invariants:

- после стабилизации interaction model добавить screen-level smoke coverage на ключевые UX/safety сценарии SQL-консоли;
- закрепить SQL-консоль отдельными архитектурными и тестовыми инвариантами;
- не допускать giant-file debt и размывания границ между `ui-compose-shared`, `ui-compose-web` и `ui-server`.
- browser-level smoke coverage уже реализован отдельным bounded harness:
  - [scripts/run-sql-console-browser-smoke.sh](/Users/kwdev/DataPoolLoader/scripts/run-sql-console-browser-smoke.sh) поднимает изолированный UI runtime со своим `storageDir`, отдельным port и smoke-config, не смешивая browser tests с пользовательским SQL-console state;
  - [tools/sql-console-browser-smoke/tests/sql-console.smoke.spec.mjs](/Users/kwdev/DataPoolLoader/tools/sql-console-browser-smoke/tests/sql-console.smoke.spec.mjs) держит 4 ключевых сценария:
    - multi-tab workspace clone без ложного banner про blocked popup;
    - manual transaction -> `PENDING_COMMIT` -> `Rollback`;
    - object inspector -> `Открыть SELECT в консоли`;
    - result `Grid / Diff` navigation;
  - smoke loop готовит детерминированную schema `datapool_manual`, чтобы object-browser и transactional сценарии не зависели от случайного user catalog state.
- текущий regression package должен явно держать:
  - `group-first` runtime contract с synthetic `Без группы`;
  - одинаковое восстановление `selectedGroups / selectedSources / manual include-exclude` в основном экране и `sql-console-objects`;
  - readonly exact-column lookup как отдельный boundary, не смешанный с inspector payload.
  - shared-store safety contract для `strict safety` и ownership-loss handling execution actions;
  - object-browser action `Открыть SELECT в консоли` как deterministic workspace rewrite по source/draft, без скрытого смешения групп и selection.

Фаза F. IDE-like enhancements:

- следующий продуктовый пакет после закрытия текущего SQL-console baseline:
  1. statement status markers прямо в Monaco:
    - gutter/inline markers для `ok / error / rows / duration / pending commit`;
    - markers должны жить как editor-native execution feedback, а не как новый page-level summary wall;
  2. быстрый object navigation из редактора:
    - hover/quick actions по `schema.table` и близким object references;
    - действия минимум: открыть inspector, открыть `SELECT` в консоли, перейти к columns metadata;
  3. result grid ergonomics ближе к IDE/data-grid:
    - resize колонок;
    - autosize по содержимому;
    - `wrap / nowrap` toggle;
    - copy row / copy column / copy cell;
    - явное выделение активной ячейки;
  4. statement-level run model:
    - явно развести `выполнить текущий statement`, `выполнить выделение`, `выполнить весь script`;
    - UX и hotkeys этих сценариев должны быть одинаково читаемыми и не конфликтовать с текущим safety-contract;
  5. `EXPLAIN / EXPLAIN ANALYZE` как first-class actions:
    - не заставлять пользователя писать их руками каждый раз;
    - выполнять только через явные SQL actions, без скрытой подмены текста;
  6. diff / mismatch view по source:
    - показывать расхождения `row count / value mismatch / source failure` в compare-friendly форме;
    - не смешивать этот режим с обычным result table без явного переключения;
  7. execution history per workspace:
    - короткая локальная история execution sessions именно для SQL-консоли;
    - хранить SQL, source set, outcome и duration без превращения экрана в тяжелый audit log.

- ограничения этой фазы:
  - не тащить полноценный SQL LSP;
  - не строить тяжелый parser/AST слой без доказанной необходимости;
  - не вводить внутренние tabs внутри одной страницы вместо уже реализованных browser-tab workspaces;
  - не размывать object browser, превращая его во вторую консоль или встроенный data preview screen.
- уже начат первый пакет этой фазы:
  - statement execution status markers добавляются прямо в Monaco как editor-native decorations;
  - marker contract строится client-side из `script outline + execution result` без нового backend state;
  - первый пакет не вводит тяжелый diagnostics engine и не подменяет result navigator отдельной marker-wall.
  - второй пакет тоже начат:
    - быстрый object navigation живет editor-native через Monaco hover/context actions, а не через новый page-level shortcut block;
    - действия `открыть inspector`, `перейти к columns metadata` и `открыть SELECT в новой вкладке консоли` должны reuse-ить существующий search-first object contract и browser-tab workspace model;
    - object browser deep-link поддерживает явный `tab`, чтобы editor navigation мог открывать не только overview, но и целевую inspector-вкладку.
  - третий пакет тоже начат:
    - `SELECT` result table получает bounded data-grid ergonomics без client-side искажения результата БД;
    - grid поддерживает global `wrap / nowrap`, autosize всех колонок, per-column resize и copy по ячейке, строке и колонке;
    - active cell остается локальным UI-state таблицы и не превращается в новый persisted/workspace state;
    - grid не получает client-side sort/filter, чтобы не создавать второй источник истины поверх реального DB result.
  - четвертый пакет тоже начат:
    - execution mode становится явным: `текущий statement`, `выделение`, `весь script` существуют как три разные user-visible actions;
    - toolbar, Monaco hotkeys и run-scope summary должны показывать один и тот же contract, без скрытого `selection if exists else current`;
    - выполнение выделения остается чисто editor-local UX слоем и reuse-ит тот же `startQuery(sqlOverride)` boundary, без нового backend execution mode.
  - пятый пакет тоже начат:
    - `EXPLAIN` и `EXPLAIN ANALYZE` живут как отдельные toolbar actions, а не как скрытая временная подмена `draft SQL`;
    - explain-scope остается явным и bounded: поддерживаются только `current statement` и `выделение`, без неочевидного `EXPLAIN всего script`;
    - `EXPLAIN ANALYZE` не должен обходить safety-contract: shared SQL analysis обязан различать plan-only `EXPLAIN` и реально исполняющий `EXPLAIN ANALYZE` по внутреннему statement keyword.
  - шестой пакет тоже начат:
    - compare-friendly `Diff` живет как отдельный data-view режим, а не как домешивание mismatch-информации в обычную result grid;
    - mismatch model считается client-side из уже загруженного `RESULT_SET`, без нового backend state и без искажения исходных DB results;
    - diff обязан явно показывать `row count mismatch`, `value mismatch` и `source failure`, но оставаться bounded по числу detail-entries.
  - седьмой пакет тоже начат:
    - execution history живет как отдельный per-workspace boundary, а не как еще одно поле внутри persisted draft/library state SQL-консоли;
    - история заполняется server-side из async execution lifecycle, поэтому фиксирует не только обычный finish, но и `PENDING_COMMIT`, manual `commit/rollback`, owner-loss и timeout rollback;
    - UI показывает короткий bounded список последних execution session рядом с `Шаблоны и быстрые действия`, с действиями `Подставить` и `Повторить`, без превращения экрана в тяжелый audit log.

Review after Phase F:

- по итогам review первая продуктовая волна SQL-консоли считается реализованной:
  - safety/recovery, multi-tab, group-first selection, object inspector, main UX, Monaco, IDE-like enhancements и execution history уже собраны в рабочую подсистему;
  - backlog `6` больше не должен расширяться новыми product-идеями до отдельного нового решения;
- closure-task пакет этой review-волны закрыт:
  1. browser-level smoke coverage реализован отдельным bounded Playwright harness без смешения с пользовательским runtime state;
  2. giant metadata-introspection слой в `core` разрезан на отдельные postgres/generic search/inspect/details responsibilities;
  3. SQL-console test layer разрезан на reviewable support/spec структуру для `core`, `ui-server` route-slice и `query manager`;
- блок `6` теперь закрывается формально как текущий SQL-console baseline;
- любые новые доработки SQL-консоли после этого момента должны идти уже как отдельная новая фаза в backlog, а не как бесконечный хвост старой программы.
- update after review:
  - giant metadata-introspection слой в `core` уже приведен к более узкой структуре:
    - postgres search вынесен отдельно;
    - postgres table-like inspect/columns вынесен отдельно;
    - postgres secondary object inspect (`index / sequence / trigger / schema`) вынесен отдельно;
    - generic JDBC metadata path и shared SQL/result-set helper-ы вынесены отдельно;
  - SQL-console test-layer cleanup уже начат:
    - server-side SQL-console route/lifecycle/export slice вынесен из giant [ServerTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/server/ServerTest.kt) в отдельный [SqlConsoleServerTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleServerTest.kt);
    - общие server test helper-ы вынесены в [ServerTestSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/server/ServerTestSupport.kt);
    - giant [SqlConsoleServiceTest.kt](/Users/kwdev/DataPoolLoader/core/src/test/kotlin/com/sbrf/lt/datapool/SqlConsoleServiceTest.kt) разрезан на:
      - [SqlConsoleServiceExecutionTest.kt](/Users/kwdev/DataPoolLoader/core/src/test/kotlin/com/sbrf/lt/datapool/SqlConsoleServiceExecutionTest.kt)
      - [SqlConsoleServiceMetadataTest.kt](/Users/kwdev/DataPoolLoader/core/src/test/kotlin/com/sbrf/lt/datapool/SqlConsoleServiceMetadataTest.kt)
      - [SqlConsoleServiceTestSupport.kt](/Users/kwdev/DataPoolLoader/core/src/test/kotlin/com/sbrf/lt/datapool/SqlConsoleServiceTestSupport.kt)
    - giant [SqlConsoleQueryManagerTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleQueryManagerTest.kt) разрезан на:
      - [SqlConsoleQueryManagerExecutionTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleQueryManagerExecutionTest.kt)
      - [SqlConsoleQueryManagerTransactionSafetyTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleQueryManagerTransactionSafetyTest.kt)
      - [SqlConsoleQueryManagerTestSupport.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleQueryManagerTestSupport.kt)
  - browser-level smoke coverage закрыт отдельным harness:
    - [scripts/run-sql-console-browser-smoke.sh](/Users/kwdev/DataPoolLoader/scripts/run-sql-console-browser-smoke.sh)
    - [tools/sql-console-browser-smoke/playwright.config.mjs](/Users/kwdev/DataPoolLoader/tools/sql-console-browser-smoke/playwright.config.mjs)
    - [tools/sql-console-browser-smoke/tests/sql-console.smoke.spec.mjs](/Users/kwdev/DataPoolLoader/tools/sql-console-browser-smoke/tests/sql-console.smoke.spec.mjs)
    - сценарии покрывают multi-tab clone, pending-commit rollback UX, object inspector -> `Открыть SELECT`, и `Grid / Diff` navigation на изолированном smoke runtime.

Критерий завершения:

- SQL-консоль рассматривается и поддерживается как самостоятельная подсистема, а не как “еще один экран”.
- manual transaction не может жить бесконтрольно;
- execution и transaction state понимаются без ручного обхода экрана;
- экран не дублирует одну и ту же operational information в нескольких presentation-формах без нового сигнала;
- navigation/help around SQL statements не существует как дублирующий внешний page-block, если тот же сценарий может быть закрыт editor-native поведением Monaco;
- connection status по source отражает последний релевантный outcome реального взаимодействия, а не только результат отдельной ручной проверки;
- object browser поддерживает быстрый сценарий `найти -> понять -> открыть SQL/preview`, а не заставляет пользователя вручную разбирать несколько конкурирующих блоков экрана;
- просмотр объекта поддерживает отдельный inspector-сценарий уровня DBA-инструмента, без превращения search results в тяжелый metadata-dump;
- object inspector сфокусирован на schema metadata и DDL, а не на показе табличных данных;
- переход к данным из object browser решается одной явной action-кнопкой `Открыть SELECT в консоли`, без отдельного preview-режима на самом объектном экране;
- поддерживаемые inspectable object types и границы первой волны явно определены и не остаются скрытым допущением;
- object inspector реализован как type-aware tabbed UI: содержимое и вкладки зависят от типа объекта, а не от одного универсального layout;
- Monaco становится ощутимо полезнее, но не ломает execution lifecycle и не тащит тяжелую stateful backend-механику.
- дальнейшие IDE-like enhancements идут отдельной фазой и не подменяют собой safety/recovery baseline.

### 7. SQL-консоль: safety hardening по аварийным сценариям

Статус:

- объединено в пункт 6

Активные safety-задачи SQL-консоли перенесены в master-блок [6. SQL-консоль: единая программа развития](/Users/kwdev/DataPoolLoader/BACKLOG.md).

### 8. Нормализовать build baseline на Java 21

Статус:

- реализовано

Цель:

- перевести проект с legacy baseline `Java 17` на `Java 21`;
- убрать ситуацию, где build-скрипты зашиты на `17`, а реальная сборка идет под `JDK 25` с fallback поведением Kotlin;
- сделать toolchain проекта воспроизводимым и честным для локальной разработки и CI.

Что сделано:

- Gradle Java toolchain и Kotlin JVM target переведены на `21` в root, desktop и shared build-слоях;
- добавлен reproducible toolchain resolution через Gradle daemon JVM criteria и auto-download;
- build baseline больше не зависит от случайного локального `JDK 25`;
- полный compile/test baseline прогнан уже под `Java 21`.

Критерий завершения:

- проект явно собирается и тестируется на `Java 21`;
- в build-скриптах не остается legacy-настроек `17`;
- baseline больше не зависит от случайной локальной `JDK 25`.

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

Что нужно сделать:

- выровнять lifecycle-модели между подсистемами;
- убрать неочевидные pending-состояния;
- закрепить политику rollback / cleanup / recovery;
- усилить server-side и scenario-level тесты.

### 10. Cleanup и укрощение толстых store-слоев

Статус:

- частично реализовано

Цель:

- не допустить, чтобы `ui-compose-shared` превратился в mini-backend.

Первый приоритет:

- [ModuleEditorStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStore.kt)
- store-слои SQL-консоли;
- store-слои maintenance/run-history экранов.

Что нужно сделать:

- отделить state management от тяжелой orchestration-логики;
- не держать в одном store:
  - загрузку;
  - mutation lifecycle;
  - runtime branching;
  - UX-политику;
  - error normalization.

Что уже сделано:

- начат cleanup store-слоев SQL-консоли:
  - [SqlConsoleStoreExecutionSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStoreExecutionSupport.kt) перестал держать весь execution lifecycle как один knowledge-heavy файл;
  - settings/connection flow вынесен в [SqlConsoleStoreSettingsSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStoreSettingsSupport.kt);
  - query lifecycle вынесен в [SqlConsoleStoreQueryLifecycleSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStoreQueryLifecycleSupport.kt);
  - owner actions и execution state helpers вынесены в [SqlConsoleStoreOwnerActionSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStoreOwnerActionSupport.kt) и [SqlConsoleStoreExecutionStateSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStoreExecutionStateSupport.kt).
  - façade store SQL-консоли тоже начал худеть:
    - [SqlConsoleStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStore.kt) больше не смешивает orchestration API-вызовы и pure state mutations;
    - pure state/input mutations вынесены в [SqlConsoleStoreStateSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStoreStateSupport.kt);
    - query-history/favorites UX-state вынесены в [SqlConsoleStoreLibrarySupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStoreLibrarySupport.kt);
  - object-browser store cleanup тоже начат:
    - [SqlConsoleObjectsStoreActionSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsStoreActionSupport.kt) больше не держит persistence/favorites/search/inspector flow в одном файле;
    - workspace persistence вынесен в [SqlConsoleObjectsStorePersistenceSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsStorePersistenceSupport.kt);
    - favorites вынесены в [SqlConsoleObjectsStoreFavoriteSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsStoreFavoriteSupport.kt);
    - search и inspector вынесены в [SqlConsoleObjectsStoreSearchSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsStoreSearchSupport.kt) и [SqlConsoleObjectsStoreInspectorSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsStoreInspectorSupport.kt).
    - [SqlConsoleObjectsStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsStore.kt) больше не держит inline query/selection/inspector UI-state helpers; они вынесены в [SqlConsoleObjectsStoreStateSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsStoreStateSupport.kt).
  - начат cleanup store-support кластера `module_editor`:
    - [ModuleEditorStoreDraftSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStoreDraftSupport.kt) перестал держать status mutations, editor field updates и create-dialog draft в одном knowledge-heavy helper;
    - чистые draft mutations вынесены в [ModuleEditorStoreDraftStatusSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStoreDraftStatusSupport.kt), [ModuleEditorStoreDraftFieldSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStoreDraftFieldSupport.kt) и [ModuleEditorStoreCreateModuleDraftSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStoreCreateModuleDraftSupport.kt);
    - SQL-resource flow больше не смешивает naming rules и mutation lifecycle: naming вынесен в [ModuleEditorStoreSqlResourceNamingSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStoreSqlResourceNamingSupport.kt), mutations вынесены в [ModuleEditorStoreSqlResourceMutationSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStoreSqlResourceMutationSupport.kt);
    - config-form support больше не держит parsing/apply flow и SQL-resource tracking в одном файле: они разведены в [ModuleEditorStoreConfigFormMutationSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStoreConfigFormMutationSupport.kt) и [ModuleEditorStoreConfigFormSqlResourceSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStoreConfigFormSqlResourceSupport.kt);
    - добавлены первые common tests на чистые naming/resource-tracking правила `module_editor`, чтобы этот cleanup не остался без regression coverage.

### 12. Финализировать boundary модульного редактора и storage contracts

Статус:

- не реализовано

Цель:

- дочистить архитектурные границы вокруг единого module editor;
- убрать оставшиеся file/db-specific leakage из editor/store/service слоев.

Что нужно сделать:

- проверить, где editor еще знает слишком много о storage mode;
- дочистить контракты around:
  - module catalog;
  - module editor store;
  - SQL resource lifecycle;
  - metadata / lifecycle / publish / working copy semantics;
- убедиться, что `Module Editor` действительно остается единым экраном с разными storage adapters, а не двумя логиками в одном файле;
- убрать устаревшие переходные решения, если они остались после реализации DB/files режима.

Критерий завершения:

- storage mode остается implementation detail для значимой части UI;
- module editor и связанные backend contracts становятся чище и предсказуемее.

### 11. Зафиксировать и поддерживать repo-level архитектурную дисциплину

Статус:

- частично реализовано

Что уже есть:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [AGENTS.md](/Users/kwdev/DataPoolLoader/AGENTS.md)

Что нужно сделать:

- держать архитектурные требования в [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md) как в едином живом документе;
- закрепить обязательную code-quality дисциплину на уровне правил разработки:
  - смотреть на код sonar-like взглядом;
  - не пропускать дублирование;
  - не тащить мертвый код и legacy-хвосты;
  - отслеживать file/method/class size, сложность и скрытую связанность;
- не допускать расхождения между кодом и repo-level правилами;
- при заметных архитектурных изменениях обновлять документацию в том же change set;
- после каждого большого этапа backlog выполнять отдельный архитектурный review и по его итогам корректировать backlog и правила.

## P2

### 16. Усилить тестовую стратегию под архитектурную программу

Статус:

- не реализовано

Цель:

- сделать архитектурный рефакторинг безопасным.

Что нужно сделать:

- добавить больше store-level tests;
- усилить server contract tests;
- добавить smoke-покрытие на критичные UI-сценарии;
- отдельно страховать:
  - SQL console lifecycle;
  - run lifecycle;
  - cleanup/retention;
  - runtime fallback scenarios.

### 17. Формализовать карту состояния проекта

Статус:

- не реализовано

Цель:

- иметь отдельный инженерный документ, в котором перечислены:
  - все значимые persisted состояния;
  - все runtime state;
  - их source of truth;
  - правила восстановления и очистки.

Это нужно для дальнейшего рефакторинга и для предотвращения повторного смешения state-моделей.

### 18. SQL-консоль: staged Monaco hints и autocomplete для PostgreSQL

Статус:

- объединено в пункт 6

Активные Monaco/autocomplete-задачи SQL-консоли перенесены в master-блок [6. SQL-консоль: единая программа развития](/Users/kwdev/DataPoolLoader/BACKLOG.md).

### 19. SQL-консоль: интерфейс, читаемость и control-flow UX

Статус:

- объединено в пункт 6

Активные UX/object-browser/object-inspector-задачи SQL-консоли перенесены в master-блок [6. SQL-консоль: единая программа развития](/Users/kwdev/DataPoolLoader/BACKLOG.md).

## P3

### 20. Product и UX-задачи, не влияющие на архитектурную программу

Статус:

- отложено

Сюда временно относятся все задачи, которые:

- не повышают архитектурный порядок;
- не улучшают надежность;
- не уменьшают structural debt;
- не помогают cleanup legacy.

Они возвращаются в активный backlog только после завершения текущей архитектурной волны или при отдельном явном переприоритизировании.
