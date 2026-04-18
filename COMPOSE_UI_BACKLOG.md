# Compose UI Migration Backlog

## Цель

- перевести web-интерфейс `ui` на Kotlin Compose постепенно, без остановки текущего HTML/JS интерфейса;
- сохранять визуальный и поведенческий паритет с существующим UI на каждом шаге;
- не переписывать backend-контракты ради frontend-миграции.

## Базовые правила

- текущий HTML/JS интерфейс остается рабочим до завершения каждого экрана в Compose;
- для каждой страницы сначала достигается визуальный паритет, потом допускаются локальные улучшения;
- backend API и маршруты Ktor считаются источником истины;
- Compose-экран не должен придумывать собственный UX, если эквивалент уже есть в production UI;
- сложные экраны переводятся только после выделения общего API/state слоя;
- редакторный функционал уровня `Monaco` не переписывается на чистых Compose-компонентах;
- для `YAML/SQL` редакторов используется встраиваемый browser editor через JS interop:
  - основной кандидат `Monaco`;
  - fallback-кандидат `CodeMirror`;

## Архитектурный вектор

- текущий `ui-compose-web` остается рабочим web-front для миграции, но не считается конечной архитектурой;
- целевая модель: `shared + web + future desktop`, а не углубление в web-only Compose-код;
- для desktop не закладываем обязательный отдельный сервер:
  - desktop UI должен уметь работать как толстый клиент;
  - web остается transport-слоем через Ktor;
  - общий application/service слой должен обслуживать обе платформы;
- browser-specific код должен оставаться только в web-слое:
  - DOM composables;
  - browser routing;
  - `window.fetch`;
  - `Monaco` JS bridge;
  - `WebSocket` browser lifecycle;
- все, что не обязано знать про browser, постепенно выносится в будущий shared-слой:
  - screen models;
  - state/store логика;
  - route state модели;
  - formatters;
  - API contracts;
  - use-case orchestration для экранов.

## Кандидаты на перенос в будущий shared-слой

В первую очередь:

- `model/RuntimeModels.kt`
- `home/HomePageState.kt`
- `home/HomePageStore.kt`
- `module_runs/ModuleRunsModels.kt`
- `module_runs/ModuleRunsStore.kt`
- `module_runs/ModuleRunsFilters.kt`
- `module_runs/ModuleRunsSummary.kt`
- `module_runs/ModuleRunsFormatters.kt` в части pure labels/stage helpers
- `module_editor/ModuleEditorModels.kt`
- `module_editor/ModuleEditorStore.kt`
- `module_editor/ModuleEditorActions.kt`
- `module_editor/ModuleEditorConfigFormDtos.kt`

Оставить в web-слое:

- `foundation/http/ComposeHttpClient.kt`
- `foundation/updates/LiveUpdates.kt`
- `foundation/component/MonacoEditorPane.kt`
- `App.kt`
- `Main.kt`
- DOM-based page composables и route adapters.

Техническая оговорка по сборке:

- для стабильной сборки `Compose Web` production bundle добавлен root `gradle.properties` с повышенными JVM limits для Gradle и Kotlin daemon;
- иначе `:ui-compose-web:jsBrowserDistribution` начинает падать по памяти на `compileProductionExecutableKotlinJs`.

## Эпик 7. Переход к `ui-compose-shared`

Статус:

- в работе

Цель:

- перестроить Compose-ветку так, чтобы web был только одной платформенной реализацией;
- подготовить кодовую базу к будущему `ui-compose-desktop`, а не углублять web-only архитектуру.

Выполнено:

- `2026-04-18`: создан отдельный модуль [ui-compose-shared](/Users/kwdev/DataPoolLoader/ui-compose-shared);
- `2026-04-18`: `ui-compose-web` переключен на зависимость от `ui-compose-shared`;
- `2026-04-18`: в shared перенесены первые чистые срезы:
  - runtime/catalog модели;
  - `home` state/store/API contract;
  - `module-runs` models/filter state;
  - `module-editor` models;
  - `module-editor` action/config-form DTO.
- `2026-04-18`: в shared перенесен первый реальный store-слой:
  - `module-runs` API contract;
  - `module-runs` store;
  - summary parser для `summary.json`;
  - `module-editor` API contract;
  - `module-editor` store c вынесением browser navigation в web callback.
- `2026-04-18`: в shared вынесен первый слой pure formatters/helpers для `module-runs`:
  - переводы статусов;
  - переводы launch source и artifact labels;
  - определение активной стадии запуска;
  - summary counters.

Осталось:

- продолжить перенос pure logic:
  - `module_runs/ModuleRunsFormatters.kt` там, где нет web-specific API;
  - `module_editor` вспомогательные pure mappers/state helpers;
- вынести shared formatters, не завязанные на DOM/browser;
- оставить в web только:
  - HTTP transport;
  - browser routing;
  - DOM composables;
  - `Monaco` bridge;
  - websocket lifecycle.

## Эпик 8. Desktop shell

Статус:

- в работе

Цель:

- подготовить первую desktop-оболочку поверх того же `shared`-слоя, не смешивая ее с web-specific кодом;
- подтвердить, что `ui-compose-shared` реально пригоден для JVM/desktop-платформы.

Выполнено:

- `2026-04-18`: добавлен модуль `ui-compose-desktop`;
- `2026-04-18`: собран первый desktop-shell на Compose Desktop:
  - отдельное окно приложения;
  - использование `HomePageStore` из `ui-compose-shared`;
  - локальный preview transport без browser API;
  - переключение режима в рамках desktop-preview.

Осталось:

- добавить реальный desktop transport к Ktor backend вместо preview API;
- вынести platform-specific desktop navigation/window actions в отдельный слой;
- начать перенос первых экранов с общего `shared`-state на desktop UI.

## Definition of Done для экрана

- экран доступен по отдельному Compose-route;
- визуально повторяет production-экран по структуре, иерархии, отступам и цветовой логике;
- использует реальные backend endpoint-ы, без временных mock-данных;
- поддерживает те же переходы и базовые действия, что и production-экран;
- сборка `:ui-compose-web:jsBrowserDistribution` проходит стабильно;
- `:ui:test` продолжает проходить;
- экран можно сравнить side-by-side с production UI без явных расхождений по layout.

## Эпик 1. Главный экран

Статус:

- в работе

Цель:

- полностью перевести главную страницу на Compose;
- сделать визуал максимально близким к текущему `/`.

Что нужно сделать:

1. Повторить структуру текущего `home.html`:
   - hero-card;
   - alert доступа к режимам;
   - блок `Загрузка дата пулов`;
   - карточки `Файловый режим` и `DB режим`;
   - карточки `SQL-консоль` и `Справка`;
   - footer.
2. Повторить текущую визуальную иерархию:
   - фон страницы;
   - glass/panel стиль карточек;
   - hero-art с платформенной схемой;
   - slider режима;
   - disabled-state карточек.
3. Подтянуть текстовый слой:
   - те же названия;
   - те же подсказки;
   - те же тексты предупреждений по режиму доступа.
4. Дотянуть runtime mode parity:
   - `requestedMode`;
   - `effectiveMode`;
   - `fallbackReason`;
   - route guarding на уровне карточек.
5. После достижения паритета:
   - переключить `/compose-spike` из режима spike в полноценную Compose-версию главной;
   - затем решить, заменяет ли Compose главную страницу `/` или живет параллельно как отдельный route.

Критерии завершения:

- Compose-главная страница визуально повторяет production `/`;
- отличие допускается только на уровне технологии рендера, а не UX-модели;
- пользователь без подсказки не должен видеть, что это другой frontend.

Выполнено:

- `2026-04-18`: создан отдельный `ui-compose-web` модуль и route `/compose-spike`;
- `2026-04-18`: Compose-главная страница переведена на структуру текущего `home.html`:
  - `hero-card`;
  - alert по режиму;
  - блок `Загрузка дата пулов`;
  - карточки `Файловый режим` и `DB режим`;
  - карточки `SQL-консоль` и `Справка`;
  - footer;
- `2026-04-18`: Compose-главная страница использует production CSS-слой `styles.css`, а локальный `compose-spike.css` оставлен только для минимальных коррекций;
- `2026-04-18`: выровнено поведение runtime mode:
  - `requestedMode`;
  - `effectiveMode`;
  - `fallbackReason`;
  - `modeAccessError`;
  - disable-state карточек и переключателя режима.

Осталось:

- довести визуальный паритет до уровня production-экрана по мелким расхождениям;
- решить, заменяет ли Compose главную страницу `/` или пока остается отдельным route для сравнения.

## Эпик 2. Общий Compose frontend foundation

Статус:

- в работе

Цель:

- убрать ad-hoc fetch/state-логику из отдельных Compose-экранов;
- сделать основу для дальнейшего перевода экранов.

Что нужно сделать:

- выделить общий API client слой;
- выделить общий state/container слой для:
  - loading;
  - error;
  - polling;
  - websocket updates;
  - runtime mode context;
- выделить общие Compose-компоненты:
  - page shell;
  - section card;
  - status badge;
  - mode switch;
  - toolbar button;
  - alert/banner;
  - empty state;
  - loading state.

Выполнено:

- `2026-04-18`: выделен общий HTTP-клиент для Compose:
  - `get`;
  - `getOrNull`;
  - `postJson`;
- `2026-04-18`: выделены базовые foundation-компоненты:
  - alert/banner;
  - runtime mode switch;
  - общий helper для DOM class attributes;
- `2026-04-18`: добавлены общие foundation-компоненты layout/state:
  - page shell;
  - section card;
  - empty state;
  - loading state;
- `2026-04-18`: добавлен foundation для live-updates:
  - polling effect;
  - websocket effect;
- `2026-04-18`: логика главной страницы вынесена из root `App.kt` в отдельный home-slice:
  - `HomePageApi`;
  - `HomePageStore`;
  - `HomePageState`;
  - `ComposeHomePage`;
- `2026-04-18`: модели runtime/catalog вынесены в отдельный `model` пакет.

Осталось:

- при необходимости вынести runtime mode context в отдельный foundation-store;
- при необходимости добавить общий container для retry/reload сценариев без копирования по экранам.

## Эпик 3. Экран `История и результаты`

Статус:

- в работе

Цель:

- перевести на Compose общий экран `/module-runs`.

Что нужно сделать:

- единый Compose-экран для `FILES` и `DB`;
- сохранить текущую модель:
  - список запусков;
  - фильтры;
  - поиск;
  - summary;
  - source results;
  - artifacts;
  - timeline;
- сохранить транспортные различия:
  - `FILES` через websocket/live updates;
  - `DB` через polling/history API.

Выполнено:

- `2026-04-18`: добавлен Compose-route `/compose-runs`, который открывает тот же bundle с `screen=module-runs`;
- `2026-04-18`: в `ui-compose-web` добавлен screen routing по query-параметру `screen`;
- `2026-04-18`: добавлен базовый Compose-экран `История и результаты`:
  - загрузка `session` выбранного модуля;
  - загрузка списка запусков;
  - выбор запуска;
  - краткая summary выбранного запуска;
  - raw `summary.json`;
  - базовый блок событий;
- `2026-04-18`: экран работает на реальных endpoint-ах:
  - `GET /api/module-runs/{storage}/{id}`;
  - `GET /api/module-runs/{storage}/{id}/runs`;
  - `GET /api/module-runs/{storage}/{id}/runs/{runId}`.
- `2026-04-18`: в Compose-версии `module-runs` добавлены:
  - structured summary;
  - результаты по источникам;
  - артефакты запуска;
  - фильтры истории;
  - поиск по запускам;
  - выбор лимита `20 / 50 / 100`.
- `2026-04-18`: подключены live-updates без изменения backend-контрактов:
  - `FILES` через WebSocket `/ws` с перезагрузкой run-history;
  - `DB` через polling `module-runs` history/details при активном запуске.
- `2026-04-18`: перенесены основные production-визуальные элементы selected-run summary:
  - progress-widget по стадиям выполнения;
  - быстрые переходы к секциям результатов;
  - summary layout на тех же CSS-классах, что и в production UI.
- `2026-04-18`: усилен visual parity `module-runs` по вторичным блокам:
  - карточки истории запусков используют production-классы списка и meta-строк;
  - human-readable timeline перенесен на production-классы `human-log-entry*`;
  - добавлена `Техническая диагностика` с раскрытием и raw JSON событий.
- `2026-04-18`: добит еще один слой visual parity `module-runs`:
  - subtitle экрана синхронизирован с production отдельно для `FILES` и `DB`;
  - control-блок истории теперь повторяет production-модель `Показывать / Поиск`;
  - summary-строки переведены на production-классы `run-summary-*`;
  - блок артефактов переведен с таблицы на production-like карточки `run-artifact-*`.

Осталось:

- дочистить оставшиеся мелкие spacing/layout-различия с текущим `module-runs.html`;
- при необходимости дотянуть последние мелкие отличия quick actions и shell-подачи относительно production renderer.

## Эпик 4. Редактор модулей

Статус:

- в работе

Цель:

- перевести общий editor shell на Compose без расхождения между `FILES` и `DB`.

Порядок:

1. Сначала общий shell:
   - header;
   - toolbar;
   - tabs;
   - alert/status zones.
2. Затем вкладки:
   - `Настройки`;
   - `SQL`;
   - `application.yml`;
   - `Метаданные`.
3. Затем mode-specific embedded blocks:
   - файловые действия;
   - личный черновик и публикация для DB;
   - compact run widget.

Дополнительное архитектурное правило:

- вкладки `SQL` и `application.yml` в Compose-редакторе должны использовать embedded editor bridge;
- нативный Compose text area допускается только как временный fallback для простого просмотра, но не как целевой replacement текущего `Monaco`.

Выполнено:

- `2026-04-18`: добавлен Compose-route `/compose-editor`, который открывает тот же bundle с `screen=module-editor`;
- `2026-04-18`: в `ui-compose-web` добавлен базовый Compose `editor shell` для `FILES` и `DB`:
  - каталог модулей;
  - header редактора;
  - storage-aware toolbar;
  - alert по валидации;
  - вкладки `Настройки модуля / SQL / application.yml / Метаданные`;
- `2026-04-18`: shell работает на реальных endpoint-ах:
  - `GET /api/modules/catalog`;
  - `GET /api/db/modules/catalog`;
  - `GET /api/modules/{id}`;
  - `GET /api/db/modules/{id}`;
- `2026-04-18`: для tab content добавлен временный read-only preview:
  - credentials и placeholders;
  - SQL catalog preview;
  - raw `application.yml`;
  - metadata и DB lifecycle summary.
- `2026-04-18`: подключен первый embedded `Monaco` bridge в Compose editor shell:
  - `application.yml` открывается в `Monaco`;
  - выбранный SQL-ресурс открывается в `Monaco`;
  - локальное редактирование `application.yml` и SQL уже работает внутри Compose-state без сохранения в backend;
  - route и backend-контракты при этом не менялись.
- `2026-04-18`: подключены первые реальные действия editor shell поверх текущих backend API:
  - `FILES`: `Сохранить`, `Запустить`;
  - `DB`: `Сохранить черновик`, `Опубликовать`, `Сбросить черновик`, `Запустить`;
  - после успешных действий Compose-shell перезагружает session модуля и показывает feedback banner.
- `2026-04-18`: вкладка `Настройки модуля` перестала быть заглушкой:
  - Compose-форма теперь работает через те же `parse/apply` endpoint-ы `config-form`, что и production UI;
  - поддержаны базовые секции:
    - общие настройки;
    - SQL по умолчанию;
    - источники;
    - квоты;
    - целевая загрузка;
  - изменение формы обновляет реальный `application.yml` внутри Compose editor shell;
  - ручное редактирование YAML и форма синхронизируются через явное действие `Перечитать из application.yml`.
- `2026-04-18`: вкладка `Метаданные` переведена с preview на рабочую Compose-форму:
  - `title`;
  - `description`;
  - `tags`;
  - `hiddenFromUi`;
  - сохранение использует тот же `save/saveWorkingCopy` контракт, что и основной UI.
- `2026-04-18`: в Compose editor добавлен parity-блок `credential.properties`:
  - показывается текущий статус файла;
  - показываются предупреждения по обязательным placeholders и отсутствующим ключам;
  - загрузка `credential.properties` выполняется прямо из Compose editor через тот же backend endpoint;
  - после upload session модуля перечитывается и блок сразу показывает обновленный статус.
- `2026-04-18`: SQL-вкладка в Compose editor переведена из preview в рабочий каталог ресурсов:
  - `Создать SQL`;
  - `Переименовать` с переносом ссылок `commonSqlFile/sqlFile` через тот же `config-form` контракт;
  - `Удалить` с запретом удаления ресурса, который еще используется;
  - каталог строится из draft-state, поэтому новые и переименованные SQL-ресурсы сразу видны до сохранения.
- `2026-04-18`: подключены действия администрирования DB-модуля в Compose editor:
  - `Новый модуль` как inline-форма с `Monaco` для стартового `application.yml`;
  - `Удалить модуль` через backend call и обновление каталога после удаления.
- `2026-04-18`: в editor shell добавлен compact live-блок `Ход выполнения`:
  - использует существующий `module-runs` API;
  - показывает прогресс стадий;
  - показывает последние события текущего или последнего запуска;
  - обновляется через те же transport-механизмы, что и Compose `module-runs` (`FILES` через websocket, `DB` через polling).
- `2026-04-18`: каталог DB-модулей в Compose editor доведен до production flow по скрытым модулям:
  - добавлен toggle `Показывать скрытые модули`;
  - route/state сохраняют `includeHidden`;
  - скрытый модуль можно создать и сразу оставить каталог в том же режиме;
  - скрытые модули помечаются badge `Скрыт`.
- `2026-04-18`: action bar Compose editor приведен к production-цветовой модели:
  - `Запустить` и `Опубликовать` используют `btn-success`;
  - `Сохранить` и `Сохранить черновик` используют `btn-outline-primary`;
  - `Сбросить черновик` и `Удалить модуль` используют `btn-outline-danger`;
  - вторичные действия оставлены на `btn-outline-secondary`.
- `2026-04-18`: кнопка `Отменить изменения` в Compose editor перестала быть декоративной:
  - перечитывает выбранный модуль из backend;
  - сбрасывает локальные изменения `application.yml`, SQL и metadata;
  - ведет себя как production-действие rollback к последнему сохраненному состоянию.
- `2026-04-18`: в Compose DB editor добавлен production-like context alert:
  - если активный runtime не находится в режиме `База данных`, экран показывает тот же понятный warning-контекст, что и legacy UI, а не только вторичную ошибку каталога.

Осталось:

- довести visual parity shell до уровня production `index.html` / `db-modules.html`;
- после стабилизации editor shell продолжить вынос pure helpers/state logic в `ui-compose-shared`.

## Эпик 5. Импорт `files -> database`

Статус:

- в работе

Цель:

- перевести экран `/db-sync` после стабилизации редактора и истории запусков.

Что нужно сделать:

- сохранить maintenance mode;
- сохранить live state текущего sync;
- сохранить историю import-run и detail-view;
- повторить текущую текстовую и цветовую модель.

Выполнено:

- `2026-04-18`: добавлен Compose-route `/compose-sync`, который открывает тот же bundle с `screen=module-sync`;
- `2026-04-18`: поднят новый slice `module_sync` в `ui-compose-shared`:
  - общие модели sync-state;
  - история import-run;
  - detail-view;
  - store с polling-refresh и действиями `sync all / sync one`;
- `2026-04-18`: в `ui-compose-web` добавлен Compose-экран импорта модулей:
  - текущее runtime/sync state;
  - maintenance mode;
  - история импортов;
  - детали выбранного запуска;
  - запуск `sync all` и `sync one`;
- `2026-04-18`: ссылка `Импорт из файлов` из Compose DB editor теперь ведет в `/compose-sync`, а не в legacy `/db-sync`.
- `2026-04-18`: action-flow `sync one` приведен к production UX:
  - отдельная кнопка `Синхронизировать один модуль`;
  - форма точечного импорта раскрывается и скрывается отдельно;
  - после успешного запуска точечного импорта форма закрывается;
  - экран остается на выбранном import-run и продолжает polling live state.
- `2026-04-18`: история и детали `compose-sync` переведены на production panel-структуру:
  - отдельные `panel h-100` для истории и деталей;
  - selector лимита истории вместо временных кнопок;
  - detail-pane выровнен под текущий `db-sync.html` по подаче summary и item-list.
- `2026-04-18`: из `compose-sync` убран пустой hero-art контейнер, чтобы верхняя часть экрана соответствовала production `db-sync.html`, где hero без отдельной декоративной правой секции.

Осталось:

- довести visual parity `compose-sync` до уровня production `db-sync.html` по мелким layout-отличиям и toolbar-подаче;
- после этого считать экран импорта функционально закрытым.

## Эпик 6. SQL-консоль

Статус:

- в работе

Что уже сделано:

- поднят отдельный Compose route `/compose-sql-console`;
- добавлен `shared` slice:
  - `sql_console/SqlConsoleModels.kt`;
  - `sql_console/SqlConsoleApi.kt`;
  - `sql_console/SqlConsoleAnalysis.kt`;
  - `sql_console/SqlConsoleStore.kt`;
- добавлен web transport:
  - `sql_console/SqlConsoleApi.kt`;
- добавлена первая рабочая Compose-страница:
  - `sql_console/SqlConsolePage.kt`;
- экран уже использует реальные backend endpoint-ы:
  - `GET /api/sql-console/info`;
  - `GET /api/sql-console/state`;
  - `POST /api/sql-console/state`;
  - `POST /api/sql-console/settings`;
  - `POST /api/sql-console/connections/check`;
  - `POST /api/sql-console/query/start`;
  - `GET /api/sql-console/query/{id}`;
  - `POST /api/sql-console/query/{id}/cancel`;
- уже работают:
  - `Monaco` editor;
  - выбор источников;
  - persisted draft-state;
  - recent/favorite queries;
  - strict safety;
  - async run/cancel/polling;
  - базовый вывод результатов по `shard/source`;
  - sidebar-блок `credential.properties` с upload через UI;
  - export `ZIP` для полного result set;
  - export `CSV` по конкретному `shard/source`.
  - tabs `Данные / Статусы`;
  - отдельный data-pane c выбором активного `shard/source`;
  - status-pane с карточками результатов по каждому source;
  - status-table `Source / Статус / Затронуто строк / Сообщение / Ошибка`;
  - production-like hero actions и shell toolbar;
  - collapsible `credential.properties` через `details/summary`;
  - двухколоночный layout блока `Последние / Избранные запросы`;
  - постоянный output shell с tabs и placeholder/error states даже до первого запуска.

Что осталось:

- добить оставшиеся мелкие layout/spacing-расхождения относительно production `sql-console.html`;
- при необходимости вынести часть table/result helpers в `shared`;
- после этого только считать Compose SQL-консоль функционально закрытой.

## Риски

- Compose Web пока менее зрелый стек, чем текущий HTML/JS;
- сложные таблицы и большие формы будут дороже по производительности и по доводке UX;
- `Monaco`/`CodeMirror` integration потребует отдельного JS bridge и lifecycle-обвязки;
- если потеряем визуальный паритет на раннем этапе, появится второй UI, а не миграция.

## Ближайшие шаги

1. Довести visual parity editor shell до уровня production UI.
2. Довести visual parity `compose-sync` по layout и toolbar-подаче.
3. Довести первый Compose-slice SQL-консоли до production parity по toolbar/results/export.
4. Продолжить перенос pure helpers и state logic из `ui-compose-web` в `ui-compose-shared`.
5. После стабилизации `shared + web` начать реальный desktop transport к Ktor backend.
