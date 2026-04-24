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

- [15. Kafka-инструмент: локальный cluster explorer и topic operations](/Users/kwdev/DataPoolLoader/BACKLOG.md);
- после фиксации контрактов `15.0` и `15.1` не размывать Kafka-stream вне backlog:
  сначала metadata/consumers/messages/produce baseline, потом только дополнительные integrations.

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

- реализовано

Цель:

- довести главный экран SQL-консоли от аккуратного web-tool вида до более pane-based IDE visual model;
- уменьшить dashboard/card feel без ослабления safety, execution readability и transaction affordance.

Дополнительный сигнал:

- ручная проверка показала, что execution history per workspace в текущем виде слишком сильно раздувает главный экран и конкурирует с editor/result panes.
- ручная проверка также показала runtime-defect в object inspector: inspector сейчас не отображает metadata в части сценариев и требует отдельного hardening-пакета до visual cleanup.

Реализованные пакеты:

#### 13.0. Object inspector direct-load hardening

Статус:

- реализовано

Проблема:

- inspector сейчас завязан на цепочку `deep-link -> search result -> selection -> metadata load`;
- в текущем web path metadata load фактически стартует только если `navigationTarget` нашелся в `searchResponse`;
- если объект не совпал с текущим search result, не попал в ограниченный список результатов или search/load flow дал рассинхрон, inspector не показывает информацию вообще, хотя URL уже содержит достаточно данных для прямой загрузки metadata.

Текущая зона риска:

- [SqlConsoleObjectsPageEffects.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPageEffects.kt)
- [SqlConsoleObjectsPageContentSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPageContentSections.kt)
- [SqlConsoleObjectInspectorSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectInspectorSections.kt)

Целевой контракт:

- inspector должен уметь грузиться напрямую из `navigationTarget`, если в URL уже есть `source/schema/object/type`;
- search result остается вспомогательным контекстом для списка объектов, но не является обязательным gate для inspector metadata;
- deep-link на inspector должен быть устойчивым даже если:
  - search result пуст;
  - объект не попал в truncated result set;
  - query изменился и больше не совпадает с navigation target;
- UI должен явно различать:
  - `inspector loading`;
  - `inspector error`;
  - `object not found by metadata request`;
  - `search result does not currently contain object`, но inspector metadata при этом доступна.

Что нужно сделать:

1. отвязать inspector load от обязательного наличия selected object внутри `searchResponse`;
2. ввести direct inspector request path от `navigationTarget`;
3. сохранить search-first модель каталога, но перестать использовать search result как hard prerequisite для metadata render;
4. выровнять empty/error states, чтобы inspector не исчезал молча;
5. добавить regression coverage на deep-link inspector scenarios.

Что сделано:

- inspector load в web path больше не зависит от обязательного попадания объекта в `searchResponse`;
- direct deep-link `source/schema/object/type` теперь грузит metadata отдельно, даже если search result пуст или не содержит объект;
- убран state-race между `load/search/inspect/persist`, из-за которого object screen мог перетирать свежий inspector/search state stale snapshot-ом;
- browser smoke теперь держит отдельный сценарий `deep-link inspector + empty search result`.

Ближайший пакет:

#### 13.1. Execution history на отдельный workspace-scoped экран

Статус:

- реализовано

Проблема:

- execution history сейчас рендерится как отдельный крупный блок на главном экране до library/tool-window слоя;
- список из session-card’ов визуально конкурирует с editor/result panes и раздувает первый экран после первых же запусков;
- history по смыслу является operational log текущего workspace, но по подаче сейчас выглядит как primary page content.

Целевой контракт:

- history остается `per workspace` capability c действиями `Подставить` и `Повторить`;
- history полностью уходит с главного экрана SQL-консоли;
- history живет на отдельном route, а не в secondary pane текущей страницы;
- экран истории по умолчанию `workspace-scoped`:
  - каждая вкладка SQL-консоли открывает историю только своего `workspaceId`;
  - `Подставить` и `Повторить` работают в контексте этого же workspace;
- главный экран SQL-консоли не показывает список execution session и не держит под него layout space;
- на отдельном экране history может иметь более плотную IDE-like подачу:
  - компактные строки вместо крупных cards;
  - status, startedAt/duration, source set summary, SQL preview;
  - действия `Подставить` и `Повторить` сохраняются;
- если позже понадобится cross-workspace обзор, это должен быть отдельный read-only слой, а не смешение history разных вкладок в один operational экран.

Что нужно сделать:

1. убрать always-visible history block из текущего primary layout рядом с `Шаблоны и быстрые действия`;
2. завести отдельный route экрана истории SQL-консоли;
3. сделать этот экран строго `workspace-scoped` по текущему `workspaceId`;
4. переделать visual model history:
   - card list -> compact row list;
   - меньше вертикального шума и декоративных подложек;
5. оставить execution history отдельной operational сущностью и не смешивать ее с `recent queries`;
6. добавить regression coverage:
   - history больше не раздувает главный экран;
   - `Подставить` и `Повторить` продолжают работать;
   - workspace-scoped history contract не ломается;
   - открытие истории из разных browser-вкладок не смешивает execution session.

Что сделано:

- history полностью убрана с главного экрана SQL-консоли и больше не занимает primary layout space;
- добавлен отдельный route `/sql-console-history` с `workspaceId` forwarding;
- history screen сделан `workspace-scoped` и использует тот же workspace contract для `Подставить` и `Повторить`;
- `Повторить` на history route сохраняет owner-state перед возвратом в main SQL-console screen, чтобы execution не терялся при navigation;
- browser smoke теперь отдельно держит сценарий `main screen -> history screen -> apply back to same workspace`.

#### 13.2. SQL workspace state retention и cleanup stale вкладок

Статус:

- реализовано

Проблема:

- SQL-консоль сейчас пишет per-workspace state и per-workspace execution history в `ui.storageDir`;
- для нестандартных `workspaceId` создаются отдельные файлы вида:
  - `sql-console-workspace-state-<workspaceId>.json`
  - `sql-console-execution-history-state-<workspaceId>.json`
- вкладки могут открываться и закрываться много раз, а явной lifecycle/cleanup-модели для stale workspace сейчас нет;
- без retention policy локальный storage будет накапливать orphan workspace state от давно закрытых вкладок.

Текущая зона:

- [SqlConsoleWorkspaceStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleWorkspaceStateStore.kt)
- [SqlConsoleExecutionHistoryStateStore.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleExecutionHistoryStateStore.kt)
- [SqlConsoleStateService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleStateService.kt)
- [SqlConsoleExecutionHistoryService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleExecutionHistoryService.kt)

Целевой контракт:

- refresh той же вкладки продолжает восстанавливать ее workspace state;
- активные и недавно использованные workspace не теряются;
- stale workspace state и stale execution history не накапливаются бесконечно в `storageDir`;
- cleanup не должен ломать multi-tab модель:
  - нельзя удалять state активной вкладки;
  - нельзя удалять workspace, который только что был открыт повторно;
- cleanup policy должна быть явной и документированной:
  - age-based retention;
  - возможно bounded max-count;
  - одинаковые правила для workspace-state и workspace execution-history.

Что нужно сделать:

1. определить durable lifecycle model для `workspaceId`:
   - что считается active;
   - что считается stale;
   - когда stale workspace можно удалять;
2. добавить persisted metadata, достаточную для cleanup decision:
   - last accessed / updated timestamp;
   - при необходимости version/marker для migration-safe cleanup;
3. реализовать cleanup stale workspace files в `ui-server`;
4. выровнять policy для:
   - workspace draft/selection state;
   - workspace execution history;
5. задокументировать retention contract в backlog/state docs;
6. добавить regression coverage на:
   - refresh той же вкладки;
   - reopen недавнего workspace;
   - cleanup старых workspace;
   - отсутствие удаления активного workspace.

Что сделано:

- в persisted workspace state и persisted execution history добавлен явный `lastAccessedAt`, чтобы cleanup опирался не на неявный browser/session guess, а на durable metadata;
- введен отдельный server-side boundary [SqlConsoleWorkspaceRetentionService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleWorkspaceRetentionService.kt), который:
  - держит age-based retention policy;
  - пинит workspace, к которым обращались в текущем процессе;
  - чистит stale pair `workspace-state + execution-history` как одну operational сущность;
- policy зафиксирована явно:
  - non-default workspace cleanup идет по `30 days` inactivity retention;
  - default workspace не удаляется автоматически;
  - recent и pinned workspace не трогаются;
- `SqlConsoleStateService` и `SqlConsoleExecutionHistoryService` теперь touch-ят workspace access metadata при `load/update/history access/record`;
- добавлена regression coverage на stale cleanup, recent reopen и защиту активного workspace от cleanup.

#### 13.3. Source navigator redesign

Статус:

- реализовано

Проблема:

- левая колонка SQL-консоли сейчас визуально подана как набор mini-card блоков на каждую группу и каждый source;
- это дает dashboard feel и мешает считывать selection как navigator/tree-pane;
- длинные explanatory messages на каждом source добавляют вертикальный шум и ослабляют IDE-like scan.

Текущая зона:

- [SqlConsoleSourceSelectionSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleSourceSelectionSections.kt)
- [SqlConsoleSourceSidebarSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleSourceSidebarSections.kt)
- [40-sql-results.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/40-sql-results.css)

Целевой контракт:

- группы и source читаются как navigator/tree-pane, а не как stack из отдельных cards;
- строки становятся плотнее;
- selection и partial-selection остаются визуально явными;
- status по source остается читаемым, но не превращает каждый row в отдельную info-card;
- explanatory noise снижается:
  - full error/detail показывается только когда он реально есть;
  - default helper text на каждой строке не нужен.

Что сделано:

- левая колонка источников переведена на более плотную navigator/tree-pane подачу вместо stack из mini-card блоков;
- group rows и source rows уплотнены:
  - меньше padding и радиусы;
  - меньше card-like подложек;
  - selection/partial-selection читаются как row state, а не как отдельная карточка;
- group rows получили компактный count badge;
- source rows теперь показывают detail line только при реальной ошибке или полезном runtime-message;
- status по source переведен на более IDE-like row signal:
  - компактный status text;
  - status dot рядом с именем source;
  - меньше цветной заливки на весь row.

#### 13.4. Tool-window redesign для secondary-зоны

Статус:

- реализовано

Проблема:

- блоки `Шаблоны и быстрые действия` и `Избранные объекты` сейчас выглядят как dashboard widgets;
- summary chips, мягкие подложки и крупные внутренние cards визуально конкурируют с editor/result pane;
- secondary-зона читается как набор самостоятельных карточек, а не как компактные IDE tool windows.

Текущая зона:

- [SqlConsoleQueryLibrarySections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleQueryLibrarySections.kt)
- [SqlConsoleFavoriteObjectSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleFavoriteObjectSections.kt)
- [SqlConsoleQueryPickerSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleQueryPickerSections.kt)
- [20-sql-console.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/20-sql-console.css)
- [40-sql-results.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/40-sql-results.css)

Целевой контракт:

- `Шаблоны и быстрые действия` и `Избранные объекты` выглядят как compact tool windows;
- shell secondary-зоны становится строже:
  - меньше мягких фоновых плашек;
  - меньше dashboard-like chips;
  - компактнее headers и строки действий;
- content внутри tool windows остается функционально тем же, но подается плотнее и спокойнее;
- editor и result pane остаются визуальным центром экрана.

Что сделано:

- для secondary-зоны введен общий visual shell `tool window` и на него переведены:
  - `Шаблоны и быстрые действия`;
  - `Избранные объекты`;
- headers, notes и open-actions стали компактнее и ближе к IDE tool-window chrome;
- summary chips поджаты и перестали выглядеть как крупные dashboard counters;
- query picker blocks получили более строгую section-подачу внутри tool window;
- `Избранные объекты` переведены из более тяжелой card-grid подачи в более плотный stacked secondary list.

#### 13.5. Shell densification

Статус:

- реализовано

Проблема:

- основной shell SQL-консоли все еще местами слишком мягкий и card-like:
  - крупные радиусы;
  - мягкие подложки;
  - избыточный внутренний воздух;
- из-за этого editor/result panes все еще не забирают достаточно визуального приоритета.

Текущая зона:

- [SqlConsolePageContentSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageContentSections.kt)
- [SqlConsoleSourceSidebarSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleSourceSidebarSections.kt)
- [SqlConsoleWorkspacePanelSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleWorkspacePanelSections.kt)
- [SqlConsoleWorkspaceToolbarSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleWorkspaceToolbarSections.kt)
- [SqlConsoleResultNavigatorSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultNavigatorSections.kt)
- [20-sql-console.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/20-sql-console.css)

Целевой контракт:

- shell SQL-консоли становится плотнее и строже;
- sidebar/workspace/output panes читаются как связанные части одной рабочей среды;
- editor frame и result pane получают больший визуальный приоритет;
- toolbar и navigator выглядят как pane chrome, а не как набор отдельных мягких карточек.

Что сделано:

- для sidebar и workspace pane введен более строгий shell chrome с явными pane-head секциями;
- editor frame, output shell и result navigator уплотнены:
  - меньше радиусы;
  - меньше мягкие подложки;
  - плотнее внутренние отступы;
- toolbar action groups и shortcut panel переведены на более плотную pane-chrome подачу;
- визуальный приоритет editor/output panes усилен без изменения execution behavior и safety affordance.

#### 13.6. Result pane densification

Статус:

- реализовано

Проблема:

- нижняя зона результатов все еще местами выглядит как набор мягких panel/card блоков:
  - grid toolbar слишком похож на отдельный widget;
  - diff summary и diff table держат лишнюю card-like подачу;
  - status pane использует слишком общий bootstrap-like table вид;
  - placeholder/meta-copy не собираются в строгий result-pane chrome;
- из-за этого result area читается слабее, чем editor pane, хотя именно она должна быть второй главной рабочей зоной.

Текущая зона:

- [SqlConsoleResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultSections.kt)
- [SqlConsoleDataResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleDataResultSections.kt)
- [SqlConsoleDiffResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleDiffResultSections.kt)
- [SqlConsoleStatusResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStatusResultSections.kt)
- [20-sql-console.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/20-sql-console.css)
- [40-sql-results.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/40-sql-results.css)

Целевой контракт:

- result area читается как единый рабочий pane;
- result-meta, toolbar, grid/diff/status tables и placeholders подаются плотнее и строже;
- data/diff/status tabs сохраняют текущую функциональность, но перестают выглядеть как набор разрозненных widgets;
- IDE-like приоритет result pane усиливается без нового banner/noise слоя.

Что сделано:

- result-meta summary собран в компактный pane-style block вместо набора разрозненных muted lines;
- data grid toolbar, table chrome и placeholders уплотнены и стали ближе к строгому IDE/data-grid виду;
- diff summary cards и diff table переведены на более плотную, менее декоративную подачу;
- status pane получил собственный строгий table chrome вместо общего bootstrap-like вида;
- status/event log boxes уплотнены, чтобы output зона читалась как связанная часть одного result pane.

#### 13.7. Status bar / working status line

Статус:

- реализовано

Проблема:

- после cleanup shell/result-pane внизу workspace все еще не хватает одного компактного operational слоя, который быстро отвечает на вопрос:
  - по скольким source сейчас идет работа;
  - какой transaction mode активен;
  - включена ли strict safety;
  - в каком result context пользователь сейчас находится;
  - что происходит с текущим execution;
- при этом возвращать старый дублирующий context block нельзя.

Текущая зона:

- [SqlConsoleWorkspacePanelSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleWorkspacePanelSections.kt)
- [SqlConsoleModels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleModels.kt)
- [SqlConsoleLabels.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleLabels.kt)
- [20-sql-console.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/20-sql-console.css)

Целевой контракт:

- внизу workspace есть компактная status line;
- она не дублирует sidebar и не превращается в второй summary-block;
- status line показывает только полезные operational сигналы:
  - selected sources;
  - transaction mode;
  - strict safety;
  - result context;
  - execution state;
- `pending commit` и failure states остаются визуально заметными.

Что сделано:

- внизу SQL workspace добавлена компактная working status line;
- она показывает только operational summary:
  - количество выбранных source;
  - transaction mode;
  - strict safety;
  - текущий result/view context;
  - execution state;
- line использует компактные tone-coded items и не возвращает старый дублирующий context block;
- `pending commit`, `running`, `failed`, `rolled back` и `success` состояния execution остаются читаемыми в нижнем chrome.

Порядок реализации:

1. `Object inspector direct-load hardening`:
   - сначала закрыть пакет `13.0`, потому что сейчас есть функциональный runtime-defect.
2. `Execution history на отдельный экран`:
   - затем закрыть пакет `13.1`, чтобы убрать текущий primary-screen bloat без смешения workspace.
3. `Workspace retention и stale cleanup`:
   - затем закрыть пакет `13.2`, чтобы multi-tab SQL-console не тащила бесконечный локальный state хвост.
4. `Source navigator redesign`:
   - перевести левую колонку источников и групп ближе к navigator/tree-pane;
   - меньше card-like контейнеров на каждый source/group;
   - плотнее строки, чище row selection, честный explorer-like scan.
5. `Tool-window redesign` для правой secondary-зоны:
   - `Шаблоны и быстрые действия`, favorites и соседние secondary blocks должны выглядеть как IDE tool windows, а не как dashboard widgets;
   - меньше визуальной конкуренции с editor/result pane.
6. `Shell densification`:
   - уменьшить cardification, слишком мягкие фоны, крупные радиусы и избыточные внутренние отступы;
   - усилить ощущение pane/panel hierarchy вместо набора dashboard-карточек.
7. `Result pane densification`:
   - довести chrome вокруг result area до более строгого IDE/data-grid вида;
   - меньше декоративных toolbar/panel подложек, больше ощущения единой рабочей области.
8. `Status bar / working status line`:
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

### 14. Страница `О проекте`: минимизация до карточек разработчиков

Статус:

- реализовано

Проблема:

- текущая страница `О проекте` содержит лишние вводные content-блоки про назначение проекта и архитектурный фокус;
- пользовательский сценарий этой страницы сейчас проще: нужны карточки разработчиков, а остальная explanatory информация только раздувает экран.

Текущая зона:

- [AboutPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/about/AboutPage.kt)
- [AboutPageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/about/AboutPageSections.kt)

Целевой контракт:

- основной content страницы `О проекте` состоит только из карточек разработчиков;
- информационные блоки `Назначение`, `Фокус` и сходный explanatory content убираются;
- карточки разработчиков с именами, alias и текущим визуальным образом страницы остаются;
- page shell может остаться минимальным:
  - заголовок страницы;
  - навигационные действия;
  - без лишнего hero/dashboard content.

Что нужно сделать:

1. убрать лишние info-panels и chips из content страницы;
2. оставить developer cards как единственный основной content block;
3. при необходимости упростить hero/subtitle, чтобы они не дублировали удаленный explanatory content;
4. не ломать существующий route `/about`.

Что сделано:

- со страницы `О проекте` убран весь explanatory content:
  - `Назначение`;
  - `Фокус`;
  - chips и сопутствующие текстовые блоки;
- основной content страницы теперь состоит только из карточек разработчиков;
- hero/subtitle и навигационные действия упрощены, чтобы shell страницы не спорил с основным контентом;
- удалены мертвые `about-*` стили, которые больше не используются после минимизации страницы.

### 15. Kafka-инструмент: локальный cluster explorer и topic operations

Статус:

- частично реализовано

Цель:

- добавить в платформу отдельный локальный инструмент для работы с Kafka-кластерами из конфига;
- покрыть основные инженерные сценарии:
  - обзор кластеров и топиков;
  - metadata по топику;
  - связанные consumer groups, member count и lag;
  - bounded reading сообщений;
  - controlled producing сообщений в топик;
- вести Kafka-инструмент как самостоятельную подсистему, а не как “еще одну карточку” на главной странице.

Проблема:

- сейчас платформа уже покрывает файловые/DB модули, SQL-консоль и maintenance flow, но не дает отдельного локального инструмента для Kafka;
- при этом сценарии Kafka отличаются от SQL-консоли:
  - источник подключения должен идти только из конфига;
  - читать нужно не просто “список сообщений”, а topic/partition/group metadata;
  - “количество консюмеров” и “consumer lag” честно моделируются как информация по consumer groups, а не как одно абстрактное число;
  - transport/auth contract должен покрывать реальные варианты подключения платформы:
    `PLAINTEXT` и `TLS/mTLS`, причем как через `JKS`, так и через `PEM`;
  - нельзя тащить в первый этап destructive admin-операции ради похожести на полноценный web-product.

Архитектурный контракт:

- Kafka-инструмент проектируется как отдельная подсистема по слоям:
  - `core`: Kafka models, contracts, topic/group/message operations;
  - `ui-server`: config resolution, orchestration, persisted local state только там, где он действительно нужен;
  - `ui-compose-shared`: screen/store/models/contracts;
  - `ui-compose-web`: page composition, browser glue, visual layout.
- подключение к Kafka идет только через UI config (`ui-application.yml` / внешний `application.yml`), без ad-hoc connection form в UI;
- config contract должен выглядеть максимально близко к стандартному Kafka client config:
  - `properties`-first;
  - реальные Kafka property names (`bootstrap.servers`, `security.protocol`, `ssl.*`);
  - без глубокого custom security DSL там, где хватает стандартных свойств клиента;
- secrets и auth material не живут в UI state и должны поддерживать тот же placeholder-resolution contract, что уже используется для SQL-консоли;
- первый auth/transport baseline Kafka подсистемы:
  - `PLAINTEXT`;
  - `TLS` c server certificate validation;
  - `mTLS`;
  - trust/identity material в форматах `JKS` и `PEM`;
- первый этап делается route-driven, а не через новый `workspaceId`-контур:
  - URL и screen state должны определять `cluster/topic/tab/filter`;
  - не копировать SQL-console multi-tab model без реальной необходимости.

Ограничения первого этапа:

- не добавлять create/delete topic, reset offsets, ACL management и другие destructive admin actions;
- не делать live tailing, бесконечный streaming consumer и implicit offset commit;
- не тянуть schema registry, protobuf/avro-specific tooling и cross-cluster diff до закрытия baseline;
- не тащить в первый auth-baseline `SASL_*`, пока не будет отдельного подтвержденного требования;
- не смешивать Kafka-tool state с SQL-console state и history.

Целевая экранная модель:

- основной route `/kafka`;
- структура ближе к `Kafka UI`, но упрощенная под локальное приложение:
  - выбор кластера;
  - список топиков;
  - экран топика с вкладками `Overview / Consumers / Messages / Produce`;
  - без лишних dashboard-плашек и без тяжелого multi-user chrome.

Ключевые зоны будущего изменения:

- [UiConfig.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/config/UiConfig.kt)
- [UiRuntimeConfigResolver.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/config/UiRuntimeConfigResolver.kt)
- [HomePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/home/HomePage.kt)
- [HomePageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/home/HomePageSections.kt)
- новые feature-пакеты `core/ui-server/ui-compose-shared/ui-compose-web` для Kafka boundary.

#### 15.0. Зафиксировать Kafka config contract и subsystem docs

Статус:

- реализовано

Что нужно сделать:

1. ввести новый config-block `ui.kafka`;
2. зафиксировать каталог кластеров без группировки по аналогии с SQL-console sources, если реальная потребность в cluster-groups не доказана;
3. поддержать placeholder-resolution для Kafka credentials через текущий runtime-config path;
4. ввести dependency baseline на Kafka client и спрятать concrete client creation за отдельным boundary/factory;
5. перейти на standard-looking `properties` contract для cluster connection:
   - обязательный thin shell только для `id / name / readOnly`;
   - connection properties хранить как стандартные Kafka keys;
   - не вводить свой отдельный nested DSL для `security`, если без него можно обойтись;
6. зафиксировать явную transport/security model:
   - `PLAINTEXT`;
   - `TLS`;
   - `mTLS`;
   - trust material в `JKS` и `PEM`;
   - client identity в `JKS` и `PEM`;
   - для `JKS` использовать стандартные Kafka SSL properties напрямую;
   - для `PEM` использовать стандартные Kafka property names, но разрешить file-backed values через placeholder resolution:
     - `ssl.truststore.certificates: "${file:/path/to/ca.crt}"`;
     - `ssl.keystore.certificate.chain: "${file:/path/to/client.crt}"`;
     - `ssl.keystore.key: "${file:/path/to/client.key}"`;
     - `ssl.key.password: "${KAFKA_CLIENT_KEY_PASSWORD}"`;
   - `ssl.truststore.location` / `ssl.keystore.location` оставлять стандартным путем для `JKS` и для тех PEM-случаев, где Kafka client действительно может читать file-based store напрямую;
   - запретить невалидные комбинации transport/material settings;
7. определить явные read/produce limits в конфиге:
   - `maxRecordsPerRead`;
   - `pollTimeoutMs`;
   - `adminTimeoutMs`;
   - `maxPayloadBytes`;
   - при необходимости default topic page size;
8. добавить отдельный reference/safety документ для Kafka subsystem с failure scenarios и scope boundaries.

Что сделано:

1. в `UiAppConfig` введен новый config-block `ui.kafka` с limits и catalog `clusters`;
2. Kafka cluster contract зафиксирован как `properties`-first:
   - thin shell `id / name / readOnly`;
   - connection settings как стандартные Kafka keys;
3. runtime resolver теперь умеет:
   - обычные `${KEY}` placeholders;
   - file-backed `${file:/path}` placeholders для PEM material;
4. добавлен validation support:
   - structural load validation для limits и cluster ids;
   - runtime validation для `PLAINTEXT / SSL`, `JKS / PEM` и недопустимых SSL-комбинаций;
5. добавлен Kafka client boundary в `ui-server`:
   - `UiKafkaClientPropertiesFactory`;
   - `UiKafkaClientFactory`;
   - concrete `kafka-clients` creation теперь спрятан за отдельным factory layer;
6. classpath `application.yml` и packaged `ui-application.example.yml` синхронизированы под новый `ui.kafka` contract;
7. добавлен reference/safety документ [KAFKA_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/KAFKA_FAILURE_SCENARIOS.md);
8. обновлены [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md) и [AGENTS.md](/Users/kwdev/DataPoolLoader/AGENTS.md), чтобы Kafka config и Kafka safety doc стали частью явной архитектурной карты.

Целевой YAML-скелет:

```yaml
ui:
  kafka:
    maxRecordsPerRead: 100
    pollTimeoutMs: 3000
    adminTimeoutMs: 5000
    maxPayloadBytes: 1048576
    clusters:
      - id: "dev"
        name: "DEV Kafka"
        readOnly: false
        properties:
          bootstrap.servers: "host1:9092,host2:9092"
          client.id: "datapool-loader"
          security.protocol: "SSL"
          ssl.truststore.type: "JKS"
          ssl.truststore.location: "/path/to/truststore.jks"
          ssl.truststore.password: "${KAFKA_TRUSTSTORE_PASSWORD}"
          ssl.keystore.type: "JKS"
          ssl.keystore.location: "/path/to/keystore.jks"
          ssl.keystore.password: "${KAFKA_KEYSTORE_PASSWORD}"
          ssl.key.password: "${KAFKA_KEY_PASSWORD}"
      - id: "dev-mtls-pem"
        name: "DEV Kafka mTLS PEM"
        readOnly: false
        properties:
          bootstrap.servers: "host1:9092,host2:9092"
          client.id: "datapool-loader"
          security.protocol: "SSL"
          ssl.truststore.type: "PEM"
          ssl.truststore.certificates: "${file:/path/to/ca.crt}"
          ssl.keystore.type: "PEM"
          ssl.keystore.certificate.chain: "${file:/path/to/client.crt}"
          ssl.keystore.key: "${file:/path/to/client.key}"
          ssl.key.password: "${KAFKA_CLIENT_KEY_PASSWORD}"
```

#### 15.1. Перегруппировать карточки главной страницы под новый инструмент

Статус:

- реализовано

Что нужно сделать:

1. разместить карточку Kafka в одном блоке с SQL-консолью как единый раздел ручной инженерной работы с источниками;
2. объединить `Справка` и `О проекте` в общий secondary-block;
3. не смешивать этот блок с модульными/maintenance потоками;
4. не превращать главную страницу в новый dashboard ради добавления Kafka.

Что сделано:

1. главная страница теперь держит три явных зоны:
   - модульный/maintenance поток;
   - manual tools block `SQL и Kafka`;
   - secondary info block `Справка и проект`;
2. `SQL-консоль` больше не висит отдельной карточкой вне смыслового блока, а собрана вместе с Kafka как единый раздел ручной инженерной работы;
3. `Справка` и `О проекте` объединены в общий info-block и больше не конкурируют с основными рабочими инструментами;
4. Kafka отражена на home-screen как полноценный primary tool платформы и ведет на отдельный `/kafka` screen;
5. текст карточки `О проекте` синхронизирован с фактическим экраном и больше не обещает лишний explanatory content.

#### 15.1.1. Выравнивание ширины primary-card на главной странице

Контекст:

- ручная проверка показала, что блок `Работа с модулями загрузки данных` на главной странице растянут на всю ширину grid, тогда как блоки `SQL и Kafka` и `Справка и проект` живут как обычные card-column;
- из-за этого home screen выглядит как смесь двух разных layout-контрактов вместо единой панели инструментов.

Что нужно сделать:

1. выровнять главный grid-контракт home screen так, чтобы основные блоки страницы читались как единообразные card-column;
2. убрать special-case растягивание блока `Работа с модулями загрузки данных` на всю ширину, если для этого нет отдельной продуктовой причины;
3. сохранить внутреннюю иерархию режима `Файлы / База данных` и cleanup-card, но не за счет ломания внешнего layout;
4. обновить visual baseline главной страницы после выравнивания layout.

#### 15.2. Metadata baseline: clusters, topics, topic overview

Статус:

- реализовано

Что нужно сделать:

1. добавить metadata-service/boundary для:
   - списка кластеров;
   - списка топиков;
   - topic overview;
   - partition summary;
   - retention/replication/basic config, если доступно без перегруза;
2. сделать основной topic list screen с filter/search;
3. добавить route/API contracts для cluster/topic navigation;
4. покрыть server-side metadata path тестами.

Что сделано:

1. в `core` добавлен явный contract `KafkaMetadataOperations` с моделями:
   - `KafkaToolInfo`;
   - `KafkaTopicsCatalog`;
   - `KafkaTopicOverview`;
   - `KafkaTopicPartitionSummary`;
2. в `ui-server` реализован config-backed metadata service поверх `AdminClient`, но concrete client вызовы спрятаны за отдельным admin facade;
3. добавлены API-routes:
   - `GET /api/kafka/info`;
   - `GET /api/kafka/topics?clusterId=...&query=...`;
   - `GET /api/kafka/topic-overview?clusterId=...&topic=...`;
4. добавлен page route `/kafka` и compose alias `/compose-kafka` c route params `clusterId/topic`;
5. в `ui-compose-web` появился основной Kafka screen:
   - cluster strip;
   - topic list с filter/search;
   - topic overview с partition summary и basic retention/cleanup metadata;
6. home Kafka-card активирована и ведет на `/kafka`, поэтому новый инструмент больше не висит placeholder-state;
7. metadata path покрыт server-side тестами:
   - unit test для config-backed metadata service;
   - route test для `/kafka` redirects и `/api/kafka/*`.

#### 15.3. Consumer groups, member count и lag по topic scope

Статус:

- реализовано

Что сделано:

1. `KafkaTopicOverview` расширен отдельным `consumerGroups` section contract, а не одним бессмысленным “числом консюмеров”;
2. для выбранного топика UI показывает связанные consumer groups с:
   - `member count`;
   - `total lag`;
   - partition-level lag через drill-down;
3. `ui-server` считает topic-scoped lag через `AdminClient`, но делает это partial-safe:
   - authorization/timeout на consumer groups не ломают весь topic overview;
   - section consumer groups может вернуться как `AVAILABLE`, `EMPTY` или `ERROR`;
4. отдельно покрыты состояния:
   - group metadata unavailable;
   - topic exists, но consumers нет;
   - timeout/authorization failure на consumer-group metadata;
5. добавлены server-side tests на normal lag summary и partial-access path.

#### 15.4. Bounded message browser

Статус:

- реализовано

Что сделано:

1. добавлен отдельный `KafkaMessageOperations` contract и server boundary для bounded reading сообщений, без смешения с metadata service;
2. реализованы три режима чтения:
   - `latest records`;
   - `from explicit offset`;
   - `from timestamp`;
3. UI стал partition-aware:
   - чтение идет по явно выбранной partition;
   - offset/timestamp controls меняются по read mode;
4. message browser работает через `assign + seek`, без `subscribe()`, без commit offsets и без background consumer session;
5. payload rendering ограничен `ui.kafka.maxPayloadBytes`, с честным `truncated` сигналом;
6. message presentation показывает:
   - key;
   - value;
   - headers;
   - timestamp;
   - partition;
   - offset;
   - JSON pretty-print best-effort для text payload.

#### 15.4.1. Topic-wide message read mode across all partitions

Статус:

- реализовано

Что сделано:

1. в message browser добавлен явный scope selector:
   - `selected partition`;
   - `all partitions`;
2. режим `all partitions` читает сообщения по topic scope через тот же bounded consumer path, без background session и без commit offsets;
3. `limit` трактуется как общий размер merged result, а не как неограниченный fan-out по partition;
4. merged result явно показывает `partition` и `offset` у каждой записи;
5. server-side и shared/store tests покрывают topic-wide request mapping и UI-state contract.

#### 15.5. Controlled produce flow

Статус:

- реализовано

Что сделано:

1. добавлена отправка одного сообщения в topic через отдельный `KafkaProduceOperations` boundary;
2. baseline поддерживает `key`, `headers`, `partition override` и `payload text`;
3. read-only cluster блокирует produce path до Kafka client call;
4. UI и server contract возвращают честный delivery result:
   - `topic`;
   - `partition`;
   - `offset`;
   - `timestamp`;
5. batch produce и file-import в baseline не добавлялись.

#### 15.6. Kafka UI state, route model и local persistence

Статус:

- реализовано

Что сделано:

1. зафиксирован route-driven contract для `/kafka`:
   - `clusterId`;
   - `topic`;
   - `query`;
   - `pane`;
   - `scope`;
   - `mode`;
   - `partition`;
2. Kafka screen не получил отдельный `workspace` layer и не повторяет SQL-console model без нужды;
3. browser-local drafts для message browser, produce и settings остаются transient и не стали скрытым persisted state;
4. [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md) синхронизирован под Kafka page state/recovery contract.

#### 15.7. Regression coverage и visual baseline для Kafka subsystem

Статус:

- частично реализовано

Что сделано:

1. добавлены server-side tests на:
   - metadata;
   - all-partitions read;
   - controlled produce;
   - settings load/save/test routes;
2. добавлены shared/store tests на:
   - route-driven load state;
   - topic-wide read request mapping;
   - produce request mapping;
   - settings workflow state transitions;
3. добавлены browser-level visual baselines для:
   - Kafka message browser shell;
   - produce pane;
   - settings pane.

Что осталось:

1. довести visual coverage для явных `empty/error` состояний Kafka screen.

#### 15.8. Kafka config settings UI для cluster connection editing

Статус:

- реализовано

Что сделано:

1. добавлен отдельный settings pane для редактирования Kafka cluster catalog в управляемом `ui-application.yml`;
2. первый scope узкий и практичный:
   - add/edit/remove cluster;
   - `id / name / readOnly`;
   - `bootstrap.servers`;
   - `client.id`;
   - transport/security fields для `PLAINTEXT` и `SSL`;
   - `JKS` paths;
   - file-backed PEM values через `${file:/...}`;
3. settings UI не стал generic editor всех Kafka properties:
   - поддерживаемый поднабор редактируется явно;
   - unknown `additionalProperties` показываются и не теряются при сохранении;
4. сохранение идет через существующий `UiConfigPersistenceService`, без ad-hoc persistence path;
5. connection test добавлен прямо в settings pane;
6. server/store/browser coverage добавлена на update/save/test сценарии.

Критерий завершения:

- в платформе есть отдельный локальный Kafka-инструмент с config-driven cluster catalog;
- пользователь может безопасно пройти базовый цикл:
  - выбрать cluster;
  - открыть topic;
  - посмотреть overview;
  - увидеть consumer groups/member count/lag;
  - прочитать bounded набор сообщений;
  - отправить одно сообщение в topic;
- при необходимости пользователь может через UI поменять broker hosts и пути к `JKS/PEM` без ручного редактирования YAML;
- home screen отражает Kafka как один из основных инженерных инструментов платформы.

## P2

### 16. Усилить тестовую стратегию под архитектурную программу

Статус:

- частично реализовано

Цель:

- сделать архитектурный рефакторинг безопасным.

Что нужно сделать:

- добавить больше store-level tests;
- усилить server contract tests;
- добавить smoke-покрытие на критичные UI-сценарии;
- ввести измеримый coverage target для server-side:
  - минимум `80%` покрытия для server-side кода;
  - зафиксировать, чем именно меряем (`Kover`) и где проходит граница server-side coverage scope;
  - не ограничиваться общим report-only режимом, а довести это до явного контрольного критерия;
- добавить отдельный visual regression stream:
  - максимально полное browser-level покрытие визуала критичных экранов и состояний;
  - screenshot/Playwright regressions для основных user-facing screens;
  - визуальные тесты должны ловить layout drift, потерю блоков, поломку pane hierarchy и регрессии ключевых состояний;
- отдельно страховать:
  - SQL console lifecycle;
  - run lifecycle;
  - cleanup/retention;
  - runtime fallback scenarios.

Детализация:

1. `Server-side coverage floor >= 80%`
   - определить exact coverage scope:
     - `ui-server` route/support/service/state boundary;
     - при необходимости связанные server-side куски в `core`, если они участвуют в transport/runtime path;
   - собрать reproducible coverage report;
   - зафиксировать нижнюю границу не ниже `80%`;
   - добавить backlog-пакет на закрытие больших coverage дыр, если до порога не дотягиваем.

#### 16.1. Bounded server-side Kover coverage floor

Статус:

- реализовано

Проблема:

- текущий root-level `koverXmlReport` не подходит как контрольный критерий server-side coverage:
  - тащит весь проект;
  - запускает лишние `app`/`web` задачи;
  - поднимает `localPostgresTest`;
  - не дает полезного aggregated root XML для server-side gate;
- из-за этого coverage target в backlog есть, но reproducible bounded verification отсутствует.

Целевой контракт:

- server-side coverage floor измеряется через `Kover`;
- exact scope для этого floor фиксируется как `:ui-server`:
  - `ui-server` является server-side boundary/orchestration слоем;
  - `core` не считается server-side scope для этого контрольного порога, потому что это общий domain/runtime слой;
- должна существовать отдельная bounded команда:
  - строит XML/HTML coverage report только для `:ui-server`;
  - проверяет floor `>= 80%` по line coverage;
  - не опирается на пустой root XML report;
- контракт должен быть задокументирован в README и backlog.

Что сделано:

- в root Gradle добавлены bounded tasks:
  - `serverCoverageXmlReport`
  - `serverCoverageHtmlReport`
  - `verifyServerCoverageFloor`
- `verifyServerCoverageFloor` использует `Kover` XML report модуля `:ui-server` и валидирует line coverage floor `>= 80%`;
- exact scope зафиксирован явно:
  - server-side coverage floor сейчас относится к `:ui-server`;
  - `core` остается отдельным quality stream, но не входит в этот gate;
- README обновлен под новый reproducible workflow и больше не обещает старые `90%` targets, которые не совпадают с активным backlog-контрактом.

#### 16.2. Первый visual regression baseline для стабильных экранов

Статус:

- реализовано

Проблема:

- visual regression stream в backlog заявлен, но сейчас нет ни одного committed browser-level screenshot baseline;
- без первого стабильного baseline невозможно постепенно наращивать полноценный visual suite;
- для первого шага нельзя брать слишком шумные экраны вроде SQL-консоли с runtime data, иначе будут ложные диффы и хрупкий suite.

Целевой контракт:

- в проекте появляется первый committed Playwright screenshot baseline;
- coverage берется с наиболее стабильных user-facing экранов:
  - главная страница;
  - страница `О проекте`;
- smoke harness умеет запускать конкретный spec и `--update-snapshots`, чтобы baseline можно было поддерживать как инженерный workflow;
- visual пакет не должен ломать существующий SQL-console smoke flow.

Что сделано:

- в existing Playwright harness добавлен первый visual regression spec для:
  - `/`
  - `/about`
- committed baseline snapshots зафиксированы в репозитории;
- smoke launcher теперь умеет принимать `PLAYWRIGHT_TEST_ARGS`, чтобы запускать отдельный spec и обновлять snapshots без ad-hoc ручного редактирования script-а;
- visual stream стартовал со стабильных экранов, не смешивая первый baseline с шумными runtime-heavy страницами.

#### 16.3. Visual baseline для стабильных SQL-screen shell scenarios

Статус:

- реализовано

Проблема:

- после запуска visual stream покрытие ограничено только:
  - главной страницей;
  - страницей `О проекте`;
- SQL-консоль и object inspector уже сильно влияют на ежедневную работу, но сейчас не защищены screenshot-baseline-ами от layout drift и потери pane hierarchy;
- при этом нельзя сразу тащить в visual suite шумные result-heavy сценарии с runtime-sensitive output.

Целевой контракт:

- visual suite получает следующий bounded слой на стабильных SQL-screen сценариях:
  - основной shell SQL-консоли без запущенного execution;
  - экран объектов БД с загруженным inspector;
- baseline должен опираться на локально воспроизводимый seeded scenario и не зависеть от динамических run-duration/rows/transaction-state;
- visual assertions должны ловить:
  - поломку navigator pane;
  - деградацию editor/tool-window chrome;
  - потерю inspector pane structure и direct-load layout.

Что сделано:

- visual suite расширен следующими screenshot-baseline сценариями:
  - основной shell SQL-консоли без execution-heavy output;
  - экран объектов БД с direct-loaded inspector на фиксированной вкладке `columns`;
- baselines строятся на том же локальном seeded Postgres harness, что и browser smoke SQL-консоли;
- новый пакет не зависит от динамических duration/rows/result-state и потому остается устойчивым как regression guard на pane hierarchy и inspector layout.

#### 16.4. Visual baseline для module editor shell в файловом режиме

Статус:

- реализовано

Проблема:

- после `16.3` visual stream все еще не покрывает основной non-SQL рабочий экран проекта;
- `module editor` остается центральным экраном ежедневной работы, и его shell/layout пока не защищены от visual drift;
- для первого пакета нельзя брать create/publish/run scenarios, потому что они слишком stateful и дадут лишний шум.

Целевой контракт:

- visual suite получает стабильный screenshot-baseline для `module editor`:
  - файловый режим;
  - фиксированный `moduleId`;
  - без run-heavy или modal-heavy state;
- baseline должен ловить:
  - деградацию navigator sidebar;
  - поломку editor/content pane;
  - layout drift toolbar/run-overview shell.

Что сделано:

- visual regression spec расширен screenshot-baseline-ом для `module editor` в `files` mode;
- baseline фиксируется на явном `module=local-manual-test`, поэтому не зависит от auto-select policy и порядка каталога;
- screenshot покрывает основной shell экрана:
  - каталог модулей;
  - toolbar редактора;
  - content pane без запуска run/modal scenarios.

#### 16.5. Visual baseline для module runs empty-state

Статус:

- реализовано

Проблема:

- visual suite пока покрывает shell главных рабочих экранов, но еще не страхует ключевые empty-state сценарии;
- `module runs` является важным operational экраном, и регрессия его empty-state легко останется незамеченной без browser-level baseline.

Целевой контракт:

- visual regression suite получает screenshot-baseline для `module runs`:
  - `files` mode;
  - фиксированный `moduleId`;
  - воспроизводимый сценарий без сохраненной истории запусков;
- baseline должен ловить:
  - поломку overview strip;
  - деградацию hero/shell компоновки;
  - потерю или разъезд empty-state блока истории запусков.

Что сделано:

- visual suite расширен screenshot-baseline-ом для `module runs` empty-state;
- baseline фиксируется на явном `module=local-manual-test` в `files` mode;
- regression guard теперь покрывает:
  - hero и shell компоновку экрана истории запусков;
  - overview strip;
  - empty-state блок `Для этого модуля запусков пока нет.`

#### 16.6. Visual baseline для module sync shell

Статус:

- реализовано

Проблема:

- maintenance/database import screen пока не защищен visual baseline-ом;
- layout drift на экране импорта модулей из файлов может остаться незамеченным, хотя это один из основных operational screens DB-mode ветки.

Целевой контракт:

- visual suite получает screenshot-baseline для `module sync`;
- baseline должен опираться на стабильный shell/fallback scenario без запуска sync-action;
- regression guard должен ловить:
  - поломку overview/actions pane;
  - разъезд history/details layout;
  - потерю runtime/fallback messaging.

Что сделано:

- для экрана импорта модулей добавлен стабильный shell target `module-sync-content-shell`;
- visual spec расширен отдельным baseline-сценарием для `/compose-sync`;
- committed snapshot `module-sync-shell` добавлен в visual suite и проходит browser regression.

#### 16.7. Visual baseline для run history cleanup shell

Статус:

- реализовано

Проблема:

- cleanup/retention экран пока не входит в committed visual suite, хотя это чувствительный operational screen;
- визуальная деградация safeguard/preview layout здесь слишком рискованна, чтобы оставлять ее без baseline-а.

Целевой контракт:

- visual suite получает screenshot-baseline для `run history cleanup`;
- baseline должен покрывать shell без destructive action execution;
- regression guard должен ловить:
  - разъезд cleanup и output-retention секций;
  - потерю safeguard controls;
  - поломку warning/fallback message area.

Что сделано:

- для cleanup screen добавлен стабильный shell target `run-history-cleanup-content-shell`;
- visual spec расширен screenshot-baseline-ом для `/run-history-cleanup`;
- committed snapshot `run-history-cleanup-shell` добавлен в regression suite.

#### 16.8. Visual baseline для SQL console history empty-state

Статус:

- реализовано

Проблема:

- отдельный экран истории SQL-консоли уже вынесен в product flow, но сейчас не защищен visual baseline-ом;
- empty-state этого экрана важен, потому что именно он чаще всего встречается в новом `workspace`.

Целевой контракт:

- visual suite получает screenshot-baseline для `sql-console-history` в новом пустом workspace;
- baseline должен быть workspace-scoped и не опираться на существующую execution history;
- regression guard должен ловить:
  - поломку screen header/summary;
  - потерю empty-state copy;
  - деградацию отдельного history-screen layout.

Что сделано:

- для history route добавлен стабильный shell target `sql-history-content-shell`;
- visual spec получил отдельный workspace-scoped empty-state baseline для нового `workspaceId`;
- committed snapshot `sql-console-history-empty-shell` добавлен в regression suite.

#### 16.9. Visual baseline для SQL object inspector error-state

Статус:

- реализовано

Проблема:

- visual suite уже страхует normal shell inspector scenario, но не держит его error-ветку;
- если direct inspector metadata path сломается, layout warning/empty-state может деградировать незаметно, хотя это важный operational сценарий для SQL-console object flow.

Целевой контракт:

- visual suite получает screenshot-baseline для `sql object inspector` в error-state;
- baseline должен воспроизводиться детерминированно через browser intercept, а не через случайный backend-failure;
- regression guard должен ловить:
  - поломку error banner / empty-state inspector path;
  - потерю search+inspector split layout;
  - деградацию `sql-object-content-shell` при failed metadata load.

Что сделано:

- visual spec расширен browser-level сценарием `sql object inspector error-state`;
- error-state воспроизводится детерминированно через Playwright intercept/abort inspector metadata request;
- committed snapshot `sql-object-inspector-error-shell` добавлен в regression suite;
- заодно стабилизированы соседние visual baselines `module runs empty-state` и `module sync shell`, чтобы suite не дрейфовал из-за page scrollbar-зависимости.

#### 16.10. Visual baseline для module sync runtime-fallback state

Статус:

- реализовано

Проблема:

- visual suite уже держит normal shell `module sync`, но не страхует его деградированный operational режим;
- если `runtimeContext` падает в `FILES`/fallback, экран меняет banner, доступность кнопок и поведение secondary-panels, и эти регрессии сейчас можно пропустить незаметно.

Целевой контракт:

- visual suite получает screenshot-baseline для `module sync` в `runtime-fallback` state;
- fallback-state воспроизводится детерминированно через browser intercept, а не через случайную конфигурацию локальной БД;
- regression guard должен ловить:
  - поломку warning banner о недоступности database mode;
  - потерю disabled-state у primary sync actions;
  - деградацию `module-sync-content-shell` в non-database operational режиме.

Что сделано:

- visual spec расширен browser-level сценарием `module sync runtime-fallback`;
- fallback-state воспроизводится детерминированно через Playwright intercept для `runtime-context` и neutral `sync-state`;
- committed snapshot `module-sync-runtime-fallback-shell` добавлен в regression suite;
- заодно normal `module sync shell` и `sql console shell` переведены на фиксированные payload-ы, чтобы suite не зависел от живого user/runtime state.

#### 16.11. Visual baseline для run history cleanup runtime-fallback state

Статус:

- реализовано

Проблема:

- visual suite уже держит normal shell `run history cleanup`, но не страхует его деградированный runtime-fallback режим;
- если maintenance screen работает вне ожидаемого database-mode/fallback context, warning banner и preview-panels меняют подачу, и эти регрессии сейчас можно пропустить.

Целевой контракт:

- visual suite получает screenshot-baseline для `run history cleanup` в runtime-fallback state;
- fallback-state воспроизводится детерминированно через browser intercept для `runtime-context` и preview payload-ов;
- regression guard должен ловить:
  - поломку warning banner о runtime fallback;
  - деградацию `run-history-cleanup-content-shell` в non-primary operational режиме;
  - потерю предсказуемой panel hierarchy при fallback-state.

Что сделано:

- visual spec расширен browser-level сценарием `run history cleanup runtime-fallback`;
- fallback-state воспроизводится детерминированно через Playwright intercept для `runtime-context`, cleanup preview и output preview;
- committed snapshot `run-history-cleanup-runtime-fallback-shell` добавлен в regression suite;
- для visual-suite введен стабильный inner target `run-history-cleanup-panels-shell`, чтобы banner и panel hierarchy проверялись раздельно и не зависели от outer shell drift.

#### 16.12. Visual baseline для module runs selected-run state

Статус:

- реализовано

Проблема:

- visual suite сейчас держит только `module runs empty-state`, но не покрывает основной рабочий двухпанельный сценарий;
- именно `selected/active pane` указан в общей цели секции `16` как отдельный обязательный тип UI-состояния;
- если сломаются active-row highlight, split layout или details-pane, текущий suite этого не заметит.

Целевой контракт:

- visual suite получает screenshot-baseline для `module runs` в состоянии, где выбран конкретный запуск и загружены его детали;
- state должен воспроизводиться детерминированно через browser intercept для session/history/details payload-ов;
- regression guard должен ловить:
  - поломку active-state в левой history-pane;
  - деградацию split layout `history -> details`;
  - потерю ключевых detail blocks в правой pane.

Критерий готовности:

- visual spec расширен сценарием `module runs selected-run state`;
- committed snapshot `module-runs-selected-shell` добавлен в regression suite.

Что сделано:

- visual spec расширен browser-level сценарием `module runs selected-run state`;
- selected/active pane воспроизводится детерминированно через Playwright intercept для `runtime-context`, session, history и details payload-ов;
- committed snapshot `module-runs-selected-shell` добавлен в regression suite;
- заодно стабилизированы flaky baselines `sql console shell` и `module sync runtime-fallback`, чтобы suite не дрейфовал из-за viewport/scrollbar и live runs history.

#### 16.13. Visual baseline для module editor loading-state

Статус:

- реализовано

Проблема:

- visual suite сейчас держит только стабильный shell `module editor`, но не страхует его initial loading-path;
- `loading` явно указан в общей цели секции `16`, и для центрального рабочего экрана это один из самых дорогих пропусков;
- если сломаются initial shell hierarchy, loading card или ранний split layout, текущий suite этого не заметит.

Целевой контракт:

- visual suite получает screenshot-baseline для `module editor` в initial loading-state;
- loading-state воспроизводится детерминированно через browser intercept с удержанием initial catalog/session request, а не через случайную медленную среду;
- regression guard должен ловить:
  - потерю `LoadingStateCard` в правой editor-pane;
  - деградацию `module-editor-content-shell` во время initial load;
  - поломку ранней sidebar/editor hierarchy до прихода session payload.

Критерий готовности:

- visual spec расширен сценарием `module editor loading-state`;
- committed snapshot `module-editor-loading-shell` добавлен в regression suite.

Что сделано:

- visual spec расширен сценарием `module editor loading-state`;
- loading-state воспроизводится детерминированно через browser intercept с удержанием initial `modules/catalog` request;
- committed snapshot `module-editor-loading-shell` добавлен в regression suite;
- заодно нормализован baseline `sql object inspector shell`: normal inspector теперь использует deterministic `search/inspect` payload и больше не зависит от live metadata response;
- полный visual suite подтвержден обычным прогоном без `--update-snapshots`.

#### 16.14. Visual baseline для SQL console history populated-state

Статус:

- реализовано

Проблема:

- visual suite сейчас покрывает только `sql-console-history` empty-state, но не держит основной action-ready сценарий с реальными execution entries;
- если деградируют row layout, status chips, source summary или action-кнопки `Подставить / Повторить`, current baseline этого не заметит;
- отдельный экран истории уже стал частью product flow и должен быть защищен не только в пустом workspace.

Целевой контракт:

- visual suite получает screenshot-baseline для `sql-console-history` в populated workspace-scoped state;
- state воспроизводится детерминированно через browser intercept для `executionHistory`, без зависимости от локального persisted storage;
- regression guard должен ловить:
  - поломку row-based list layout;
  - деградацию status/meta/source summary в history entry;
  - потерю action affordance `Подставить / Повторить`.

Критерий готовности:

- visual spec расширен сценарием `sql console history populated-state`;
- committed snapshot `sql-console-history-populated-shell` добавлен в regression suite.

Что сделано сейчас:

- visual spec расширен сценарием `sql console history populated-state`;
- history populated-state воспроизводится детерминированно через browser intercept для `runtime-context`, `sql-console/info`, `state` и `execution history`;
- empty/populated history screen переведены на фиксированные `workspaceId`, чтобы summary chip не дрейфовал между прогонами;
- часть shell-heavy baseline-ов дополнительно нормализована test-only gutter/overflow sizing.

Открытый блокер:

- блокер снят: после дополнительной normalizing-волны full visual suite проходит и на `--update-snapshots`, и на обычном прогоне.

Что сделано финально:

- committed snapshot `sql-console-history-populated-shell` добавлен в regression suite;
- empty/populated history screen переведены на fixed `workspaceId`, чтобы summary chip не ломал baseline;
- populated history scenario подтвержден в составе полного visual suite.

#### 16.15. Visual target normalization для shell-heavy baseline

Статус:

- реализовано

Проблема:

- несколько screenshot-baseline все еще зависят от слишком крупного shell target, случайного scroll growth и viewport-sensitive layout;
- из-за этого `--update-snapshots` проходит, а обычный прогон затем может падать на соседних экранах без product change;
- пока это не исправлено, visual suite нельзя считать надежным regression guard.

Целевой контракт:

- shell-heavy baseline-ы переводятся на действительно bounded и repeatable target normalization;
- обычный прогон `ui-visual.smoke.spec.mjs` проходит после `--update-snapshots` без дрейфа snapshot-ов;
- стабилизируются как минимум:
  - `sql console shell`
  - `module runs empty-state`
  - `run history cleanup runtime-fallback`

Критерий готовности:

- visual spec получает final normalization для перечисленных unstable shell scenarios;
- полный suite проходит и на `--update-snapshots`, и на обычном прогоне.

Что сделано:

- shell-heavy baseline-ы переведены на более жесткий deterministic contract:
  - fixed payload где это было нужно;
  - fixed `workspaceId` для history screen;
  - test-only `scrollbar-gutter`, bounded `width/height` и `overflow` для крупных shell-target-ов;
  - explicit settle-wait для сценариев, где layout добирался после раннего render-phase;
- `sql object inspector error-state` больше не опирается на browser-level `route.abort("failed")`, а использует явный synthetic `500` response;
- normalizing-пакет закрыл дрейфующие baseline-ы:
  - `sql console shell`
  - `module runs empty-state`
  - `run history cleanup runtime-fallback`
  - соседние shell/error/history scenarios, которые цеплялись за те же visual timing issues;
- полный suite `ui-visual.smoke.spec.mjs` подтвержден двумя способами:
  - `--update-snapshots`
  - обычный прогон без update.

2. `Comprehensive visual regression coverage`
   - расширить существующий browser smoke harness до visual regression уровня;
   - покрыть как минимум:
     - главную страницу;
     - SQL-консоль;
     - экран объектов БД / inspector;
     - module editor;
     - module runs;
     - maintenance screens;
     - страницу `О проекте`;
   - отдельно проверить ключевые UI-состояния:
     - loading;
     - empty;
     - error/warning;
     - selected/active pane;
     - compact/collapsed states там, где они критичны.

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
