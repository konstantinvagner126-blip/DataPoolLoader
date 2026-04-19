# Project Architecture Review

Дата: 2026-04-19

Контекст оценки:

- проект рассматривается как локальное инженерное приложение;
- каждый пользователь запускает его на своем компьютере;
- не оценивается переход в нормальный multi-user web-режим;
- оцениваются архитектура, качество кода, слабые стороны и будущие доработки.

## 1. Краткий вывод

### Общая оценка

- как локальный инженерный инструмент проект жизнеспособен;
- архитектура уже не хаотичная и содержит реальные границы между `core`, `ui-server` и Compose UI;
- при этом проект уже вошел в стадию, где без дальнейшей архитектурной дисциплины стоимость изменений будет расти быстро.

### Мой строгий вывод

- это хороший, но тяжелый internal tool;
- это еще не аккуратно доведенный локальный продукт;
- главный риск уже не в том, что "что-то не работает", а в том, что слишком много важной логики и UI-поведения сконцентрировано в крупных файлах, глобальных сервисах и смешанных слоях ответственности.

Итоговая оценка как локального приложения:

- архитектура: `7/10`
- качество backend/core: `7.5/10`
- качество server integration слоя: `6/10`
- maintainability frontend: `5/10`
- операционная надежность long-running сценариев: `5.5/10`
- общая инженерная зрелость: `6.5/10`

## 2. Объем кодовой базы

Основной Kotlin-код:

- `core/src/main/kotlin`: 58 файлов, около `6543` строк
- `ui-server/src/main/kotlin`: 187 файлов, около `10914` строк
- `ui-compose-shared/src/commonMain/kotlin`: 32 файла, около `3942` строк
- `ui-compose-web/src/jsMain/kotlin`: 30 файлов, около `8997` строк

Итого:

- основной Kotlin-код: около `30396` строк
- Kotlin-тесты: около `10406` строк
- отношение тестового Kotlin-кода к основному: около `0.34`

Это уже не маленький проект. Это средняя инженерная кодовая база с полноценным backend, frontend, persisted state, DB-режимом, файловым режимом и отдельной SQL-консолью.

## 3. Что в архитектуре сделано хорошо

### 3.1. Есть реальное разделение по модулям

Сильная сторона проекта в том, что разделение на модули не декоративное:

- `core` держит бизнес-логику и execution pipeline;
- `ui-server` держит Ktor, API, persisted state и orchestration;
- `ui-compose-shared` держит shared models/store;
- `ui-compose-web` держит web UI;
- `ui-compose-desktop` изолирован и не размазывает desktop-специфику по проекту.

Это правильная база. Проект не выглядит как монолитный "один модуль на все".

### 3.2. Domain logic не утекла целиком в UI

Ключевой плюс: data-processing логика остается в `core`, а не живет в контроллерах и Compose-страницах. Это видно по:

- [ApplicationRunner.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/app/ApplicationRunner.kt)
- [SqlConsoleService.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleService.kt)
- [ModuleSyncService.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/module/sync/ModuleSyncService.kt)

Для локального приложения это уже хороший уровень инженерной дисциплины.

### 3.3. В проекте виден настоящий рефакторинг, а не только "дописывание сверху"

Это очень важный сигнал качества.

Крупные сервисы уже системно распиливаются на support-слои и контракты:

- `ApplicationRunner`
- `SqlConsoleService`
- `DatabaseModuleStore`
- `DatabaseRunStore`
- `RunManager`
- `DatabaseModuleRunService`

Это означает, что проект не застрял в необратимом legacy-состоянии.

### 3.4. Backend-тестов уже много

Особенно сильный пласт находится в `ui-server` и `core`:

- [ServerTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/server/ServerTest.kt)
- [SqlConsoleServiceTest.kt](/Users/kwdev/DataPoolLoader/core/src/test/kotlin/com/sbrf/lt/datapool/SqlConsoleServiceTest.kt)
- [RunManagerTest.kt](/Users/kwdev/DataPoolLoader/ui-server/src/test/kotlin/com/sbrf/lt/platform/ui/run/RunManagerTest.kt)

Это не означает высокой coverage автоматически, но означает, что backend уже не живет полностью без страховки.

## 4. Главные архитектурные слабости

### 4.1. `ui-server` перегружен ролью "всего сразу"

Сейчас `ui-server` одновременно является:

- HTTP API;
- хостом web UI;
- orchestrator длинных операций;
- persisted state container;
- runtime mode switch;
- SQL-console execution manager;
- local configuration mutation layer.

Главная точка концентрации:

- [UiServerContext.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/UiServerContext.kt)
- [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt)

Это пока еще работает, потому что приложение локальное. Но именно здесь уже накоплен серьезный structural debt.

Проблема не в том, что это "без DI". Проблема в том, что composition root становится knowledge-heavy и медленно превращается в сервис-локатор.

### 4.2. HTTP error model сделана слишком грубо

Сейчас в [Server.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/server/Server.kt) любой `Throwable` переводится в `400 Bad Request`.

Для локального инструмента это тоже плохо, потому что:

- реальные server-side дефекты маскируются под ошибки пользователя;
- дебаг становится дороже;
- UI не может различать валидационную проблему и внутренний сбой;
- журналирование и диагностика получаются смазанными.

Это надо исправлять даже без всякого multi-user сценария.

### 4.3. Persistence-модель слишком смешанная

Сейчас проект одновременно использует:

- in-memory mutable state;
- JSON state-файлы в `storageDir`;
- PostgreSQL registry/history;
- прямую работу с файловой системой `apps/*`.

Для локального приложения это допустимо, но только до определенного масштаба. Дальше это начинает ломать ясность архитектуры.

Слабое место здесь не само смешение, а отсутствие явной карты:

- что является source of truth;
- что является cache;
- что является recoverable state;
- что является transient runtime state.

Это особенно заметно в:

- [RunManager.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/run/RunManager.kt)
- [SqlConsoleStateService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleStateService.kt)
- [UiConfigPersistenceService.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/config/UiConfigPersistenceService.kt)

### 4.4. Frontend уже слишком монолитен

Самая слабая часть проекта по maintainability сейчас не backend, а Compose Web.

Крупные файлы:

- [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt) около `1994` строк
- [ModuleEditorPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorPage.kt) около `1658` строк
- [ModuleEditorConfigForm.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorConfigForm.kt) около `1035` строк
- [ModuleRunsPage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/module_runs/ModuleRunsPage.kt) около `1003` строк

Это уже не просто вопрос стиля. Это прямой сигнал:

- дорого ревьюить;
- дорого вносить изменения;
- сложно локализовать побочные эффекты;
- новые разработчики будут входить в экран, а не в систему компонентов.

Если этот слой не резать дальше, именно frontend станет главным тормозом проекта.

### 4.5. Store-слой на UI местами слишком толстый

Слабое место не только в больших страницах, но и в store-объектах.

Например:

- [ModuleEditorStore.kt](/Users/kwdev/DataPoolLoader/ui-compose-shared/src/commonMain/kotlin/com/sbrf/lt/platform/composeui/module_editor/ModuleEditorStore.kt)

Такие store начинают тянуть на себя:

- загрузку данных;
- mutation orchestration;
- error handling;
- side effects;
- runtime-mode branching;
- UX-логику.

Это уменьшает пользу от `ui-compose-shared`: shared слой остается, но внутри него растет собственный mini-backend.

### 4.6. SQL-консоль функционально сильная, но остается зоной повышенного риска

SQL-консоль в проекте уже сильнее, чем обычно бывает у internal tooling. Но именно поэтому требования к ней выше.

Слабые стороны:

- сложный lifecycle async execution;
- ручной `Commit / Rollback`;
- риск незавершенных сценариев;
- большой объем логики в связке `core + ui-server + web UI`.

Критичные файлы:

- [SqlConsoleService.kt](/Users/kwdev/DataPoolLoader/core/src/main/kotlin/com/sbrf/lt/datapool/sqlconsole/SqlConsoleService.kt)
- [SqlConsoleQueryManager.kt](/Users/kwdev/DataPoolLoader/ui-server/src/main/kotlin/com/sbrf/lt/platform/ui/sqlconsole/SqlConsoleQueryManager.kt)
- [SqlConsolePage.kt](/Users/kwdev/DataPoolLoader/ui-compose-web/src/jsMain/kotlin/com/sbrf/lt/platform/composeui/sql_console/SqlConsolePage.kt)

С архитектурной точки зрения SQL-консоль уже надо считать не "одним экраном", а отдельной подсистемой внутри проекта.

### 4.7. Безопасность long-running операций еще не доведена до конца

Для локального приложения это важнее, чем вопрос "много пользователей или нет".

Риски:

- закрытие вкладки при долгом SQL execution;
- pending transaction lifecycle;
- долгие выгрузки и run-state после частичного сбоя;
- разрыв между UI-состоянием и server-side execution.

Это не косметика. Это operational safety.

Особенно это касается:

- SQL-консоли;
- cleanup/retention;
- модульных запусков.

## 5. Оценка качества кода

### Что в коде хорошего

- naming в основном ровный;
- модули названы понятно;
- backend-код в среднем читаемый;
- рефакторинг идет в правильную сторону;
- доменные границы в `core` ощущаются;
- в DB-режиме много кода уже стало заметно лучше организовано, чем было раньше.

### Что тянет качество вниз

- oversized UI pages;
- слишком knowledge-rich routes;
- ручной composition root с высокой связанностью;
- неодинаковая строгость по error model;
- смешение runtime-state и persisted-state;
- слабое frontend test coverage.

### Моя оценка по слоям

- `core`: хороший средний уровень, местами выше среднего
- `ui-server`: функционально сильный, но архитектурно перегруженный
- `ui-compose-shared`: полезный слой, но есть риск разрастания store-логики
- `ui-compose-web`: самый дорогой в поддержке слой проекта
- `ui-compose-desktop`: пока скорее вспомогательный, чем опорный

## 6. Что в проекте нужно доработать в будущем

Ниже не wishlist, а обязательные будущие доработки, если проект продолжит расти.

### 6.1. Разделить operational state и user preferences

Нужно явно развести:

- runtime execution state;
- persisted history;
- UI preferences;
- глобальные настройки приложения.

Сейчас это различается не всегда достаточно четко.

### 6.2. Довести error model до инженерно честной

Минимум:

- validation error != internal error;
- UI должен видеть понятный тип ошибки;
- сервер должен писать нормальную диагностику;
- `Throwable -> 400` нужно убирать.

### 6.3. Продолжать распил frontend-экранов

Это уже не optional improvement.

Первый приоритет:

- `SqlConsolePage`
- `ModuleEditorPage`
- `ModuleRunsPage`

Их нужно доводить до модели:

- shell page;
- small feature blocks;
- отдельные action panels;
- отдельные visual components;
- минимально толстые store adapters.

### 6.4. Выделить SQL-консоль как самостоятельную подсистему

Нужно перестать мыслить ею как просто экраном.

Практически это означает:

- отдельные contracts;
- отдельный lifecycle model;
- отдельные failure-policy rules;
- отдельная safety-документация и тесты.

Часть этого уже начата, но доводить все равно придется.

### 6.5. Укрепить safety long-running операций

Приоритетно:

- транзакционная безопасность SQL-консоли;
- корректный lifecycle pending commit / rollback;
- понятное восстановление состояния после рестарта;
- строгая политика для закрытия вкладки, отмены и orphan execution.

### 6.6. Формализовать архитектурные правила проекта

Сейчас проект еще держится на инженерной дисциплине в головах.

Нужно явно зафиксировать:

- что может жить в `core`;
- что может жить в `ui-server`;
- что допустимо в `ui-compose-shared`;
- что запрещено тянуть в page/store;
- как оформляются новые support/interfaces;
- какой размер файла уже считается сигналом на декомпозицию.

Иначе долг снова начнет расти.

## 7. Что я бы запретил уже сейчас

Как строгий архитектор, я бы сразу запретил:

- добавлять новые экраны на `1000+` строк;
- складывать новую orchestration-логику в route handlers;
- писать новые "глобальные mutable service" без явного justification;
- расширять SQL-консоль без одновременной проверки failure-scenarios;
- смешивать UI preferences и operational config в одних и тех же persisted моделях.

Это не вкусовщина. Это прямые меры против дальнейшего разрастания долга.

## 8. Итог

Если смотреть строго и без скидок:

- проект уже достаточно силен как локальный инженерный инструмент;
- backend/core в целом на хорошем уровне;
- главный долг сейчас не в "плохих алгоритмах", а в распределении ответственности и размере UI-слоев;
- архитектура уже требует не просто feature delivery, а постоянного архитектурного контроля;
- без этого через несколько итераций проект станет заметно дороже в поддержке, чем должен быть.

Если смотреть прагматично:

- фундамент у проекта хороший;
- кодовая база уже достаточно взрослая;
- все основные проблемы исправимы;
- но дальше нельзя расти только за счет добавления новых функций.

Следующий правильный этап для проекта:

1. продолжать feature development;
2. параллельно системно резать UI-монолиты;
3. ужесточить error model и operational safety;
4. формализовать архитектурные правила на уровне repo.
