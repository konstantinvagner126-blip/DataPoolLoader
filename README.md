# DataPoolLoader

Multi-module Kotlin-проект для:

- параллельной выгрузки данных из нескольких PostgreSQL БД;
- сохранения отдельных CSV по каждому источнику;
- сборки общего `merged.csv`;
- загрузки результата в целевую PostgreSQL таблицу;
- запуска как напрямую из app-модулей, так и через локальный Web UI.

## Состав проекта

- `core`  
  Общее ядро: конфиг, выгрузка, merge, валидация, загрузка в target БД.

- `apps/dc-sms-offer`  
  Пример прикладного модуля.

- `ui`  
  Локальный Ktor UI для запуска и редактирования модулей через браузер.

- `templates/app-module`  
  Шаблон для создания нового app-модуля.

## Что умеет приложение

- читает `application.yml`;
- поддерживает общий SQL и SQL override для отдельных источников;
- поддерживает SQL прямо в YAML и в отдельных `.sql` файлах;
- умеет брать `jdbcUrl`, `username`, `password` напрямую или через `${PLACEHOLDER}`;
- выполняет source-запросы параллельно;
- сохраняет `<source-name>.csv` по каждому успешному источнику;
- формирует `merged.csv` в режимах `plain`, `round_robin`, `proportional`, `quota`;
- загружает `merged.csv` в target PostgreSQL;
- пишет `summary.json`;
- продолжает работу при ошибке отдельных источников, если остались успешные.

## Быстрый старт

Сборка и базовая проверка:

```bash
./gradlew test
```

Запуск прикладного модуля:

```bash
./gradlew :apps:dc-sms-offer:run
```

Запуск UI:

```bash
./gradlew :ui:run
```

После старта UI:

```text
http://localhost:8080
```

## Как запускать app-модули

Точка входа app-модулей:

```text
com.sbrf.lt.datapool.app.MainKt
```

Можно запускать:

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

## Поиск credential.properties

`credential.properties` ищется автоматически в таком порядке:

1. `-Dcredentials.file=/absolute/path/to/credential.properties`
2. ближайший `gradle/credential.properties` вверх по дереву проекта
3. `~/.gradle/credential.properties`

Это поведение одинаково для:

- app-модулей;
- UI;
- ручного запуска из IDEA;
- запуска через Gradle `run`.

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

1. в найденном `credential.properties`
2. в переменных окружения
3. в JVM system properties

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

## Формат application.yml

Пример:

```yaml
app:
  outputDir: ./output
  fileFormat: csv
  mergeMode: round_robin
  errorMode: continue_on_error
  parallelism: 5
  fetchSize: 1000
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
| `app.progressLogEveryRows` | `10000` | нет |
| `app.maxMergedRows` | без ограничения | нет |
| `app.deleteOutputFilesAfterCompletion` | `false` | нет |
| `app.commonSql` | пусто | нет |
| `app.commonSqlFile` | `null` | нет |
| `app.sources` | пустой список | да, для рабочего запуска должен быть хотя бы один источник |
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

Пример:

```yaml
outputDir: ./output
```

### `app.fileFormat`

Сейчас поддерживается только `csv`.

По умолчанию: `csv`

### `app.mergeMode`

Поддерживаемые значения:

- `plain`
- `round_robin`
- `proportional`
- `quota`

Подробно описаны ниже в разделе `Режимы merge`.

По умолчанию: `plain`

### `app.errorMode`

Сейчас поддерживается только:

```yaml
errorMode: continue_on_error
```

Это значит:

- ошибка одной БД не останавливает весь запуск;
- merge строится по успешным источникам;
- если успешных источников не осталось, запуск завершается ошибкой.

По умолчанию: `continue_on_error`

### `app.parallelism`

Сколько source БД обрабатывать одновременно.

По умолчанию: `5`

Пример:

```yaml
parallelism: 5
```

### `app.fetchSize`

Размер JDBC-порции при чтении результата из PostgreSQL.

Обычно разумный старт:

По умолчанию: `1000`

```yaml
fetchSize: 1000
```

### `app.progressLogEveryRows`

Как часто писать прогресс по строкам для каждой source БД.

По умолчанию: `10000`

Пример:

```yaml
progressLogEveryRows: 10000
```

### `app.maxMergedRows`

Необязательный лимит на размер итогового `merged.csv`.

По умолчанию: не ограничен

Пример:

```yaml
maxMergedRows: 50000
```

### `app.deleteOutputFilesAfterCompletion`

Если `true`, после завершения будут удалены:

- промежуточные `<source-name>.csv`
- итоговый `merged.csv`

`summary.json` остается.

По умолчанию: `false`

Пример:

```yaml
deleteOutputFilesAfterCompletion: true
```

### `app.commonSql`

Общий SQL для всех источников прямо в YAML.

Нельзя задавать одновременно с `commonSqlFile`.

По умолчанию: пустое значение

Пример:

```yaml
commonSql: |
  select id, payload
  from some_table
```

### `app.commonSqlFile`

Общий SQL в отдельном файле.

Поддерживаются:

- `classpath:sql/common.sql`
- относительный путь
- абсолютный путь

Для `classpath:` путь считается относительно `src/main/resources`.

По умолчанию: `null`

Пример:

```yaml
commonSqlFile: classpath:sql/common.sql
```

### `app.sources`

Список источников.

По умолчанию: пустой список, но для рабочего запуска должен быть задан хотя бы один источник.

Обязательные поля у каждого источника:

- `name`
- `jdbcUrl`
- `username`
- `password`

Опционально:

- `sql`
- `sqlFile`

`sql` и `sqlFile` нельзя задавать одновременно.

Значения по умолчанию внутри элемента `sources`:

- `name`: пустая строка
- `jdbcUrl`: пустая строка
- `username`: пустая строка
- `password`: пустая строка
- `sql`: `null`
- `sqlFile`: `null`

Практически это означает: для реального запуска `name`, `jdbcUrl`, `username` и `password` должны быть заданы явно.

### Приоритет выбора SQL

Для каждого источника запрос выбирается так:

1. `source.sql`
2. `source.sqlFile`
3. `app.commonSql`
4. `app.commonSqlFile`

### `app.quotas`

Используется только в режиме:

```yaml
mergeMode: quota
```

Пример:

```yaml
quotas:
  - source: db1
    percent: 10
  - source: db2
    percent: 30
  - source: db3
    percent: 60
```

Правила:

- квота должна быть задана для каждого источника;
- сумма процентов должна быть `100`.

По умолчанию: пустой список

### `app.target`

Настройки целевой БД.

Поля:

- `enabled`
- `jdbcUrl`
- `username`
- `password`
- `table`
- `truncateBeforeLoad`

Значения по умолчанию:

- `enabled`: `true`
- `jdbcUrl`: пустая строка
- `username`: пустая строка
- `password`: пустая строка
- `table`: пустая строка
- `truncateBeforeLoad`: `false`

Пример:

```yaml
target:
  enabled: true
  jdbcUrl: ${TARGET_JDBC_URL}
  username: ${TARGET_USERNAME}
  password: ${TARGET_DB_PASSWORD}
  table: public.test_data_pool
  truncateBeforeLoad: true
```

Если `enabled: false`, выгрузка и merge выполняются, а импорт в target пропускается.

## SQL в отдельных файлах

Поддерживаются:

- `commonSqlFile`
- `sources[].sqlFile`

Файлы могут лежать:

- в ресурсах модуля, например `src/main/resources/sql/common.sql`
- во внешней файловой системе

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

Пример:

```text
db1 -> A1 A2
db2 -> B1 B2
результат -> A1 A2 B1 B2
```

### `round_robin`

Берет по одной строке из каждого источника по кругу.

Пример:

```text
db1 -> A1 A2 A3
db2 -> B1 B2
db3 -> C1
результат -> A1 B1 C1 A2 B2 A3
```

### `proportional`

Автоматически распределяет строки по фактическому объему источников и старается размазать их по merged-файлу равномерно.

Пример:

- `db1`: 10 строк
- `db2`: 90 строк

Итог:

- в merged-файле будет примерно 10% строк из `db1`
- и 90% из `db2`
- строки маленького источника будут распределены по всему файлу, а не лежать блоком

### `quota`

Распределяет строки по вручную заданным процентам.

Пример:

```yaml
mergeMode: quota
quotas:
  - source: db1
    percent: 25
  - source: db2
    percent: 75
```

Алгоритм:

1. сначала собирается полный `rowCount` по каждому успешному источнику;
2. затем вычисляется максимально допустимый общий объем merged-файла без дублирования строк;
3. по этому объему рассчитывается, сколько строк можно взять из каждого источника;
4. строки равномерно распределяются по merged-файлу.

Если источник вернул больше строк, чем попадает по квоте:

- его отдельный CSV сохраняется полностью;
- в `merged.csv` попадает только часть строк.

## Проверки перед загрузкой в target

Если `target.enabled=true`, выполняется preflight-проверка:

- целевая таблица существует;
- все колонки из входных данных есть в target;
- если есть несовпадение только по регистру, ошибка содержит отдельную подсказку;
- если в target есть обязательные `NOT NULL` колонки без default, которых нет во входных данных, загрузка не начнется.

## UI

Запуск:

```bash
./gradlew :ui:run
```

После запуска открыть:

```text
http://localhost:8080
```

Что умеет UI:

- показывает список app-модулей;
- открывает и редактирует `application.yml`;
- открывает и редактирует связанные SQL-файлы;
- загружает `credential.properties` через интерфейс;
- запускает модуль;
- показывает прогресс и события выполнения;
- сохраняет изменения в файлы модуля.

Порядок credentials в UI:

1. файл, загруженный через интерфейс;
2. `ui.defaultCredentialsFile` из `ui/src/main/resources/application.yml`;
3. `-Dcredentials.file=...`;
4. ближайший `gradle/credential.properties` вверх по дереву проекта;
5. `~/.gradle/credential.properties`

Важно:

- UI не заменяет app-модули;
- любой app-модуль по-прежнему можно запускать напрямую из IDEA или через Gradle.

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

Настройки для них по умолчанию лежат в:

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
- `ui` >= 90%

## Создание нового app-модуля

Через шаблон:

```bash
./gradlew createAppModule -PappName=my-new-app
```

Что делает task:

- создает `apps/my-new-app`
- копирует шаблон из `templates/app-module`
- подставляет имя модуля

Новый модуль автоматически подхватывается через сканирование `apps/` в `settings.gradle.kts`.

## Полезные файлы

- `apps/dc-sms-offer/src/main/resources/application.yml`
- `ui/src/main/resources/application.yml`
- `gradle/credential.properties`
- `gradle/local-postgres-test.properties`
