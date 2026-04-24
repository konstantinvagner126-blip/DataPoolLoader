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
- ограничивать `limit` сверху через `ui.kafka.maxRecordsPerRead`;
- ограничивать payload rendering через `ui.kafka.maxPayloadBytes`;
- честно помечать `truncated` payload, а не делать вид, что отрисован полный message body;
- явно показывать `partition` и `offset` у каждой записи в merged result;
- различать `latest`, `explicit offset` и `timestamp` read modes без скрытого fallback на background session.

### 5.5. Write operations on readOnly cluster

`readOnly=true` — это safety contract, а не cosmetic badge.

Если cluster помечен как `readOnly`, write paths должны быть запрещены до Kafka client call:

- produce;
- create topic.

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
