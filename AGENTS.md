# AGENTS.md

Этот репозиторий нужно вести по явным архитектурным правилам, а не по импровизации.

Главный приоритет разработки:

- хорошая архитектура;
- надежность;
- расширяемость.

Если локальная скорость реализации конфликтует с этими критериями, приоритет у архитектуры, надежности и расширяемости.

## Обязательные документы

Перед архитектурно заметными изменениями обязательно читать:

- [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md)
- [PROJECT_ARCHITECTURE_REVIEW.md](/Users/kwdev/DataPoolLoader/PROJECT_ARCHITECTURE_REVIEW.md)

Если изменение затрагивает SQL-консоль, дополнительно обязательно читать:

- [SQL_CONSOLE_FAILURE_SCENARIOS.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_FAILURE_SCENARIOS.md)
- [SQL_CONSOLE_MONACO_AUTOCOMPLETE.md](/Users/kwdev/DataPoolLoader/SQL_CONSOLE_MONACO_AUTOCOMPLETE.md)

## Базовый контекст проекта

Проект рассматривается как локальное инженерное приложение:

- пользователь запускает его у себя на компьютере;
- архитектурные решения должны быть прагматичны для локального режима;
- не нужно тащить в проект случайные multi-user/web-product допущения, если это отдельно не согласовано.

## Обязательные правила работы

1. Новые изменения не должны противоречить [ARCHITECTURE_RULES.md](/Users/kwdev/DataPoolLoader/ARCHITECTURE_RULES.md).
2. Если задача требует нарушения правила, сначала нужно предложить изменить правило, а не молча нарушать его в коде.
3. Нельзя усиливать giant-file debt в `ui-compose-web` и `ui-compose-shared`.
4. Нельзя складывать новую orchestration-логику в route handlers.
5. Нельзя без явного обоснования добавлять новые глобальные mutable service.
6. Для long-running операций и SQL-консоли безопасность важнее удобства UI.
7. Разработка по умолчанию идет от интерфейсов и контрактов, а не от concrete-реализаций, если речь идет о значимом service/store/execution/boundary слое.
8. Нельзя перегружать интерфейс лишними данными, подсказками и дублирующими статусами, если они не помогают действию, безопасности или пониманию текущего состояния.
9. Если изменение меняет архитектурные инварианты проекта, нужно обновить соответствующий `.md`-документ в этом же изменении.

## Что делать при сомнении

Если неясно, куда должна жить логика:

- domain/business logic -> `core`
- transport/orchestration/persisted local state -> `ui-server`
- shared UI state/store/models -> `ui-compose-shared`
- page composition/components/browser glue -> `ui-compose-web`

Если этого недостаточно, нужно остановиться и сначала уточнить архитектурное решение, а потом писать код.
