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
- [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md)
- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)
- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)

## Правила приоритизации

- `P0` — архитектурные и операционные задачи, которые прямо влияют на надежность и maintainability.
- `P1` — обязательный следующий слой после `P0`, без которого архитектурный порядок останется незавершенным.
- `P2` — среднесрочное усиление качества и developer experience.
- `P3` — отложенные продуктовые и UX-задачи, которые не должны обгонять архитектурную программу.

## P0

Текущее состояние `P0`:

- основные baseline-пакеты `P0` уже закрыты;
- новые `P0` задачи заводятся только для реального нарушения build/runtime baseline, safety или архитектурного инварианта;
- активная работа смещена в `P1/P2` architectural follow-up streams.

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

- реализовано

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

- реализовано

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
- собрана отдельная инженерная карта состояний в [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md):
  - persisted local state;
  - runtime/process-local state;
  - UI page/store state;
  - operational config;
  - recovery/cleanup rules;
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

- реализовано

Источник:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)
- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)

Цель:

- вести SQL-консоль как самостоятельную подсистему, а не как набор разрозненных экранных задач;
- выстроить единый порядок работ: сначала safety и ownership, затем object inspector и UI control-flow, затем Monaco и regression coverage.

Что уже сделано:

- server-side ownership и transaction safety;
- multi-tab workspaces и `workspaceId`;
- global source groups и `group-first` selection;
- search-first object browser и type-aware tabbed inspector;
- staged Monaco autocomplete, diff-mode и IDE-like result ergonomics;
- browser-level smoke harness и разрезанный SQL-console test layer.

Детальная история фаз `A -> F`, closure review и закрытые implementation details вынесены в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

Что остается после закрытия baseline:

- новые инвестиции в SQL-консоль должны идти уже отдельной фазой, а не как хвост старой программы;
- из старого baseline осмысленным функциональным хвостом остается только richer inspector metadata (`comments / dependencies / grants / richer DDL`), если это даст новый DBA-сигнал.

Критерий завершения:

- SQL-консоль рассматривается и поддерживается как самостоятельная подсистема, а не как “еще один экран”;
- safety/recovery, object browser, Monaco и browser smoke baseline уже не требуют повторного добивания теми же пакетами.

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

Текущий наивысший приоритет внутри `P1`:

- [13. SQL-консоль: visual IDE refinement](/Users/kwdev/DataPoolLoader/BACKLOG.md);
- до завершения первого пакета этой фазы не размывать активную работу новыми SQL-console фичами, если они не снимают явный safety или visual defect.

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

- SQL-console shared stores выведены из статуса top-offender:
  - execution/settings/query/owner flow и object-browser state уже разрезаны на более узкие support-слои;
  - façade store SQL-консоли перестал держать inline orchestration и pure mutations в одном файле.
- `module_editor` прошел основную cleanup-волну:
  - draft/resource/config/save/run/lifecycle/storage boundaries уже разнесены;
  - remaining workflow/store wiring выглядит как reviewable façade, а не как giant knowledge-heavy cluster.
- `module_runs`, `module_sync` и `run_history_cleanup` уже получили bounded splits по runtime, selection, action и loading-state policy;
- новые support-boundary extraction-волны страхуются common tests, чтобы cleanup не оставался без regression coverage;
- детальная хронология выполненных волн вынесена в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

Следующий шаг:

- сделать отдельный review remaining shared-store top-offenders;
- если нового реального thick cluster уже нет, закрыть эту волну как завершенную и не продолжать инерционный split уже reviewable façade-файлов;
- если thick cluster еще есть, назвать его явно и завести следующий bounded пакет с конкретной причиной, а не “дробить дальше все подряд”.

### 11. Зафиксировать и поддерживать repo-level архитектурную дисциплину

Статус:

- частично реализовано

Что уже есть:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [AGENTS.md](/Users/kwdev/DataPoolLoader/AGENTS.md)
- [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md)

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

### 12. Финализировать boundary модульного редактора и storage contracts

Статус:

- реализовано

Цель:

- дочистить архитектурные границы вокруг единого module editor;
- убрать file/db-specific leakage из editor/store/service слоев.

Что уже сделано:

- storage-aware `read / save / run` boundaries вынесены в отдельные store-контракты;
- database lifecycle и working-copy lifecycle отделены от общего workflow/write path;
- обычные `save/run` сценарии переведены на unified workflow contract без ручного выбора storage path в page execution layer;
- закрывающий review показал, что remaining `route.storage` checks теперь в основном относятся к UI-capabilities и presentation, а не к knowledge-heavy workflow branching.

Детальная история реализации и список закрытых boundary-пакетов вынесены в [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md).

Критерий завершения:

- storage mode остается implementation detail для значимой части UI;
- module editor и связанные backend contracts становятся чище и предсказуемее.

### 13. SQL-консоль: visual IDE refinement

Статус:

- не реализовано

Цель:

- довести главный экран SQL-консоли от аккуратного web-tool вида до более pane-based IDE visual model;
- уменьшить dashboard/card feel без ослабления safety, execution readability и transaction affordance.

Дополнительный сигнал:

- ручная проверка показала, что execution history per workspace в текущем виде слишком сильно раздувает главный экран и конкурирует с editor/result panes.

Порядок реализации:

1. `Execution history demotion`:
   - execution history per workspace сохраняется как функциональный boundary, но не должна раздувать главный экран;
   - history не должна быть постоянно раскрытым крупным блоком рядом с editor;
   - первая целевая форма: secondary/collapsible tool window, collapsed by default или скрытая за явным tab/switch.
2. `Source navigator redesign`:
   - перевести левую колонку источников и групп ближе к navigator/tree-pane;
   - меньше card-like контейнеров на каждый source/group;
   - плотнее строки, чище row selection, честный explorer-like scan.
3. `Tool-window redesign` для правой secondary-зоны:
   - `Шаблоны и быстрые действия`, favorites и соседние secondary blocks должны выглядеть как IDE tool windows, а не как dashboard widgets;
   - меньше визуальной конкуренции с editor/result pane.
4. `Shell densification`:
   - уменьшить cardification, слишком мягкие фоны, крупные радиусы и избыточные внутренние отступы;
   - усилить ощущение pane/panel hierarchy вместо набора dashboard-карточек.
5. `Result pane densification`:
   - довести chrome вокруг result area до более строгого IDE/data-grid вида;
   - меньше декоративных toolbar/panel подложек, больше ощущения единой рабочей области.
6. `Status bar / working status line`:
   - рассмотреть компактный нижний status bar для execution mode, selected sources count, transaction mode и текущего result context;
   - не возвращать page-level banners и не дублировать sidebar.

Ограничения:

- не расширять эту фазу новыми parser/LSP-функциями;
- не возвращать дублирующие summary blocks и banner spam;
- execution history нельзя удалять как capability, нужно понизить ее visual prominence;
- safety-state и `pending commit` affordance должны оставаться читаемыми после visual cleanup.

Критерий завершения:

- главный экран SQL-консоли ощущается как IDE-like workspace с pane hierarchy, а не как набор dashboard-карточек;
- execution history больше не раздувает первый экран;
- navigator/editor/result/tool-window иерархия читается с первого взгляда.

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

- реализовано

Цель:

- иметь отдельный инженерный документ, в котором перечислены:
  - все значимые persisted состояния;
  - все runtime state;
  - их source of truth;
  - правила восстановления и очистки.

Это нужно для дальнейшего рефакторинга и для предотвращения повторного смешения state-моделей.

Что уже сделано:

- добавлен [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md);
- карта состояния теперь явно разделяет:
  - `operational config`;
  - `persisted local state`;
  - `operational history`;
  - `UI preference`;
  - `transient runtime state`;
  - `cache`;
- для ключевых state boundary задокументированы:
  - source of truth;
  - recovery rules;
  - cleanup rules;
- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md) обновлен так, чтобы state-model изменения требовали синхронизации с этим документом.

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
