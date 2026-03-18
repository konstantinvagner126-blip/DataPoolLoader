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
- формирует `merged.csv` в режимах `plain` и `round_robin`;
- загружает `merged.csv` в целевую PostgreSQL таблицу;
- пишет `summary.json` по результатам запуска;
- при ошибке одной БД продолжает работу по остальным.

## Запуск из IDEA

Точка входа первого приложения: `com.example.datapoolloader.app.MainKt`

Аргумент конфигурации:

```text
--config=/absolute/path/to/application.yml
```

Дополнительный аргумент для файла credentials:

```text
--credentials=/absolute/path/to/gradle/credential.properties
```

Если `--credentials` не передан, приложение попробует использовать `gradle/credential.properties`, если файл существует. Если аргумент `--config` не передан, используется [application.yml](/Users/kwdev/DataPoolLoader/apps/dc-sms-offer/src/main/resources/application.yml).

Сборка и запуск:

```text
./gradlew test
./gradlew :apps:dc-sms-offer:run
```

## Формат конфига

```yaml
app:
  outputDir: ./output
  fileFormat: csv
  mergeMode: round_robin
  errorMode: continue_on_error
  parallelism: 5
  fetchSize: 1000
  sql: |
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
