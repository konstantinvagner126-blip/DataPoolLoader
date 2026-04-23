# SQL Console Monaco Autocomplete

Цель документа:

- оценить, какие подсказки и autocomplete реально можно добавить в Monaco для PostgreSQL;
- отделить быстрые и полезные улучшения от тяжелого IDE/LSP-сценария;
- зафиксировать рекомендуемый план без лишнего риска для текущей архитектуры.

## Текущее состояние

Сейчас SQL-консоль использует Monaco как редактор с языком `sql`.

По текущему коду:

- [MonacoEditorPane.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/foundation/component/MonacoEditorPane.kt) только создает редактор и пробрасывает `value / language / readOnly`;
- [compose-monaco.js](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/compose-monaco.js) только инициализирует Monaco loader и создает editor instance;
- кастомных provider-ов для:
  - completion;
  - hover;
  - signature help;
  - diagnostics;
  раньше не было.

Вывод:

- базовая подсветка SQL есть;
- уже реализованы:
  - local keyword/function/snippet completions;
  - lightweight hover;
  - metadata-aware autocomplete по объектам БД через search-first backend;
  - exact object column lookup для сценария `schema.table.` через отдельный readonly columns contract.

## Что Monaco умеет из коробки

Из коробки Monaco дает только легкий editor experience:

- syntax highlighting;
- bracket matching;
- basic word-based suggestions;
- keyboard/navigation UX.

Но этого недостаточно для PostgreSQL-опыта уровня IDE:

- нет встроенного полноценного PostgreSQL semantic autocomplete;
- нет понимания схем, таблиц, колонок, индексов и alias-ов;
- нет проверки SQL на уровне PostgreSQL dialect.

## Что реально можно сделать

### Уровень 1. Локальные подсказки без тяжелого backend

Это самый дешевый и полезный слой.

Что сюда входит:

- completion по PostgreSQL keywords:
  - `select`, `from`, `where`, `join`, `group by`, `order by`, `limit`, `insert`, `update`, `delete`, `create`, `alter`, `drop`;
- completion по часто используемым PostgreSQL functions:
  - `count`, `sum`, `avg`, `coalesce`, `now`, `date_trunc`, `jsonb_build_object`, `jsonb_agg`, `unnest`;
- сниппеты:
  - `select * from ...`
  - `insert into ... values (...)`
  - `update ... set ... where ...`
  - `with ... as (...) select ...`
- hover-подсказки для keyword/function с коротким описанием.

Плюсы:

- быстро внедряется;
- почти не трогает backend;
- уже сильно улучшает UX.

Минусы:

- не знает реальные объекты БД;
- не знает колонки текущей таблицы;
- не понимает alias-ы.

### Уровень 2. Metadata-aware autocomplete по объектам БД

Это уже практическое приближение к IDE без полноценного LSP.

Источник данных у нас уже частично есть:

- отдельный экран `Объекты БД`;
- search-first API по объектам БД;
- persisted favorites;
- metadata search по source.

Что можно добавить:

- autocomplete по schema/table/view/index names;
- autocomplete по недавно найденным и закрепленным объектам;
- autocomplete после `schema.` или `table_alias.` по колонкам;
- autocomplete с учетом выбранных source;
- простые hover-карточки по найденному объекту:
  - тип;
  - схема;
  - комментарий / DDL / набор колонок, если есть.

Ключевое ограничение:

- нельзя подгружать весь catalog заранее;
- поиск и autocomplete должны быть prefix-based и throttled;
- запросы должны идти только:
  - после минимальной длины строки;
  - по явному контексту;
  - с debounce.

Рекомендуемая модель:

- при вводе общего имени: искать только объекты по префиксу;
- при вводе после точки: грузить только колонки для конкретной таблицы или exact object;
- использовать локальный cache на время editor session.

Плюсы:

- дает реально полезное business autocomplete;
- переиспользует уже сделанный metadata screen;
- не требует полного SQL language server.

Минусы:

- нужен отдельный completion API;
- нужен аккуратный кэш и троттлинг;
- alias resolution будет ограниченным.

### Уровень 3. Почти IDE / language intelligence

Это самый дорогой путь.

Что сюда входит:

- полноценный SQL parser;
- alias resolution;
- контекстные колонки по `FROM/JOIN`;
- diagnostics по синтаксису и структуре query;
- signature help;
- rename/reference-like поведение;
- полноценный PostgreSQL dialect awareness.

Для этого обычно нужен один из вариантов:

- SQL language server;
- тяжелая библиотека уровня `monaco-sql-languages` + отдельная интеграция;
- собственный parser/AST слой.

Риски:

- заметно больше complexity;
- web-worker и bundle size;
- нужно решать источник metadata и invalidation;
- стоимость поддержки сильно выше.

Вывод:

- как первый этап для этого проекта такой вариант избыточен.

## Что я рекомендую

### Рекомендуемый этап 1

- добавить Monaco completion provider для PostgreSQL keywords/functions/snippets;
- добавить hover provider для ключевых конструкций;
- добавить простые сниппеты для частых шаблонов.

Это даст быстрый и заметный эффект почти без архитектурного риска.

### Рекомендуемый этап 2

- добавить metadata-aware completion по объектам БД;
- использовать текущий search-first backend;
- отдельно добавить lightweight API для колонок exact object;
- опираться на:
  - выбранные source;
  - favorites;
  - локальный in-memory cache редактора.

Это уже даст практический autocomplete ближе к IDE, но без полноценного language server.

Статус:

- этап 2 уже частично реализован:
  - object autocomplete по schema/object names работает;
  - exact object column lookup вынесен в отдельный readonly route и не использует full inspector payload;
  - для column autocomplete по-прежнему не реализованы alias resolution и более глубокий SQL context analysis.

### Рекомендуемый этап 3

- только если после этапов 1 и 2 останется реальный запрос на более глубокую SQL-навигацию;
- отдельно оценивать LSP/worker-интеграцию.

## Что точно не стоит делать сразу

- грузить весь список объектов БД при открытии страницы;
- тащить тяжелый SQL language server без доказанной необходимости;
- строить серверный stateful autocomplete session;
- смешивать autocomplete и execution lifecycle.

## Архитектурные требования

- autocomplete не должен ломать текущий async lifecycle SQL-консоли;
- metadata-запросы должны быть readonly;
- autocomplete должен быть search-first и bounded;
- exact object column lookup должен идти через отдельный readonly boundary, а не через stateful backend completion-session;
- при недоступной БД UI должен деградировать до локальных keyword/snippet hints, а не ломаться полностью.

## Практический вывод

Да, подсказки в Monaco сделать можно.

Но правильно делить задачу на два реалистичных слоя:

1. локальные PostgreSQL keyword/function/snippet hints;
2. metadata-aware autocomplete по объектам и колонкам через search-first API.

Именно этот путь даст ощутимую IDE-пользу без тяжелого LSP-стека и без лишнего риска для текущей архитектуры.
