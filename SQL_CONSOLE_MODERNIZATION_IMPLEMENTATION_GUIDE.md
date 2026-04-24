# SQL Console Modernization Implementation Guide

Дата: 2026-04-25

Назначение документа:

- зафиксировать практический план переноса согласованного SQL-console mockup в product UI;
- сохранить существующую функциональность SQL-консоли при переписывании интерфейса;
- определить порядок работ, границы компонентов, проверки и stop-criteria.

Этот документ является implementation contract для backlog section 18:

- [BACKLOG.md](/Users/kwdev/DataPoolLoader/BACKLOG.md)

Исходный визуальный артефакт:

- [design/sql-console-modernization/index.html](/Users/kwdev/DataPoolLoader/design/sql-console-modernization/index.html)

Обязательные связанные документы:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md)
- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)
- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)

## 1. Главный принцип переноса

Макет переносится не как новая страница "с нуля", а как визуальная оболочка поверх уже работающих контрактов SQL-консоли.

Запрещено при redesign:

- переписывать execution lifecycle только ради UI;
- удалять owner-session, owner-token, heartbeat, release, cancel, commit, rollback контракты;
- заменять текущую result grid на упрощенную mock-table;
- убирать Monaco object autocomplete, hover/actions, statement markers и object navigation;
- смешивать source settings, execution history, workspace state и credentials в один новый state bucket;
- добавлять orchestration-логику в route handlers или page composition files;
- возвращать `credential.properties` block на главный SQL workspace.

Разрешено:

- менять visual shell и CSS;
- переставлять существующие компоненты;
- дробить UI sections на более узкие Compose-компоненты;
- добавлять thin view adapters для combined result grid;
- расширять server contracts только отдельными bounded задачами, если UI-контракт уже требует новой операции.

## 2. Что уже есть и должно сохраниться

### 2.1. Page shell и workspace

Текущая точка входа:

- [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt)
- [SqlConsolePageContentSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageContentSections.kt)

Сейчас страница использует `PageScaffold` с hero. Target layout должен заменить большой hero на compact tool header с SQL tool icon, но не должен ломать вычисления:

- `currentExecution`;
- `statementResults`;
- `activeStatementResult`;
- `statementAnalysis`;
- `scriptOutline`;
- `currentOutlineItem`;
- `pendingManualTransaction`;
- `exportableResult`;
- `activeExportShard`.

Эти вычисления остаются в page-level composition layer, пока не появится отдельная причина вынести их в support-файл.

### 2.2. Source navigator

Текущие файлы:

- [SqlConsoleSourceSidebarSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleSourceSidebarSections.kt)
- [SqlConsoleSourceSelectionSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleSourceSelectionSections.kt)

Нужно сохранить:

- выбор groups;
- выбор отдельных sources;
- partial/selected state groups;
- per-source connection status;
- explicit `Проверить подключение`;
- настройки `maxRowsPerShard` и `queryTimeoutSec`;
- сохранение selected sources в workspace state.

Нужно убрать с главного workspace:

- file chooser для `credential.properties`;
- upload credentials action;
- имя выбранного credentials-файла;
- подробные placeholder diagnostics.

На главном workspace при credentials-проблемах допустимы только:

- compact warning/error status на source;
- action/link на будущий экран настроек sources.

### 2.3. Editor, Monaco и object navigation

Текущие файлы:

- [SqlConsoleWorkspacePanelSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleWorkspacePanelSections.kt)
- [SqlConsoleEditorBindings.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleEditorBindings.kt)
- [SqlConsolePageEffects.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageEffects.kt)
- [SqlConsoleMonacoMetadataSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleMonacoMetadataSupport.kt)
- [SqlConsoleMonacoObjectNavigationSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleMonacoObjectNavigationSupport.kt)
- [SqlConsoleMonacoStatementMarkerSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleMonacoStatementMarkerSupport.kt)

Нужно сохранить:

- `MonacoEditorPane`;
- `sqlObjectNavigation = true`;
- `glyphMargin = true`;
- `onEditorReady`;
- `onValueChange`;
- cursor line tracking;
- selected SQL tracking;
- current statement detection;
- format SQL;
- open new SQL tab;
- favorites/recent insert/apply flows;
- object metadata navigation from editor;
- open `SELECT` in a new SQL-console workspace tab.

Нельзя:

- делать server-side stateful autocomplete session;
- грузить весь catalog при открытии страницы;
- отключать bounded metadata search-first autocomplete.

### 2.4. Execution controls

Текущие файлы:

- [SqlConsoleWorkspaceToolbarSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleWorkspaceToolbarSections.kt)
- [SqlConsoleExecutionBindings.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleExecutionBindings.kt)
- [SqlConsolePageBindings.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageBindings.kt)

Нужно сохранить все команды:

- `EXPLAIN` current statement;
- `EXPLAIN ANALYZE` current statement;
- `EXPLAIN` selection;
- `EXPLAIN ANALYZE` selection;
- run current statement;
- run selection;
- run whole script;
- stop running execution;
- commit manual transaction;
- rollback manual transaction;
- autocommit toggle;
- strict safety toggle;
- page size setting.

Target UI меняет только presentation:

- primary run actions в compact toolbar;
- secondary actions в compact secondary zone;
- transaction state в compact safety/status strip;
- controls остаются disabled при тех же условиях, что и сейчас.

### 2.5. Result pane и grid

Текущие файлы:

- [SqlConsoleResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultSections.kt)
- [SqlConsoleDataResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleDataResultSections.kt)
- [SqlConsoleResultGridSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultGridSupport.kt)
- [SqlConsoleResultNavigatorSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultNavigatorSections.kt)
- [SqlConsoleStatusResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleStatusResultSections.kt)
- [SqlConsoleDiffResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleDiffResultSections.kt)

Текущая functional grid является основой новой таблицы. Нужно сохранить:

- result placeholder behavior;
- multi-statement navigation;
- `Данные` / `Статусы` tabs или их compact replacement;
- result view modes;
- per-source table view;
- pagination;
- wrap / nowrap;
- autosize columns;
- manual grow/shrink columns;
- copy cell;
- copy row;
- copy column;
- active cell;
- active row;
- active column;
- row index column;
- horizontal scroll;
- truncated/maxRows messaging;
- non-result command placeholder;
- diff mode;
- status pane.

Target result modes:

- `Общий grid` - default mode, combines rows from all successful sources and adds required `source` column.
- `По источникам` - existing per-source grid semantics with source selector.
- `Diff` - existing compare-view, separate from normal grid.

Implementation rule:

- combined grid must reuse the same table interaction model as per-source grid;
- do not fork a second simplified table;
- if shared grid extraction is needed, extract a reusable `SqlResultGridTable` first, then feed it either source rows or combined rows.

### 2.6. Export

Текущие файлы:

- [SqlConsoleExecutionBindings.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleExecutionBindings.kt)
- [SqlConsoleExportRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleExportRoutes.kt)
- [SqlConsoleExportService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleExportService.kt)

Текущее поведение:

- `/api/sql-console/export/source-csv` экспортирует один source CSV;
- `/api/sql-console/export/all-zip` экспортирует ZIP с отдельным CSV на каждый source;
- текущий export использует переданный result snapshot и поэтому наследует ограничения отображаемого результата.

Target UI placement:

- `Экспорт общий` выгружает один combined CSV с обязательной колонкой `source`;
- `Экспорт по sources` выгружает отдельные файлы по sources, текущий ZIP-контракт подходит как base behavior;
- export actions живут в result pane;
- до появления result actions видимы, но disabled;
- export для non-`RESULT_SET` disabled.

Open decision:

- финальная export-семантика отложена для отдельного обсуждения;
- export должен уметь выгружать полный набор данных, а не только limited rows из отображаемого result;
- current result snapshot нельзя считать достаточным final source of truth для full-data export;
- full export может потребовать отдельный server-side execution/cursor/streaming path, limits, temp-file lifecycle и long-running status model.

Если combined CSV отсутствует в server service, добавить bounded server operation:

- новый method в `SqlConsoleExportService`;
- новый route рядом с существующими export routes;
- web binding рядом с текущими export bindings;
- tests на CSV header, source column, empty successful shards, escaping.

Важно:

- этот bounded operation относится только к future export package после обсуждения full-data contract;
- во время визуального redesign не закреплять новую export semantics через limited result snapshot.

## 3. Целевая структура UI

### 3.1. Main SQL workspace

Первый viewport должен показывать:

- compact tool header;
- source navigator;
- SQL editor;
- execution toolbar;
- result pane.

Не должен показывать:

- большой hero;
- credentials upload block;
- длинные explanatory hints;
- marketing copy;
- крупный execution history block.

Рекомендуемая page composition:

- `ComposeSqlConsolePage` - state derivation, effects, callbacks, top-level route page.
- `SqlConsoleToolHeader` - compact header, icon, navigation, workspace/source/status chips.
- `SqlConsolePageContent` - layout grid only.
- `SqlConsoleSourceNavigatorPane` - dense source tree and source actions.
- `SqlConsoleWorkspacePanel` - editor, compact library controls, execution toolbar, result pane.
- `SqlConsoleResultPanel` - navigator, mode tabs, export actions, data/status content.

### 3.2. Source settings screen

Этот экран является отдельным backlog item, но main workspace redesign должен быть готов к нему.

Target source settings behavior:

- add/edit/delete source;
- edit groups;
- test one source;
- test all sources;
- configure `credential.properties` path;
- support credentials modes:
  - `System keychain`;
  - `Placeholders`;
- no `Plain text` UI mode;
- only active credentials mode fields are visible;
- password is masked while entered;
- `Показать пароль` affects only current unsaved draft;
- saved password is never returned to browser;
- source catalog remains config-backed through `ui-application.yml`;
- hot reload SQL info after save without losing current draft SQL.

## 4. Migration phases

### Phase 0. Documentation and gate

Goal:

- keep product code unchanged;
- finalize implementation contract;
- link guide from backlog;
- make future tasks follow this guide.

Exit criteria:

- this guide exists;
- backlog section 18 references it;
- no product code changed.

### Phase 1. Introduce shell components without behavior changes

Goal:

- create compact header and new layout containers;
- keep all existing callbacks and child components wired.

Suggested changes:

- add `SqlConsoleToolHeaderSections.kt`;
- update `ComposeSqlConsolePage` to stop using large hero art for SQL-console;
- keep existing `SqlConsolePageContent` child wiring;
- add CSS in SQL-specific styles only:
  - [20-sql-console.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/20-sql-console.css)
  - [22-sql-tool-windows.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/22-sql-tool-windows.css)
  - [41-sql-result-pane.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/41-sql-result-pane.css)

Do not:

- move execution bindings;
- change API DTOs;
- change persisted state.

### Phase 2. Rebalance workspace hierarchy

Goal:

- demote query library and favorite objects;
- make editor and result pane primary;
- preserve all query library actions.

Suggested changes:

- split `QueryLibraryBlock` into compact summary/actions sections if needed;
- keep recent/favorite flows in `SqlConsoleLibraryBindings`;
- keep favorite object insert/open/remove behavior;
- reduce nested card shells by CSS and small component extraction.

Exit criteria:

- run/current/selection/explain/favorites/recent still work;
- editor is visible without scrolling on standard desktop viewport.

### Phase 3. Dense source navigator

Goal:

- replace card-heavy source selection visual with dense tree/list.

Suggested changes:

- keep `SqlConsoleSourceSelectionBlock` logic;
- change row presentation and classes;
- keep `onToggleSourceGroup` and `onToggleSource`;
- remove `SqlConsoleCredentialsPanel` from main workspace wiring;
- show only compact warnings and link/action toward source settings.

Important:

- removing credentials UI from main workspace must not delete server credentials support until source settings replacement exists;
- if callbacks become unused on main page, remove them in a dedicated cleanup after source settings is implemented.

### Phase 4. Execution toolbar and safety strip

Goal:

- make execution controls feel like tool controls, not cards.

Suggested changes:

- preserve `SqlConsoleWorkspaceToolbar` command set;
- change grouping/presentation;
- keep all disabled conditions;
- keep `ExecutionStatusStrip`;
- improve visual placement of pending commit, rollback timeout, failure and running states.

Exit criteria:

- manual transaction flow remains identical;
- commit/rollback disabled/enabled states match current rules.

### Phase 5. Result panel and grid extraction

Goal:

- implement `Общий grid`, `По источникам`, `Diff` without losing current grid features.

Suggested sequence:

1. Extract reusable grid rendering from `SelectResultPane` into a component that accepts:
   - `gridKey`;
   - `columns`;
   - `rows`;
   - `rowCount`;
   - `pageSize`;
   - `currentPage`;
   - copy/autosize/wrap state scope.
2. Rewire existing per-source view to use the extracted grid with no behavior changes.
3. Add combined-row adapter:
   - filter successful shards with rows;
   - prepend `source` column;
   - preserve original row values;
   - compute combined row count from actual combined rows.
4. Add result mode labels:
   - `Общий grid`;
   - `По источникам`;
   - `Diff`.
5. Keep `Diff` delegation to `SqlConsoleDiffResultPane`.

Do not:

- duplicate grid toolbar logic;
- copy only visible rows for column copy if existing behavior copies all rows in shard;
- break page selection when switching statement or mode.

### Phase 6. Export placement and combined export

Goal:

- move export actions to result pane without prematurely locking final full-data export semantics.

Suggested sequence:

1. Reserve result-pane placement for `Экспорт общий` and `Экспорт по sources`.
2. Keep UI disabled states explicit for running/empty/non-result output.
3. Do not implement new final export semantics during pure UI redesign.
4. Do not define full export as "download current limited result snapshot".
5. Return to backlog item `18.10` before changing server export contracts.

### Phase 7. Related screens consistency

Goal:

- align SQL objects, inspector and history with new visual language after main workspace stabilizes.

Scope:

- [SqlConsoleObjectsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectsPage.kt)
- [SqlConsoleObjectInspectorSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleObjectInspectorSections.kt)
- [SqlConsoleHistoryPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleHistoryPage.kt)

Rules:

- history remains separate screen;
- object inspector direct-load remains;
- editor-native navigation remains;
- no broad object catalog redesign in the same patch as main workspace rewrite.

### Phase 8. Source settings and secrets

Goal:

- implement source catalog editor and remove credentials management from main workspace completely.

Rules:

- source of truth remains `ui-application.yml`;
- config persistence goes through a server-side boundary, not route handler logic;
- secrets go through provider abstraction;
- no raw password response to browser;
- update [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md) if a new state boundary is introduced;
- update architecture docs if secret storage changes project invariants.

## 5. State boundaries

Do not introduce new persisted state unless the state type is explicit.

Current relevant state:

- workspace state: `draftSql`, selected groups, selected sources, last accessed;
- library state: recent queries, favorite queries, favorite objects;
- preferences state: page size, strict safety, transaction mode;
- execution history: completed execution summaries;
- active execution: process-local runtime state only;
- credentials upload state: currently separate local credentials state, target UI location changes but source of truth must remain explicit;
- SQL source catalog: operational config in `ui-application.yml`.

If redesign needs a new persisted preference, first classify it:

- UI preference;
- operational history;
- operational config;
- cache;
- transient runtime state.

Examples:

- active result mode can remain browser-local transient UI state unless there is a clear reason to persist it;
- column width state should stay local UI state unless user explicitly asks to persist table layout;
- source catalog changes are operational config, not SQL workspace state;
- execution history never restores active execution ownership.

## 6. File-level implementation map

Likely UI files to change:

- [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt)
- [SqlConsolePageContentSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePageContentSections.kt)
- [SqlConsoleSourceSidebarSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleSourceSidebarSections.kt)
- [SqlConsoleSourceSelectionSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleSourceSelectionSections.kt)
- [SqlConsoleWorkspacePanelSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleWorkspacePanelSections.kt)
- [SqlConsoleQueryLibrarySections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleQueryLibrarySections.kt)
- [SqlConsoleWorkspaceToolbarSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleWorkspaceToolbarSections.kt)
- [SqlConsoleResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultSections.kt)
- [SqlConsoleDataResultSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleDataResultSections.kt)
- [SqlConsoleResultNavigatorSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsoleResultNavigatorSections.kt)

Likely CSS files:

- [20-sql-console.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/20-sql-console.css)
- [22-sql-tool-windows.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/22-sql-tool-windows.css)
- [41-sql-result-pane.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/41-sql-result-pane.css)
- [40-sql-sources.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/40-sql-sources.css)
- [44-sql-objects.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/44-sql-objects.css)

Likely server files only for combined export/source settings phases:

- [SqlConsoleExportService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleExportService.kt)
- [SqlConsoleExportRoutes.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/SqlConsoleExportRoutes.kt)
- [UiConfigPersistenceService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/config/UiConfigPersistenceService.kt)
- [UiConfig.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/config/UiConfig.kt)

## 7. Regression checklist

Before each implementation patch:

- verify task exists in backlog;
- verify changed layer matches architecture rules;
- avoid expanding giant files with mixed responsibilities;
- keep unrelated dirty files untouched.

Manual UI checks after main workspace changes:

- SQL console loads with configured sources;
- no credentials upload block on main workspace;
- source groups toggle correctly;
- individual sources toggle correctly;
- connection check updates statuses;
- max rows and timeout save still work;
- editor keeps draft SQL after navigation/recomposition;
- recent query apply works;
- favorite query add/apply/remove works;
- favorite object insert/open/remove works;
- Monaco object autocomplete still appears;
- Monaco object navigation opens inspector/columns/select flow;
- format SQL works;
- run current statement works;
- run selection works;
- run all works;
- explain current works;
- explain analyze current works;
- explain selection works;
- explain analyze selection works;
- stop works during running execution;
- autocommit toggle persists;
- strict safety toggle persists;
- manual transaction enters pending commit when expected;
- commit works only for owner session;
- rollback works only for owner session;
- owner-loss/timeout statuses remain visible;
- multi-statement navigation works;
- status tab/pane works;
- `Общий grid` shows `source` column;
- `По источникам` preserves source selector;
- `Diff` still renders;
- pagination works;
- wrap/nowrap works;
- autosize works;
- manual grow/shrink works;
- copy cell/row/column works;
- active cell/row/column styling works;
- horizontal scroll works for wide results;
- empty state is compact;
- non-result command points to status view;
- export buttons are visible/disabled according to result state;
- full-data export is not implemented as limited result snapshot by accident;
- execution history screen opens separately.

Server/unit checks when server contracts change:

- export service tests after full-data export contract is designed;
- export route tests if route behavior changes;
- state store tests if persisted model changes;
- config persistence tests for source settings;
- secret provider tests with fake provider;
- hot reload tests for SQL info after source catalog save.

Browser/visual checks after UI stabilizes:

- main SQL workspace;
- result empty state;
- result combined grid;
- result per-source grid;
- result diff mode;
- source settings screen;
- DB object inspector;
- SQL execution history.

## 8. Stop criteria and rollback plan

Stop and reassess if:

- a visual change requires changing execution ownership semantics;
- source settings starts requiring raw password in browser responses;
- combined grid implementation duplicates large table logic instead of extracting shared grid;
- route handlers start accumulating config mutation logic;
- any change requires a new state file not represented in [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md);
- Monaco autocomplete/object navigation breaks during shell rewrite.

Rollback strategy:

- keep phases small;
- first patch should be shell-only and reversible;
- extract shared grid before changing behavior;
- keep old per-source result path working while adding combined mode;
- only remove old credentials UI after source settings has replacement behavior;
- commit after bounded phase according to repository workflow.

## 9. Definition of done

Main SQL-console modernization can be considered complete only when:

- compact header replaces hero;
- main workspace has no credentials upload block;
- source navigator is dense and usable;
- editor/result pane dominate visual hierarchy;
- all execution/explain/transaction actions still work;
- result pane has `Общий grid`, `По источникам`, `Diff`;
- existing grid feature parity is preserved;
- export action placement exists in result pane;
- full-data export contract is either implemented after separate discussion or explicitly left deferred;
- execution history remains a separate screen;
- source settings screen owns credentials/source catalog management;
- relevant tests/manual checks are completed;
- backlog section 18 tasks are moved to history after implementation.
