# DataPoolLoader

`DataPoolLoader` — multi-module Kotlin-проект для подготовки данных при нагрузочном тестировании.

Сейчас проект покрывает:

- параллельную выгрузку данных из нескольких PostgreSQL-источников;
- сборку общего `merged.csv`;
- загрузку результата в целевую PostgreSQL-таблицу;
- локальный UI для запуска модулей, редактирования конфигов и SQL;
- SQL-консоль для ручной работы с настроенными источниками;
- desktop-packaging UI через `jpackage`.

## Состав проекта

- `core`  
  Общее ядро: конфиг, разрешение placeholders, экспорт из PostgreSQL, merge, validation, import в target, SQL-console backend.

- `apps/*`  
  Прикладные модули запуска. Каждый модуль содержит собственный `MainKt`, `application.yml` и SQL-ресурсы.

- `ui-server`  
  Серверная часть Compose UI в каталоге `ui-server/`. Через нее можно:
  - запускать app-модули;
  - редактировать `application.yml` и SQL-файлы;
  - смотреть историю запусков и summary;
  - работать с SQL-консолью;
  - открывать встроенную справку по модулям.

- `templates/app-module`  
  Шаблон для создания нового app-модуля.

## Что умеет система

### Batch-модули

- читают `application.yml`;
- поддерживают `commonSql`, `commonSqlFile`, `sources[].sql`, `sources[].sqlFile`;
- умеют брать `jdbcUrl`, `username`, `password` напрямую или через `${PLACEHOLDER}`;
- выполняют source-запросы параллельно;
- сохраняют `<source-name>.csv` по каждому успешному источнику;
- формируют `merged.csv` в режимах `plain`, `round_robin`, `proportional`, `quota`;
- выполняют preflight-проверку target-таблицы;
- загружают `merged.csv` в target PostgreSQL;
- пишут `summary.json`;
- продолжают работу при ошибке отдельных источников, если остались успешные.

### UI

- главная страница с переходами в:
  - модули;
  - SQL-консоль;
  - справку;
- визуальный редактор конфига модуля с синхронизацией `YAML <-> форма`;
- редактирование связанных SQL-файлов;
- загрузка `credential.properties` через интерфейс;
- история запусков;
- структурированный просмотр summary;
- help-страница с инструкциями по каждому модулю.

### SQL-консоль

- работает по `ui.sqlConsole.sources`;
- поддерживает один SQL statement за запуск;
- `SELECT` показывает отдельно по каждому source;
- остальные команды показывает как статусы выполнения по каждому source;
- умеет:
  - выбирать source через чекбоксы;
  - выполнять запрос асинхронно;
  - отменять запрос;
  - учитывать `queryTimeoutSec`.

## Быстрый старт

Базовая проверка:

```bash
./gradlew test
```

Запуск UI:

```bash
./gradlew :ui-server:run
```

После старта открыть:

```text
http://localhost:8080
```

Запуск app-модуля напрямую:

```bash
./gradlew :apps:<module-name>:run
```

Пример:

```bash
./gradlew :apps:dc-sms-offer:run
```

## Launcher-скрипты

Для локального старта UI без ручного выбора Gradle-task:

```bash
./scripts/run-ui-server.sh
```

Для старта desktop-прототипа:

```bash
./scripts/run-ui-compose-desktop.sh
```

Оба скрипта запускаются из корня проекта и сами переходят в нужный каталог.

## Запуск из IDEA

Рекомендуемые конфигурации:

1. `UI Server Full`
   Используй как основной запуск проекта. Это Gradle-конфиг, который пересобирает `ui-compose-web`, синхронизирует web asset-ы и потом поднимает `ui-server`.

2. `UI Server`
   Используй для быстрого JVM-debug server-части, когда web bundle уже собран и ты не менял `ui-compose-web`.

3. `UI Compose Desktop`
   Запуск desktop-прототипа.

Если менялся `ui-compose-web`, для корректного старта web UI нужен именно `UI Server Full`.

## Quick Start для UI

Обычный рабочий цикл:

1. Запусти `UI Server Full` из IDEA или `./scripts/run-ui-server.sh`.
2. Открой `http://localhost:8080`.
3. Проверь нужный экран:
   - `/modules`
   - `/db-modules`
   - `/db-sync`
   - `/sql-console`
4. После правок в `ui-compose-web` перезапускай именно полный старт, чтобы bundle пересобрался.

## Как запускать app-модули

Точка входа app-модулей:

```text
com.sbrf.lt.datapool.app.MainKt
```

Варианты запуска:

1. Через Gradle:

```bash
./gradlew :apps:<module-name>:run
```

2. Из IDEA как обычное Kotlin/Gradle-приложение.

3. С внешним конфигом:

```text
--config=/absolute/path/to/application.yml
```

Если `--config` не передан, используется встроенный `application.yml` самого модуля.

## Как работает `credential.properties`

`credential.properties` ищется автоматически в таком порядке:

1. `-Dcredentials.file=/absolute/path/to/credential.properties`
2. ближайший `gradle/credential.properties` вверх по дереву проекта
3. `~/.gradle/credential.properties`

Это поведение одинаково для:

- app-модулей;
- UI;
- запуска из IDEA;
- запуска через Gradle.

Пример VM option:

```text
-Dcredentials.file=/absolute/path/to/credential.properties
```

## Placeholder-ы в конфиге

В этих полях можно использовать `${KEY}`:

- `sources[].jdbcUrl`
- `sources[].username`
- `sources[].password`
- `target.jdbcUrl`
- `target.username`
- `target.password`

Значение ищется так:

1. в найденном `credential.properties`;
2. в переменных окружения;
3. в JVM system properties.

Если placeholder не разрешился:

- source помечается ошибочным;
- target-загрузка падает, если `target.enabled=true`.

Пример `credential.properties`:

```properties
DB1_JDBC_URL=jdbc:postgresql://localhost:5432/db1
DB1_USERNAME=user1
DB1_PASSWORD=secret1

DB2_JDBC_URL=jdbc:postgresql://localhost:5432/db2
DB2_USERNAME=user2
DB2_PASSWORD=secret2

TARGET_JDBC_URL=jdbc:postgresql://localhost:5432/target_db
TARGET_USERNAME=loader
TARGET_DB_PASSWORD=target_secret
```

## Что делать после добавления нового модуля

Минимальный чек-лист:

1. Создай каталог модуля в `apps/<module-name>`.
2. Добавь:
   - `build.gradle.kts`
   - `src/main/resources/application.yml`
   - SQL-ресурсы в `src/main/resources/sql`
   - `ui-module.yml`
3. Если модуль работает с placeholder-ами, проверь, что ключи есть в `credential.properties`.
4. Запусти `UI Server Full`.
5. Убедись, что модуль появился в:
   - `/modules` для файлового режима;
   - `/db-sync` как кандидат на импорт в DB registry.
6. Сделай пробный запуск из UI и проверь:
   - `Ход выполнения`
   - `История и результаты`
   - итоговый `summary.json`

## Формат `application.yml`

Пример:

```yaml
app:
  outputDir: ./output
  fileFormat: csv
  mergeMode: round_robin
  errorMode: continue_on_error
  parallelism: 5
  fetchSize: 1000
  queryTimeoutSec: 60
  progressLogEveryRows: 10000
  maxMergedRows:
  deleteOutputFilesAfterCompletion: false
  commonSqlFile: classpath:sql/common.sql

  sources:
    - name: db1
      jdbcUrl: ${DB1_JDBC_URL}
      username: ${DB1_USERNAME}
      password: ${DB1_PASSWORD}

    - name: db2
      jdbcUrl: ${DB2_JDBC_URL}
      username: ${DB2_USERNAME}
      password: ${DB2_PASSWORD}
      sqlFile: classpath:sql/db2_override.sql

  target:
    enabled: true
    jdbcUrl: ${TARGET_JDBC_URL}
    username: ${TARGET_USERNAME}
    password: ${TARGET_DB_PASSWORD}
    table: public.test_data_pool
    truncateBeforeLoad: true
```

## Параметры конфига

### Краткая таблица defaults

| Параметр | По умолчанию | Обязателен |
| --- | --- | --- |
| `app.outputDir` | `./output` | нет |
| `app.fileFormat` | `csv` | нет |
| `app.mergeMode` | `plain` | нет |
| `app.errorMode` | `continue_on_error` | нет |
| `app.parallelism` | `5` | нет |
| `app.fetchSize` | `1000` | нет |
| `app.queryTimeoutSec` | не задан | нет |
| `app.progressLogEveryRows` | `10000` | нет |
| `app.maxMergedRows` | без ограничения | нет |
| `app.deleteOutputFilesAfterCompletion` | `false` | нет |
| `app.commonSql` | пусто | нет |
| `app.commonSqlFile` | `null` | нет |
| `app.sources` | пустой список | да, для рабочего запуска нужен хотя бы один источник |
| `app.quotas` | пустой список | только для `mergeMode=quota` |
| `app.target.enabled` | `true` | нет |
| `app.target.jdbcUrl` | пусто | да, если `target.enabled=true` |
| `app.target.username` | пусто | да, если `target.enabled=true` |
| `app.target.password` | пусто | да, если `target.enabled=true` |
| `app.target.table` | пусто | да, если `target.enabled=true` |
| `app.target.truncateBeforeLoad` | `false` | нет |

Для каждого элемента `app.sources`:

| Параметр | По умолчанию | Обязателен |
| --- | --- | --- |
| `name` | пусто | да |
| `jdbcUrl` | пусто | да |
| `username` | пусто | да |
| `password` | пусто | да |
| `sql` | `null` | нет |
| `sqlFile` | `null` | нет |

### `app.outputDir`

Каталог результатов. Для каждого запуска создается подпапка с timestamp.

По умолчанию: `./output`

Важно:

- относительный `outputDir` резолвится от корня проекта, а не от случайной рабочей директории процесса.

### `app.fileFormat`

Сейчас поддерживается только:

```yaml
fileFormat: csv
```

### `app.mergeMode`

Поддерживаемые значения:

- `plain`
- `round_robin`
- `proportional`
- `quota`

### `app.errorMode`

Сейчас поддерживается только:

```yaml
errorMode: continue_on_error
```

Это значит:

- ошибка одной БД не останавливает весь запуск;
- merge строится по успешным источникам;
- если успешных источников не осталось, запуск завершается ошибкой.

### `app.parallelism`

Сколько source БД обрабатывать одновременно.

### `app.fetchSize`

Размер JDBC-порции при чтении результата из PostgreSQL.

Обычно разумный старт:

```yaml
fetchSize: 1000
```

### `app.queryTimeoutSec`

Таймаут выполнения одного SQL-запроса к source БД в секундах.

Если параметр не задан, приложение не устанавливает JDBC query timeout.

Пример:

```yaml
queryTimeoutSec: 60
```

### `app.progressLogEveryRows`

Как часто писать прогресс по строкам для каждой source БД.

### `app.maxMergedRows`

Необязательный лимит на размер итогового `merged.csv`.

### `app.deleteOutputFilesAfterCompletion`

Если `true`, после завершения будут удалены:

- промежуточные `<source-name>.csv`;
- итоговый `merged.csv`.

`summary.json` остается.

### `app.commonSql` и `app.commonSqlFile`

Общий SQL для всех источников:

- либо прямо в YAML через `commonSql`;
- либо в отдельном файле через `commonSqlFile`.

Нельзя задавать оба параметра одновременно.

Поддерживаются SQL-файлы:

- `classpath:sql/common.sql`
- относительный путь
- абсолютный путь

Для `classpath:` путь считается относительно `src/main/resources`.

### `app.sources`

Список источников.

У каждого источника:

- обязательны `name`, `jdbcUrl`, `username`, `password`;
- опциональны `sql`, `sqlFile`;
- `sql` и `sqlFile` нельзя задавать одновременно.

### Приоритет выбора SQL

Для каждого источника запрос выбирается так:

1. `source.sql`
2. `source.sqlFile`
3. `app.commonSql`
4. `app.commonSqlFile`

### `app.quotas`

Используется только при:

```yaml
mergeMode: quota
```

Правила:

- квота должна быть задана для каждого источника;
- сумма процентов должна быть `100`.

### `app.target`

Настройки целевой БД:

- `enabled`
- `jdbcUrl`
- `username`
- `password`
- `table`
- `truncateBeforeLoad`

Если `enabled: false`, выгрузка и merge выполняются, а импорт в target пропускается.

## SQL в отдельных файлах

Поддерживаются:

- `commonSqlFile`
- `sources[].sqlFile`

Файлы могут лежать:

- в ресурсах модуля, например `src/main/resources/sql/common.sql`;
- во внешней файловой системе.

Для ресурсов используется запись:

```yaml
commonSqlFile: classpath:sql/common.sql
sqlFile: classpath:sql/source1.sql
```

То есть:

- `classpath:sql/common.sql` -> `src/main/resources/sql/common.sql`

## Режимы merge

### `plain`

Просто склеивает успешные источники друг за другом.

### `round_robin`

Берет по одной строке из каждого источника по кругу.

### `proportional`

Распределяет строки по merged-файлу пропорционально фактическому объему источников.

### `quota`

Распределяет строки по вручную заданным процентам.

Алгоритм:

1. собирается полный `rowCount` по каждому успешному источнику;
2. вычисляется максимально допустимый общий объем merged-файла без дублирования строк;
3. рассчитывается, сколько строк можно взять из каждого источника;
4. строки равномерно распределяются по merged-файлу.

## Проверки перед загрузкой в target

Если `target.enabled=true`, выполняется preflight-проверка:

- целевая таблица существует;
- все колонки из входных данных есть в target;
- если есть несовпадение только по регистру, ошибка содержит отдельную подсказку;
- если в target есть обязательные `NOT NULL` колонки без default, которых нет во входных данных, загрузка не начнется.

## UI

Запуск:

```bash
./gradlew :ui-server:run
```

После запуска открыть:

```text
http://localhost:8080
```

### Что есть в UI

- главная страница:
  - переход в модули;
  - переход в SQL-консоль;
  - переход в справку;
- страница модулей:
  - визуальный редактор конфига;
  - вкладка `application.yml`;
  - редактор SQL-файлов;
  - запуск модуля;
  - история запусков;
  - summary;
- SQL-консоль;
- help-страница с инструкциями по каждому модулю.

### Визуальный редактор модуля

Поддерживает:

- двустороннюю синхронизацию `YAML <-> форма`;
- секции:
  - `Общие`
  - `SQL`
  - `Источники`
  - `Квоты`
  - `Target`
- сохранение изменений в реальные файлы модуля;
- запуск с текущими правками, даже если они еще не сохранены.

### Порядок credentials в UI

1. файл, загруженный через интерфейс;
2. `ui.defaultCredentialsFile`;
3. `-Dcredentials.file=...`;
4. ближайший `gradle/credential.properties`;
5. `~/.gradle/credential.properties`

Важно:

- UI не заменяет app-модули;
- любой app-модуль по-прежнему можно запускать напрямую из IDEA или через Gradle.

## SQL-консоль

SQL-консоль использует `ui.sqlConsole.sources`.

Сейчас она умеет:

- выполнять один SQL statement за запуск;
- выбирать конкретные source через чекбоксы;
- для `SELECT` показывать данные по каждому source отдельно;
- для остальных команд показывать статус выполнения по каждому source;
- работать асинхронно;
- отменять запрос;
- применять `queryTimeoutSec`.

Если в `ui.sqlConsole.sources` используются `${...}`, перед запуском SQL через UI нужно:

- загрузить `credential.properties` через интерфейс;
- или настроить fallback credentials.

## Desktop package через `jpackage`

UI можно упаковать как desktop-приложение без IDEA.

Команды:

```bash
./gradlew :ui-server:jpackageAppImage
./gradlew :ui-server:jpackageInstaller
```

Результат сборки появляется в:

- `ui-server/build/jpackage`

Примеры артефактов:

- macOS:
  - `LoadTestingDataPlatform.app`
  - `LoadTestingDataPlatform-<version>.dmg`
- Windows:
  - `msi`
- Linux:
  - `deb`

Важно:

- `jpackage` обычно собирает пакет под текущую ОС;
- packaged UI не требует IDE, но для работы с модулями ему нужен доступ к workspace проекта.

### Внешний UI-конфиг для packaged приложения

Packaged UI сначала ищет внешний конфиг:

1. `-Ddatapool.ui.config=/path/to/application.yml`
2. `DATAPOOL_UI_CONFIG`
3. `ui-application.yml` рядом с собранным приложением
4. `~/.datapool-loader/ui/application.yml`
5. затем fallback на встроенный `ui-server/src/main/resources/application.yml`

Пример:

```yaml
ui:
  port: 8080
  appsRoot: /absolute/path/to/DataPoolLoader/apps
  defaultCredentialsFile: /path/to/credential.properties
```

Полный пример:

- `ui-server/src/jpackage/ui-application.example.yml`

После `./gradlew :ui-server:jpackageAppImage` рядом с собранным приложением автоматически появляется шаблон:

- `ui-server/build/jpackage/ui-application.yml`

Его можно отредактировать и затем запускать `.app` без IDE и без дополнительных параметров.

### Как packaged UI находит app-модули

Packaged UI использует только `ui.appsRoot` из UI-конфига.

Рекомендуется указывать абсолютный путь:

```yaml
ui:
  appsRoot: /absolute/path/to/DataPoolLoader/apps
```

Если `appsRoot` задан корректно, packaged UI сможет:

- читать и редактировать `apps/*/src/main/resources/application.yml`;
- читать и редактировать SQL-файлы модулей;
- видеть новые app-модули без пересборки UI.

Для новых модулей пересборка packaged UI не нужна:

- достаточно добавить модуль в `apps/`;
- затем обновить страницу UI или перезапустить UI.

## Тестирование

Обычные тесты:

```bash
./gradlew test
```

Локальные integration-тесты против реального PostgreSQL:

```bash
./gradlew :core:localPostgresTest
```

Они:

- не входят в обычный `test`;
- используют локальный PostgreSQL;
- создают временные схемы и удаляют их после завершения.

Настройки по умолчанию лежат в:

- `gradle/local-postgres-test.properties`

Поддерживаются system properties:

```text
-Ddatapool.test.pg.host=127.0.0.1
-Ddatapool.test.pg.port=5432
-Ddatapool.test.pg.database=postgres
-Ddatapool.test.pg.username=<db-user>
-Ddatapool.test.pg.password=dummy
```

## Покрытие тестами

Для проекта подключен `Kover`.

Основные команды:

```bash
./gradlew koverHtmlReport
./gradlew koverXmlReport
```

Текущая целевая планка:

- `core` >= 90%
- `ui-server` >= 90%

## Создание нового app-модуля

Через шаблон:

```bash
./gradlew createAppModule -PappName=my-new-app
```

Task:

- создает `apps/my-new-app`;
- копирует шаблон из `templates/app-module`;
- подставляет имя модуля.
- создает `ui-module.yml` для UI-метаданных модуля.

Новый модуль автоматически подхватывается через сканирование `apps/` в `settings.gradle.kts`.

Если модуль только что добавлен в IDE:

- сделай `Reload Gradle Project`;
- затем обнови страницу UI или перезапусти UI.

## Полезные файлы

- `apps/dc-sms-offer/src/main/resources/application.yml`
- `ui-server/src/main/resources/application.yml`
- `ui-server/src/jpackage/ui-application.example.yml`
- `gradle/credential.properties`
- `gradle/local-postgres-test.properties`
- `PLAN.md`
- `ROADMAP.md`
- `BACKLOG.md`
