# DataPoolLoader Backlog

Текущий backlog сформирован после ревизии всех `.md`-документов проекта.

Исторические планы, roadmap и черновики схлопнуты в этот backlog. В рабочем виде оставлены только:

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
- [PROJECT_ARCHITECTURE_REVIEW.md](/Users/kwdev/DataPoolLoader/PROJECT_ARCHITECTURE_REVIEW.md)
- [BACKLOG_HISTORY.md](/Users/kwdev/DataPoolLoader/BACKLOG_HISTORY.md)
- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)
- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)

## Правила приоритизации

- `P0` — архитектурные и операционные задачи, которые прямо влияют на надежность и maintainability.
- `P1` — обязательный следующий слой после `P0`, без которого архитектурный порядок останется незавершенным.
- `P2` — среднесрочное усиление качества и developer experience.
- `P3` — отложенные продуктовые и UX-задачи, которые не должны обгонять архитектурную программу.

## P0

### 0. Архитектурная программа: вычистить `ui-server` как перегруженный boundary

Статус:

- не реализовано

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

Критерий завершения:

- `ui-server` снова выглядит как boundary/orchestration слой, а не как второй `core`;
- новые сценарии не требуют разрастания routes и `UiServerContext`.

### 1. Архитектурная программа: нормализовать error model

Статус:

- не реализовано

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

Критерий завершения:

- сервер перестает маскировать внутренние дефекты под пользовательские ошибки;
- UI может по типу ответа различать, что именно произошло.

### 2. Архитектурная программа: нормализовать state model

Статус:

- не реализовано

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

Критерий завершения:

- новые и существующие состояния можно классифицировать без двусмысленности;
- у проекта появляется ясная карта источников истины.

### 3. Архитектурная программа: разрезать giant UI files

Статус:

- не реализовано

Цель:

- снизить стоимость изменений во frontend;
- превратить крупные Compose-экраны в набор feature-блоков.

Первый приоритет:

- [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt)
- [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt)
- [ModuleRunsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPage.kt)
- [ModuleEditorConfigForm.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigForm.kt)

Что нужно сделать:

- выделить shell pages;
- вынести action-panels и visual sections в отдельные компоненты;
- уменьшить прямой объем page-файлов;
- не тащить дополнительную логику обратно в page/store после декомпозиции.

Критерий завершения:

- новые экраны не растут как монолиты;
- текущие giant-файлы разбиты до состояния, когда их можно нормально ревьюить и менять локально.

### 4. Архитектурная программа: разрезать `styles.css`

Статус:

- не реализовано

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

Критерий завершения:

- стили проекта разбиты по подсистемам;
- giant CSS-файл больше не является точкой концентрации UI-долга.

### 5. Архитектурная программа: провести cleanup legacy и мусора

Статус:

- не реализовано

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

Критерий завершения:

- в проекте уменьшено число legacy-paths;
- кодовая база чище, проще и с меньшим количеством “исторических хвостов”.

### 6. Архитектурная программа: довести SQL-консоль как самостоятельную подсистему

Статус:

- частично реализовано

Цель:

- перестать относиться к SQL-консоли как к обычному экрану;
- довести execution lifecycle, safety и архитектурные границы до устойчивого состояния.

Что уже есть:

- отдельные документы по failure scenarios и Monaco autocomplete;
- async execution;
- manual `Commit / Rollback`;
- object browser;
- IDE-like UX;
- отдельные support/contract-слои в backend.

Что нужно сделать:

- добить safety long-running execution;
- дочистить lifecycle pending commit / rollback;
- продолжить cleanup giant UI/page/store слоя SQL-консоли;
- закрепить SQL-консоль отдельными архитектурными и тестовыми инвариантами.

Критерий завершения:

- SQL-консоль рассматривается и поддерживается как самостоятельная подсистема, а не как “еще один экран”.

### 7. SQL-консоль: safety hardening по аварийным сценариям

Статус:

- не реализовано

Источник:

- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)

Цель:

- исключить orphan execution и висящие транзакции;
- довести SQL-консоль до состояния, где безопасность БД обеспечивается server-side механизмами, а не надеждой на живую вкладку браузера.

Что нужно сделать:

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

Критерий завершения:

- manual transaction не может жить бесконтрольно;
- при потере владельца система гарантированно уходит в безопасный rollback.

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

- не реализовано

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
- [PROJECT_ARCHITECTURE_REVIEW.md](/Users/kwdev/DataPoolLoader/PROJECT_ARCHITECTURE_REVIEW.md)
- [AGENTS.md](/Users/kwdev/DataPoolLoader/AGENTS.md)

Что нужно сделать:

- продолжать обновлять эти документы при изменении архитектурных инвариантов;
- не допускать расхождения между кодом и repo-level правилами;
- при заметных архитектурных изменениях обновлять документацию в том же change set.

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

- не реализовано

Источник:

- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)

Цель:

- развивать Monaco постепенно, без тяжелого IDE/LSP-стека и без ущерба для архитектуры SQL-консоли.

Этапы:

- уровень 1:
  - local completion по keywords/functions/snippets;
  - hover hints;
- уровень 2:
  - metadata-aware autocomplete по schema/table/view/index names;
  - bounded search-first completion по объектам БД;
  - exact object column lookup там, где это оправдано;
- уровень 3:
  - deeper language intelligence только при доказанной необходимости.

Критерий завершения:

- Monaco становится ощутимо полезнее;
- autocomplete не ломает execution lifecycle и не тащит тяжелую stateful backend-механику.

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
