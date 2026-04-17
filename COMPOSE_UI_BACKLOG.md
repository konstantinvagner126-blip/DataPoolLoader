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
- SQL-консоль не трогаем до конца миграции основных экранов.

## Архитектурный вектор

- текущий `ui-compose-web` остается рабочим web-front для миграции, но не считается конечной архитектурой;
- целевая модель: `shared + web + future desktop`, а не углубление в web-only Compose-код;
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
- `module_runs/ModuleRunsFormatters.kt`
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

Осталось:

- выровнять visual parity с текущим `module-runs.html`;
- при необходимости дотянуть quick actions, compact/full режимы и оставшиеся мелкие отличия от production renderer.

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

Осталось:

- довести visual parity shell до уровня production `index.html` / `db-modules.html`;
- добавить metadata form-state вместо текущего preview;
- добавить `Новый модуль` и `Удалить модуль`;
- после стабилизации editor shell начать вынос state/store слоя в будущий shared-модуль.

## Эпик 5. Импорт `files -> database`

Статус:

- не начато

Цель:

- перевести экран `/db-sync` после стабилизации редактора и истории запусков.

Что нужно сделать:

- сохранить maintenance mode;
- сохранить live state текущего sync;
- сохранить историю import-run и detail-view;
- повторить текущую текстовую и цветовую модель.

## Эпик 6. SQL-консоль

Статус:

- отложено

Причина:

- самый дорогой экран по риску;
- зависит от embedded editor (`Monaco` или `CodeMirror`), таблиц результатов, toolbar-состояния, favorites и строгой защиты;
- переводить только после стабилизации foundation, истории и редактора.

## Риски

- Compose Web пока менее зрелый стек, чем текущий HTML/JS;
- сложные таблицы и большие формы будут дороже по производительности и по доводке UX;
- `Monaco`/`CodeMirror` integration потребует отдельного JS bridge и lifecycle-обвязки;
- если потеряем визуальный паритет на раннем этапе, появится второй UI, а не миграция.

## Ближайшие шаги

1. Довести visual parity editor shell до уровня production UI.
2. Перевести metadata tab с preview на рабочую Compose-форму.
3. Подключить `Новый модуль` и `Удалить модуль`.
4. После стабилизации editor shell начать декомпозицию на `shared + web`.
