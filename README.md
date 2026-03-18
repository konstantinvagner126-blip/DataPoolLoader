# DataPoolLoader

Multi-module проект на Kotlin для параллельной выгрузки данных из нескольких PostgreSQL БД в отдельные CSV-файлы, объединения в `merged.csv` и, при необходимости, загрузки результата в target PostgreSQL.

## Структура проекта

- [core](/Users/kwdev/DataPoolLoader/core) — библиотечный модуль с общей логикой.
- [apps/dc-sms-offer](/Users/kwdev/DataPoolLoader/apps/dc-sms-offer) — первое запускаемое приложение.

Пользовательские сценарии добавляются как отдельные app-модули, которые зависят от `core`.

## Что делает

- читает `application.yml`;
- поддерживает общий SQL и override SQL на уровне источника;
- разрешает `jdbcUrl`, `username` и `password` напрямую или через `${PLACEHOLDER}`;
- выполняет выгрузки параллельно;
- сохраняет `<source-name>.csv` по каждому успешному источнику;
- формирует `merged.csv` в режимах `plain`, `round_robin`, `proportional` и `quota`;
- загружает `merged.csv` в целевую PostgreSQL таблицу;
- пишет `summary.json` по результатам запуска;
- при ошибке одной БД продолжает работу по остальным.

## Запуск из IDEA

Точка входа первого приложения: `com.sbrf.lt.datapool.app.MainKt`

Аргумент конфигурации:

```text
--config=/absolute/path/to/application.yml
```

Либо можно указать путь через VM options:

```text
-Dcredentials.file=/absolute/path/to/credential.properties
```

Приоритет источников для credentials-файла:

1. `-Dcredentials.file=...`
2. `gradle/credential.properties`

Если аргумент `--config` не передан, используется [application.yml](/Users/kwdev/DataPoolLoader/apps/dc-sms-offer/src/main/resources/application.yml).

Сборка и запуск:

```text
./gradlew test
./gradlew :apps:dc-sms-offer:run
```

## Шаблон нового app-модуля

В репозитории есть шаблон:

- [templates/app-module](/Users/kwdev/DataPoolLoader/templates/app-module)

Что в нем есть:

- `build.gradle.kts`
- `Main.kt`
- `application.yml`

Как использовать:

1. Скопировать `templates/app-module` в `apps/<new-name>`.
2. Заменить `__APP_NAME__` в `Main.kt`.
3. Подключить новый модуль в [settings.gradle.kts](/Users/kwdev/DataPoolLoader/settings.gradle.kts).
4. Настроить свой `application.yml`.

Либо использовать Gradle task:

```text
./gradlew createAppModule -PappName=my-new-app
```

Что делает task:

- создает `apps/my-new-app`;
- копирует туда шаблон из `templates/app-module`;
- подставляет имя модуля вместо `__APP_NAME__`;
- модуль автоматически подхватится проектом, потому что `settings.gradle.kts` сканирует каталог `apps/`.

## Формат конфига

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
  commonSql: |
    select id, created_at, payload
    from some_table

  sources:
    - name: db1
      jdbcUrl: ${DB1_JDBC_URL}
      username: ${DB1_USERNAME}
      password: ${DB1_PASSWORD}

    - name: db2
      jdbcUrl: ${DB2_JDBC_URL}
      username: ${DB2_USERNAME}
      password: local-dev-password
      sql: |
        select id, created_at, payload
        from special_table

  target:
    enabled: true
    jdbcUrl: ${TARGET_JDBC_URL}
    username: ${TARGET_USERNAME}
    password: ${TARGET_DB_PASSWORD}
    table: public.test_data_pool
    truncateBeforeLoad: true
```

## Параметры конфига

Ниже описаны все основные параметры `application.yml`.

### Блок `app`

- `outputDir`
  Каталог, в который складываются результаты запуска.
  Для каждого запуска внутри создается подкаталог с timestamp.
  Пример:
  ```yaml
  outputDir: ./output
  ```

- `fileFormat`
  Формат промежуточных и итогового файлов.
  Сейчас поддерживается только `csv`.
  Пример:
  ```yaml
  fileFormat: csv
  ```

- `mergeMode`
  Режим объединения данных из всех успешных источников.
  Поддерживаемые значения:
  - `plain`
  - `round_robin`
  - `proportional`
  - `quota`

- `errorMode`
  Режим обработки ошибок по источникам.
  Сейчас поддерживается только `continue_on_error`.
  Это означает:
  - ошибка одной БД не останавливает весь запуск;
  - merge строится по успешным источникам;
  - если успешных источников нет, запуск завершается ошибкой.

- `parallelism`
  Сколько source БД обрабатывать одновременно.
  Если указано `5`, то одновременно будут идти максимум 5 выгрузок.
  Пример:
  ```yaml
  parallelism: 5
  ```

- `fetchSize`
  Размер JDBC-порции при чтении результата из PostgreSQL.
  Чем больше значение, тем меньше сетевых обращений к БД, но тем больше памяти на одну порцию.
  Обычно рабочий диапазон: `1000`–`5000`.
  Пример:
  ```yaml
  fetchSize: 1000
  ```

- `progressLogEveryRows`
  Как часто выводить в лог прогресс по обработанным строкам для каждой source БД.
  Если указано `10000`, лог будет печататься на `10000`, `20000`, `30000` и так далее.
  Пример:
  ```yaml
  progressLogEveryRows: 10000
  ```

- `maxMergedRows`
  Необязательный лимит на размер итогового `merged.csv`.
  Если параметр задан, merge не превысит указанное число строк.
  Особенно полезно для подготовки ограниченной тестовой выборки.
  Пример:
  ```yaml
  maxMergedRows: 50000
  ```

- `commonSql`
  Общий SQL-запрос для всех источников.
  Используется, если у конкретного источника не задан собственный `sql`.
  Должен быть только `SELECT` или `WITH ... SELECT`.
  Пример:
  ```yaml
  commonSql: |
    select id, created_at, payload
    from some_table
  ```

### Блок `app.sources`

Список source БД, из которых идет чтение.

Каждый элемент массива описывает одно подключение.

- `name`
  Уникальное имя источника.
  Используется:
  - в логах;
  - в имени промежуточного файла `<source-name>.csv`;
  - в `quota.source`.

- `jdbcUrl`
  JDBC URL подключения к PostgreSQL.
  Можно указывать напрямую или через placeholder:
  ```yaml
  jdbcUrl: ${DB1_JDBC_URL}
  ```

- `username`
  Логин для подключения к source БД.
  Можно указывать напрямую или через placeholder.

- `password`
  Пароль для подключения к source БД.
  Можно указывать напрямую или через placeholder.

- `sql`
  Необязательный SQL-запрос для конкретного источника.
  Если задан, он имеет приоритет над `commonSql`.
  Если не задан, используется `commonSql`.
  Пример:
  ```yaml
  sql: |
    select id, created_at, payload
    from special_table
  ```

### Блок `app.quotas`

Используется только при:

```yaml
mergeMode: quota
```

Каждый элемент задает долю строк от конкретного источника в итоговом `merged.csv`.

- `source`
  Имя источника из `sources.name`.

- `percent`
  Процент строк этого источника в итоговом merged-файле.

Правила:

- квота должна быть задана для каждого источника;
- сумма всех `percent` должна быть ровно `100`;
- проценты используются только в режиме `quota`.

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

### Блок `app.target`

Описывает целевую PostgreSQL БД, в которую загружается итоговый `merged.csv`.

- `enabled`
  Включена ли загрузка в target БД.
  Если `false`, приложение:
  - все равно выгрузит source CSV;
  - все равно соберет `merged.csv`;
  - не будет делать import в target БД.
  Пример:
  ```yaml
  enabled: false
  ```

- `jdbcUrl`
  JDBC URL целевой PostgreSQL БД.
  Обязателен, если `enabled: true`.

- `username`
  Логин для target БД.
  Обязателен, если `enabled: true`.

- `password`
  Пароль для target БД.
  Обязателен, если `enabled: true`.

- `table`
  Таблица, в которую грузится `merged.csv`.
  Формат:
  - `table`
  - или `schema.table`
  Примеры:
  ```yaml
  table: test_data_pool
  table: public.test_data_pool
  ```

- `truncateBeforeLoad`
  Нужно ли очищать target-таблицу перед загрузкой.
  Если `true`, перед `COPY` выполняется `TRUNCATE TABLE`.
  Если `false`, данные просто догружаются в таблицу.

### Placeholder-ы и credentials

Поля:

- `sources[].jdbcUrl`
- `sources[].username`
- `sources[].password`
- `target.jdbcUrl`
- `target.username`
- `target.password`

могут содержать placeholder вида:

```yaml
${SOME_KEY}
```

Значения ищутся в таком порядке:

1. файл из `-Dcredentials.file=/path/to/credential.properties`
2. `gradle/credential.properties`
3. переменные окружения
4. JVM system properties

Если значение не найдено:

- для source БД источник помечается ошибочным;
- для target БД запуск завершается ошибкой, если `target.enabled=true`.

Пример `gradle/credential.properties`:

```properties
DB1_JDBC_URL=jdbc:postgresql://localhost:5432/db1
DB1_USERNAME=user1
DB1_PASSWORD=secret1
DB2_JDBC_URL=jdbc:postgresql://localhost:5432/db2
DB2_USERNAME=user2
TARGET_JDBC_URL=jdbc:postgresql://localhost:5432/target_db
TARGET_USERNAME=loader_user
TARGET_DB_PASSWORD=target_secret
```

Если нужно только проверить выгрузку и `merged.csv` без загрузки в target БД, установите:

```yaml
target:
  enabled: false
```

Дополнительно:

- при `target.enabled=true` перед импортом выполняется preflight-проверка целевой таблицы;
- если в `merged.csv` есть колонки, которых нет в target таблице, запуск завершается до загрузки;
- если в target таблице есть обязательные `NOT NULL` колонки без default, которых нет в данных, запуск тоже завершается до загрузки.

## Режимы merge

- `plain` — просто склеить результаты по источникам.
- `round_robin` — брать по одной строке из каждого источника по кругу.
- `proportional` — распределять строки по источникам пропорционально фактическому объему каждой выгрузки и равномерно размазывать их по merged-файлу.
- `quota` — распределять строки по вручную заданным процентам и равномерно размазывать их по merged-файлу.

### `plain`

Самый простой режим.

Как работает:

- сначала полностью записываются строки первого успешного источника;
- затем второго;
- затем третьего;
- и так далее.

Пример:

- `db1`: `A1 A2 A3`
- `db2`: `B1 B2`
- `db3`: `C1 C2`

Результат:

```text
A1 A2 A3 B1 B2 C1 C2
```

Когда использовать:

- если нужно получить полный набор данных без перемешивания;
- если порядок по источникам не важен;
- если требуется самый предсказуемый и простой merge.

### `round_robin`

Режим строгого чередования.

Как работает:

- по одной строке из каждого источника по кругу;
- если у какого-то источника строки закончились, он исключается из дальнейшего обхода;
- merge продолжается по оставшимся.

Пример:

- `db1`: `A1 A2 A3`
- `db2`: `B1 B2`
- `db3`: `C1 C2 C3 C4`

Результат:

```text
A1 B1 C1 A2 B2 C2 A3 C3 C4
```

Когда использовать:

- если объемы источников близки;
- если хочется равномерного чередования без учета размеров выборок.

Ограничение:

- если один источник очень маленький, а другой очень большой, это не дает “процентно равномерного” распределения по всему файлу, а только чередование в начале merge.

### `proportional`

Режим автоматического равномерного распределения по фактическому объему данных.

Как работает:

- сначала приложение выгружает все источники в отдельные CSV и считает `rowCount`;
- затем доля каждого источника определяется автоматически по числу строк;
- после этого строки из каждого источника равномерно распределяются по merged-файлу в соответствии с этой долей.

Пример:

- `db1`: `10` строк
- `db2`: `90` строк

Тогда итоговый merge:

- будет содержать все `100` строк;
- примерно `10%` строк будут из `db1`;
- примерно `90%` строк будут из `db2`;
- строки `db1` будут размазаны по всему merged-файлу, а не лежать одним блоком.

Условный вид:

```text
B B B B B B B B B A B B B B B B B B B A ...
```

Когда использовать:

- если нужно автоматически получить равномерное смешивание по реальным объемам источников;
- если не хочется вручную задавать проценты.

### `quota`

Режим ручного управления долями данных по источникам.

Как работает:

- для каждого источника в конфиге задается процент;
- сумма процентов должна быть ровно `100`;
- сначала приложение выгружает все источники и узнает фактическое число строк по каждому;
- затем рассчитывается, сколько строк можно взять из каждого источника без дублирования строк;
- после этого выбранные строки равномерно распределяются по merged-файлу.

Пример конфига:

```yaml
app:
  mergeMode: quota
  commonSql: |
    select id, created_at, payload
    from some_table

  quotas:
    - source: db1
      percent: 10
    - source: db2
      percent: 30
    - source: db3
      percent: 60
```

#### Как считается допустимый итоговый объем

Допустимый объем считается только после фактической выгрузки, когда уже известны `rowCount` всех успешных источников.

Для каждого источника считается максимум:

```text
availableRows / quotaShare
```

Где:

- `availableRows` — сколько строк реально вернул источник;
- `quotaShare` — доля источника в виде числа от `0` до `1`.

Затем берется минимум по всем источникам.

Пример:

- `db1`: `10` строк, квота `10%`
- `db2`: `1000` строк, квота `90%`

Расчет:

- `db1`: `10 / 0.10 = 100`
- `db2`: `1000 / 0.90 = 1111`

Итоговый допустимый объем:

```text
100
```

Значит merged-файл будет содержать:

- `10` строк из `db1`
- `90` строк из `db2`

И все они будут равномерно распределены по merged-файлу.

#### Если один из источников завершился ошибкой

В проекте уже используется модель `continue_on_error`, поэтому:

- merge выполняется по успешным источникам;
- в режиме `quota` проценты нормализуются на успешный набор источников;
- если успешных источников недостаточно для построения merge, запуск завершится ошибкой.

#### Если источник вернул больше строк, чем вошло по квоте

Это нормальное поведение:

- отдельный source CSV сохраняется полностью;
- в `merged.csv` попадает только часть строк этого источника;
- строки не дублируются.

Когда использовать:

- если нужно явно управлять составом итоговой выборки;
- если требуется подготовка тестовых данных с заданной долей строк от каждого источника.

Пример `quota`:

```yaml
app:
  mergeMode: quota
  commonSql: |
    select id, created_at, payload
    from some_table

  quotas:
    - source: db1
      percent: 10
    - source: db2
      percent: 30
    - source: db3
      percent: 60
```

Для `quota`:

- сумма процентов должна быть ровно `100`;
- квота должна быть задана для каждого источника;
- если часть источников завершилась ошибкой, merge продолжается по успешным, а проценты нормализуются на оставшийся набор источников;
- итоговый объем merged-файла ограничивается доступным числом строк в каждом источнике, без дублирования строк.
