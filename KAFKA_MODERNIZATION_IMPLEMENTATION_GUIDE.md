# Kafka Modernization Implementation Guide

Дата: 2026-04-25

Назначение документа:

- зафиксировать практический план переноса согласованного Kafka mockup в product UI;
- сохранить текущие Kafka behavior/safety contracts при визуальной переписке;
- определить порядок работ, границы компонентов, проверки и stop-criteria.

Этот документ является implementation contract для backlog section 19:

- [BACKLOG.md](/Users/kwdev/DataPoolLoader/BACKLOG.md)

Исходный визуальный артефакт:

- [design/kafka-modernization/index.html](/Users/kwdev/DataPoolLoader/design/kafka-modernization/index.html)

Обязательные связанные документы:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md)
- [KAFKA_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/KAFKA_FAILURE_SCENARIOS.md)

## 1. Главный принцип переноса

Макет переносится как visual rewrite поверх уже работающих Kafka contracts.

Запрещено при redesign:

- менять source of truth Kafka config: `ui.kafka` в `ui-application.yml`;
- превращать settings screen или browser state во второй config source;
- менять bounded message read contract `assign + seek / no commit`;
- добавлять background consumers, live tailing, reset offsets, ACL, delete topic или schema registry;
- ослаблять `readOnly` блокировку для produce/create-topic;
- переносить Kafka client creation или config mutation logic в `ui-compose-web`;
- складывать новую orchestration logic в route handlers;
- маскировать partial metadata/ACL failures как полный cluster failure;
- возвращать card-stack presentation для message list.

Разрешено:

- менять visual shell, CSS и компонентную композицию в `ui-compose-web`;
- дробить Kafka UI sections на более узкие Compose-компоненты;
- переставлять существующие controls и sections;
- добавлять thin view adapters для presentation-only layout;
- расширять server/store contracts только отдельными bounded задачами, если UI-контракт требует новой операции.

## 2. Что уже есть и должно сохраниться

### 2.1. Page shell, route и store wiring

Текущие файлы:

- [KafkaPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaPage.kt)
- [KafkaPageContentSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaPageContentSections.kt)
- [KafkaRoute.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaRoute.kt)
- [KafkaStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaStore.kt)

Нужно сохранить:

- selected cluster;
- cluster section;
- selected topic;
- active pane;
- topic filter;
- message read scope/mode/partition route state;
- settings pane route behavior;
- route replacement через `buildKafkaPageHref`;
- no full page reload after settings save when store can update in-place.

Target UI меняет:

- большой `PageScaffold` hero на compact tool shell;
- sidebar на более плотный cluster rail;
- main content на table-first/toolbar-first screens.

### 2.2. Cluster rail и navigation

Текущие файлы:

- [KafkaShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaShellSections.kt)
- [KafkaPageContentSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaPageContentSections.kt)

Нужно сохранить:

- cluster catalog из `state.info.clusters`;
- active cluster highlighting;
- navigation sections `topics`, `consumer-groups`, `brokers`;
- settings action;
- read-only cluster indication.

Target shell:

- compact Kafka tool header;
- left rail with cluster selector/catalog;
- cluster navigation in the rail;
- recent/selected topic affordances if available from current state;
- no decorative large hero copy.

### 2.3. Topics catalog

Текущие файлы:

- [KafkaOverviewSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaOverviewSections.kt)
- [KafkaStoreLoadingSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaStoreLoadingSupport.kt)
- [KafkaApi.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaApi.kt)

Нужно сохранить:

- topic filter draft;
- explicit apply/reload;
- topic table;
- internal topic badge;
- selected topic route;
- create-topic action on topics screen.

Target UI:

- table-first catalog;
- compact toolbar above table;
- status/count chips;
- row-click navigation;
- create-topic form as a bounded inline/tool panel, not hidden in settings.

### 2.4. Topic details

Текущие файлы:

- [KafkaTopicDetailsSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaTopicDetailsSections.kt)
- [KafkaOverviewSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaOverviewSections.kt)

Нужно сохранить:

- topic overview metadata;
- partition table;
- partition offsets/load;
- reload topic overview;
- tabs/panes `Overview`, `Messages`, `Consumers`, `Settings`, `Produce`;
- section-level warning/error for partial consumer metadata.

Target UI:

- breadcrumb back to topics;
- compact topic header;
- dense summary metrics;
- clear tabs;
- no topic failure just because groups/lag are partially unavailable.

### 2.5. Messages browser

Текущие файлы:

- [KafkaMessageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaMessageSections.kt)
- [KafkaStoreMessageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaStoreMessageSupport.kt)
- [UiKafkaMessageService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/kafka/UiKafkaMessageService.kt)

Нужно сохранить:

- `selected partition / all partitions`;
- `LATEST / OFFSET / TIMESTAMP`;
- explicit UI limit;
- offset/timestamp inputs;
- explicit `Читать сообщения`;
- last successful result remains visible until next explicit read;
- read result summary `DONE / ms / bytes / messages consumed`;
- table-first message list;
- selected/expanded message details;
- valid JSON pretty/highlight rendering;
- plain text fallback for non-JSON and invalid JSON.

Нельзя:

- запускать read при изменении scope/mode/limit;
- скрывать results до явного read;
- использовать `subscribe()` или consumer group;
- делать JSON parse failure причиной падения renderer.

Target UI:

- максимально близко к Provectus Kafka UI для messages screen;
- controls above table;
- consume summary before table;
- selected message inline expanded row / inspector;
- tabs `Value / Key / Headers / Metadata`;
- no card-list presentation.

### 2.6. Produce и create-topic

Текущие файлы:

- [KafkaProduceSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaProduceSections.kt)
- [KafkaStoreProduceSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaStoreProduceSupport.kt)
- [KafkaStoreTopicAdminSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaStoreTopicAdminSupport.kt)
- [UiKafkaProduceService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/kafka/UiKafkaProduceService.kt)
- [UiKafkaTopicAdminService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/kafka/UiKafkaTopicAdminService.kt)

Нужно сохранить:

- readOnly block before Kafka client call;
- partition override;
- key input;
- structured headers rows;
- high payload editor;
- delivery result summary;
- create topic fields:
  - topic name;
  - partitions;
  - replication factor;
  - cleanup policy;
  - retention.ms;
  - retention.bytes;
- explicit failure states for duplicate/invalid/admin/authorization errors.

Target UI:

- produce form closer to Kafka UI;
- payload editor stays large enough for real JSON/manual payloads;
- create-topic remains on topics screen and follows compact tool-form style.

### 2.7. Consumer groups, brokers и settings

Текущие файлы:

- [KafkaClusterConsumerGroupsSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaClusterConsumerGroupsSections.kt)
- [KafkaConsumerGroupSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaConsumerGroupSections.kt)
- [KafkaClusterBrokersSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaClusterBrokersSections.kt)
- [KafkaSettingsSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaSettingsSections.kt)
- [KafkaStoreSettingsSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaStoreSettingsSupport.kt)
- [UiKafkaSettingsService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/kafka/UiKafkaSettingsService.kt)
- [UiKafkaSettingsFilePicker.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/kafka/UiKafkaSettingsFilePicker.kt)

Нужно сохранить:

- groups list and selected group details;
- lag table;
- brokers metadata;
- controller/rack/endpoint info;
- settings cluster add/edit/remove;
- PLAINTEXT/SSL;
- readOnly flag;
- test connection;
- local file chooser for JKS/PEM/cert/key fields;
- PEM fields as `${file:/abs/path}` placeholders.

Target UI:

- same compact shell as other Kafka screens;
- settings remains properties-first editor;
- empty cluster catalog still allows opening settings and adding first cluster.

## 3. Migration phases

### Phase 0. Documentation and gate

Goal:

- keep product code unchanged;
- create this guide;
- link guide from backlog section 19;
- make future Kafka redesign patches follow this guide.

Exit criteria:

- this guide exists;
- backlog section 19.9 references it;
- product Kafka code is unchanged in this phase.

### Phase 1. Compact shell and rail

Goal:

- replace hero-like Kafka page entry with compact Kafka tool shell;
- keep all existing callbacks/store wiring;
- avoid touching server/store contracts.

Suggested changes:

- update [KafkaPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaPage.kt) to stop using large `PageScaffold` hero for Kafka;
- update [KafkaPageContentSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaPageContentSections.kt) for new shell layout only;
- update [KafkaShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaShellSections.kt) for cluster rail;
- add/update CSS only in [45-kafka.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/45-kafka.css).

Exit criteria:

- cluster selection still works;
- section navigation still works;
- settings screen still opens when cluster catalog is empty;
- route parameters remain stable.

### Phase 2. Topics catalog and topic overview

Goal:

- align topics catalog and topic overview with agreed table-first mockup.

Suggested changes:

- densify topic filter toolbar;
- keep create-topic action on topics screen;
- keep topic row navigation;
- compact topic header and tabs;
- preserve partition and consumer metadata states.

Exit criteria:

- topic filter/apply works;
- topic selection opens overview;
- topic reload works;
- create-topic form remains functional;
- partial group metadata warning remains visible.

### Phase 3. Messages browser

Goal:

- implement Kafka UI-like messages screen without changing bounded read semantics.

Suggested sequence:

1. Keep existing store/server read contract.
2. Rework controls area into compact filter/seek toolbar.
3. Keep controls as draft state only.
4. Keep last successful result visible.
5. Render summary `DONE / ms / bytes / messages consumed`.
6. Render messages as table with expander/inspector.
7. Preserve valid JSON syntax highlighting and plain fallback.

Exit criteria:

- selected partition read works;
- all partitions read works;
- changing scope/mode/limit does not clear existing result before read;
- invalid JSON/non-JSON message renders without error;
- browser has no `DOMTokenList` or syntax errors.

### Phase 4. Produce and create-topic forms

Goal:

- align write/admin forms with compact tool design.

Rules:

- keep readOnly block before Kafka client call;
- keep structured headers;
- keep large payload editor;
- keep create-topic validation/failure states.

Exit criteria:

- produce success and failure summary still render;
- create-topic success updates catalog and opens topic;
- failed create does not fake success.

### Phase 5. Consumer groups, brokers and settings consistency

Goal:

- align cluster-level screens with new shell.

Rules:

- preserve partial failure semantics;
- preserve settings source of truth;
- preserve file picker affordances;
- no hidden config state.

Exit criteria:

- groups reload works;
- brokers reload works;
- settings load/save/test connection works;
- first-cluster onboarding remains available.

### Phase 6. Visual regression and cleanup

Goal:

- verify main Kafka screens in browser;
- remove obsolete Kafka CSS/classes only when no longer referenced;
- update backlog/history.

Browser screens to check:

- `/kafka`;
- topics catalog;
- topic overview;
- messages;
- produce;
- create topic;
- consumer groups;
- brokers;
- settings;
- empty cluster catalog/settings path if feasible with fixture or mocked config.

## 4. State boundaries

Do not introduce new persisted state unless explicitly classified.

Current relevant state:

- route state: selected cluster, section, topic, pane, message scope/mode/partition;
- server config source of truth: `ui.kafka` in `ui-application.yml`;
- transient UI draft state: topic query, message read draft controls, produce draft, create-topic draft, settings draft;
- operation results: topic metadata, message read result, produce result, create-topic result, groups/brokers result;
- TLS material paths: persisted only through config save, not browser-local secret state.

Examples:

- expanded message row can stay transient UI state;
- selected message detail tab can stay transient UI state;
- table column width should stay UI-local unless user explicitly asks to persist it;
- settings draft is not source of truth until save;
- `readOnly` is cluster config, not a badge-only UI hint.

If a change introduces a new state boundary, update [STATE_MODEL_MAP.md](/Users/kwdev/DataPoolLoader/STATE_MODEL_MAP.md).

## 5. File-level implementation map

Likely UI files:

- [KafkaPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaPage.kt)
- [KafkaPageContentSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaPageContentSections.kt)
- [KafkaShellSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaShellSections.kt)
- [KafkaOverviewSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaOverviewSections.kt)
- [KafkaTopicDetailsSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaTopicDetailsSections.kt)
- [KafkaMessageSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaMessageSections.kt)
- [KafkaProduceSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaProduceSections.kt)
- [KafkaClusterConsumerGroupsSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaClusterConsumerGroupsSections.kt)
- [KafkaClusterBrokersSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaClusterBrokersSections.kt)
- [KafkaSettingsSections.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaSettingsSections.kt)

Likely CSS:

- [45-kafka.css](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/resources/styles/45-kafka.css)

Shared/server files should change only for bounded behavior fixes:

- [KafkaStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaStore.kt)
- [KafkaStoreMessageSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaStoreMessageSupport.kt)
- [KafkaStoreSettingsSupport.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/kafka/KafkaStoreSettingsSupport.kt)
- [UiKafkaMessageService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/kafka/UiKafkaMessageService.kt)
- [UiKafkaSettingsService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/kafka/UiKafkaSettingsService.kt)

## 6. Regression checklist

Before each implementation patch:

- verify task exists in backlog;
- verify changed layer matches architecture rules;
- keep unrelated dirty files untouched;
- keep `KAFKA_FAILURE_SCENARIOS.md` contracts intact.

Manual/browser checks:

- Kafka page loads with configured clusters;
- no old large marketing hero;
- cluster selector/rail works;
- topics catalog loads;
- topic filter applies only on action;
- topic details open;
- topic tabs switch;
- selected partition read works;
- all partitions read works;
- changing message controls does not clear last successful result;
- message result summary shows status/time/bytes/count;
- valid JSON has JSON rendering;
- invalid JSON and plain text do not crash;
- produce form remains usable;
- create-topic form remains usable;
- readOnly cluster blocks produce/create-topic;
- consumer groups screen opens and reloads;
- brokers screen opens and reloads;
- settings opens, saves and supports file picker affordances;
- empty cluster catalog can still open settings;
- browser console has no runtime errors.

Automated checks:

- `./gradlew :ui-compose-web:compileKotlinJs` after UI-only patches;
- targeted `:ui-compose-shared` / `:ui-server` tests when store/server contracts change;
- no Playwright visual suite until UI stabilizes, unless a specific regression requires targeted browser smoke.

## 7. Stop criteria and rollback plan

Stop and reassess if:

- visual rewrite requires changing Kafka read semantics;
- settings needs a second hidden config source;
- write/admin operations bypass `readOnly`;
- message rendering needs background consumer/session state;
- route handlers start accumulating config mutation or Kafka client creation logic;
- a new state boundary appears but is not represented in `STATE_MODEL_MAP.md`.

Rollback strategy:

- keep phases small;
- first product patch should be shell-only and reversible;
- avoid server/store changes during pure layout work;
- keep old topic/message/produce behavior wired while changing presentation;
- update backlog status after each bounded phase.

## 8. Definition of done

Kafka modernization can be considered complete only when:

- compact Kafka tool shell replaces old hero;
- cluster rail and navigation match agreed direction;
- topics catalog is table-first and dense;
- topic details/tabs are compact and preserve metadata states;
- messages browser follows Kafka UI-like table + expanded details model;
- produce and create-topic forms are compact and functional;
- consumer groups, brokers and settings follow the same visual language;
- bounded read, no-commit, no-background-consumer, readOnly and config source-of-truth contracts are preserved;
- regression checklist is completed;
- backlog section 19 tasks are moved to history after implementation.
