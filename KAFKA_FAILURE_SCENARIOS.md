# Kafka Failure Scenarios

Дата: 2026-04-24

Назначение документа:

- зафиксировать safety и boundary contract для Kafka-подсистемы;
- удержать Kafka-инструмент в локальной, предсказуемой архитектуре;
- явно описать failure scenarios, которые нельзя маскировать UI-упрощениями.

Связанные документы:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md)
- [BACKLOG.md](/Users/kwdev/DataPoolLoader/BACKLOG.md)

## 1. Базовый scope Kafka-подсистемы

Kafka-инструмент рассматривается как локальный cluster explorer и bounded operations tool.

Первый baseline включает только:

- catalog кластеров из `ui-application.yml`;
- metadata по кластерам, топикам и consumer groups;
- bounded reading сообщений;
- controlled produce;
- bounded create-topic admin path;
- settings UI для редактирования cluster catalog.

Первый baseline не включает:

- delete topic;
- ACL management;
- reset offsets;
- live tailing;
- background consumers;
- schema registry tooling;
- implicit offset commit.

## 2. Source of Truth

Source of truth для cluster connections:

- `ui.kafka` в `ui-application.yml`.

Kafka credentials и TLS material не должны жить в:

- `ui-compose-shared` store state;
- browser local state;
- persisted JSON state в `ui-server`.

Допустимые runtime inputs:

- стандартные Kafka client properties;
- `${KEY}` placeholders для secret values;
- `${file:/path/to/file}` placeholders для PEM material.

Для локального settings UI дополнительно допустим bounded server-side file chooser path:

- `JKS` fields сохраняются как filesystem path;
- `PEM` fields сохраняются как `${file:/abs/path}` placeholder;
- certificate targets в settings UI допускают `.crt`, `.cer` и `.pem`, private key target допускает `.key` и `.pem`;
- material file fields выбираются через системный file chooser и не редактируются ручным вводом path;
- settings UI должен показывать только поля, релевантные выбранному `JKS` или `PEM` material format;
- browser upload не становится вторым source of truth для TLS material.

## 3. Supported transport/security model

Поддерживаются только:

- `PLAINTEXT`;
- `SSL` для TLS и mTLS.

Первый baseline сознательно не поддерживает:

- `SASL_PLAINTEXT`;
- `SASL_SSL`;
- кастомные auth DSL поверх Kafka properties.

Поддерживаемые material formats:

- `JKS`;
- `PEM`.

## 4. Config contract

Kafka config должен оставаться `properties`-first:

- `id`, `name`, `readOnly` как thin shell;
- connection settings как стандартные Kafka keys;
- без отдельного nested `security { ... }` DSL.

Пример:

```yaml
ui:
  kafka:
    maxRecordsPerRead: 100
    pollTimeoutMs: 3000
    adminTimeoutMs: 5000
    maxPayloadBytes: 1048576
    clusters:
      - id: "local-plain"
        name: "Local Kafka"
        readOnly: false
        properties:
          bootstrap.servers: "localhost:19092"
          client.id: "datapool-loader"
          security.protocol: "PLAINTEXT"
      - id: "dev-mtls-pem"
        name: "DEV Kafka mTLS PEM"
        readOnly: true
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

## 5. Failure Scenarios

### 5.1. Invalid transport/material combination

Нельзя молча принимать конфиги вида:

- `security.protocol=PLAINTEXT` вместе с `ssl.*`;
- `ssl.truststore.type=PEM` без trust material;
- `ssl.keystore.type=PEM` только с certificate без key;
- `ssl.keystore.type=JKS` вместе с PEM inline fields;
- unsupported store types.

Такие конфиги должны падать как configuration error до начала реального Kafka operation path.

### 5.2. Missing file-backed PEM material

`${file:/...}` — это best-effort runtime resolution.

Если файл:

- отсутствует;
- unreadable;
- содержит неожиданный формат,

UI не должен переписывать исходный config и не должен silently invent fallback.

Ожидаемое поведение:

- raw config сохраняется как есть;
- конкретная Kafka operation падает с понятным error state;
- settings UI позже сможет показать path-level validation.

### 5.3. Partial access cluster

Kafka cluster может давать:

- metadata read;
- topic list;
- message read;
- produce

с разными ACL и с разной доступностью.

UI не должен трактовать `operation not authorized` как “кластер сломан”.

Правильная модель:

- cluster может быть partially usable;
- отдельные tabs/actions могут быть недоступны;
- authorization failure показывается per operation.

Для topic overview это означает отдельный contract:

- metadata топика и partition offsets не должны падать только потому, что недоступны consumer groups;
- consumer groups идут отдельным section-level state:
  - `AVAILABLE`;
  - `EMPTY`;
  - `ERROR`;
- если offsets или group descriptions недоступны только для части consumer groups, overview все равно должен рендериться, но section обязан честно показать warning, что список может быть неполным;
- `group metadata unavailable` не должна маскироваться под `0 consumers`.

### 5.4. Message reading must not pollute consumer groups

Message browser не должен использовать:

- `subscribe()`;
- implicit group ownership;
- auto commit.

Baseline reading path должен быть bounded и side-effect free:

- short-lived consumer;
- `assign + seek`;
- no commit.

Если инструмент начинает создавать consumer lag и новые group entries только из-за чтения сообщений, это считается дефектом.

Message browser также обязан:

- поддерживать два явных scope-режима:
  - `selected partition`;
  - `all partitions`;
- для `selected partition` читать только по явно выбранной partition;
- для `all partitions` читать bounded merged result по topic scope без fan-out в неограниченный объем;
- использовать `ui.kafka.maxRecordsPerRead` только как default/fallback для initial read, а не как hidden clamp поверх explicit UI limit;
- ограничивать payload rendering через `ui.kafka.maxPayloadBytes`;
- честно помечать `truncated` payload, а не делать вид, что отрисован полный message body;
- явно показывать `partition` и `offset` у каждой записи в merged result;
- различать `latest`, `explicit offset` и `timestamp` read modes без скрытого fallback на background session.
- относиться к `scope/mode/partition/limit/offset/timestamp` как к draft controls следующего запроса:
  - изменение этих полей само по себе не должно запускать read;
  - last successful result не должен исчезать до явного нажатия `Читать сообщения`.
- применять JSON-specific rendering только при успешном parse:
  - valid JSON может получать `jsonPrettyText` и syntax highlighting;
  - plain text и invalid JSON должны оставаться plain text;
  - parse failure не должен валить message read или web rendering path.
- поддерживать независимое раскрытие сообщений в таблице:
  - раскрытие одного сообщения не должно сворачивать другие уже раскрытые сообщения;
  - каждое раскрытое сообщение должно сворачиваться явным действием пользователя;
  - смена result set после нового чтения может сбрасывать UI-only expansion state.

### 5.5. Write operations on readOnly cluster

`readOnly=true` — это safety contract, а не cosmetic badge.

Если cluster помечен как `readOnly`, write paths должны быть запрещены до Kafka client call:

- produce;
- create topic.

Produce payload editor является UI-only функцией:

- valid JSON может подсвечиваться внутри editor до отправки;
- invalid JSON и plain text должны оставаться редактируемыми в том же editor;
- editor не должен автоматически менять фактический payload, который отправляется в Kafka;
- pretty-format JSON допустим только по явному действию пользователя и только для валидного JSON.

### 5.6. Create topic must fail explicitly on duplicate or invalid admin input

Create-topic path не должен прятать admin failures за generic UI message.

Ожидаемые явные failure cases:

- duplicate topic name;
- invalid partition count;
- invalid replication factor;
- invalid cleanup policy;
- invalid `retention.ms` / `retention.bytes`;
- authorization failure на topic create path.

Требования:

- validation простых числовых и semantic полей делается до Kafka client call;
- duplicate/authorization/admin failures не должны теряться в generic `500`;
- после failed create UI не должен делать вид, что topic создан и не должен silently обновлять catalog как будто операция прошла.

### 5.7. Kafka UI state must not become a second config source

Нельзя делать так, чтобы:

- connection settings жили одновременно в YAML и в hidden local state;
- screen state silently overrides `ui.kafka`;
- runtime-edited values переживали рестарт без явного сохранения в config.

Если settings UI меняет cluster catalog, единственный source of truth остается `ui-application.yml`.

Blank `ssl.truststore.type` / `ssl.keystore.type` не является отдельным Kafka mode:

- это только отсутствие явно заданного `type`;
- settings UI должен показывать это как `Не задано`, а не как `default`.

Settings UI также не должен становиться недоступным только из-за того, что текущий cluster catalog пуст:

- first-cluster onboarding обязан оставаться доступным;
- global empty-state не должен перекрывать `cluster-settings` screen;
- после успешного save shell-level cluster catalog должен обновляться без обязательного full page reload.

## 6. Architecture boundaries

По умолчанию:

- `core`:
  модели и contracts topic/group/message operations;
- `ui-server`:
  config loading, placeholder resolution, Kafka client factory, route orchestration, persisted local state только там, где он реально нужен;
- `ui-compose-shared`:
  store/state/view contracts;
- `ui-compose-web`:
  page composition, DOM/browser glue.

Нельзя:

- прятать Kafka connection logic в page-layer;
- складывать Kafka client creation в route handlers;
- смешивать Kafka state с SQL console state model.

## 7. Local development baseline

Для локальной разработки допустим Docker Desktop стенд:

- Kafka broker на `localhost:19092`;
- `kafka-ui` на `localhost:18085`.

Этот стенд служит только dev/test baseline и не должен диктовать продуктовый config contract.
