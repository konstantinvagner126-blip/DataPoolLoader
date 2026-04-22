package com.sbrf.lt.platform.ui.run.history

internal fun filesStageFor(eventType: String): String =
    when (eventType) {
        "RunStartedEvent" -> "PREPARE"
        "SourceExportStartedEvent",
        "SourceExportProgressEvent",
        "SourceExportFinishedEvent",
        "SourceSchemaMismatchEvent" -> "SOURCE"
        "MergeStartedEvent", "MergeFinishedEvent" -> "MERGE"
        "TargetImportStartedEvent", "TargetImportFinishedEvent" -> "TARGET"
        else -> "RUN"
    }

internal fun filesSeverityFor(eventType: String, event: Map<String, Any?>): String =
    when (eventType) {
        "SourceSchemaMismatchEvent" -> "WARNING"
        "SourceExportFinishedEvent",
        "TargetImportFinishedEvent",
        "RunFinishedEvent" -> {
            when (event.statusValue()) {
                "FAILED" -> "ERROR"
                "SUCCESS" -> "SUCCESS"
                "SKIPPED" -> "INFO"
                else -> "INFO"
            }
        }
        "MergeFinishedEvent" -> "SUCCESS"
        else -> "INFO"
    }

internal fun filesMessageFor(eventType: String, event: Map<String, Any?>): String =
    when (eventType) {
        "RunStartedEvent" -> {
            val sourceCount = event.eventSourceNames().size
            val mergeMode = event.eventStringValue("mergeMode") ?: "-"
            "Запуск начат. Источников: $sourceCount, режим объединения: $mergeMode."
        }
        "SourceExportStartedEvent" -> "Начата выгрузка из источника ${event.eventSourceName()}."
        "SourceExportProgressEvent" -> "Источник ${event.eventSourceName()}: выгружено ${event.eventRowCount() ?: 0} строк."
        "SourceExportFinishedEvent" -> {
            if (event.statusValue() == "SUCCESS") {
                "Источник ${event.eventSourceName()} завершен успешно. Получено ${event.eventRowCount() ?: 0} строк."
            } else {
                "Источник ${event.eventSourceName()} завершился с ошибкой: ${event.eventErrorMessage() ?: "неизвестная ошибка"}."
            }
        }
        "SourceSchemaMismatchEvent" -> "Источник ${event.eventSourceName()} исключен из объединения: набор колонок отличается от базового."
        "MergeStartedEvent" -> "Начато объединение данных из ${event.eventSourceNames().size} успешных источников."
        "MergeFinishedEvent" -> "Объединение завершено. В merged.csv записано ${event.eventLongValue("rowCount") ?: 0} строк."
        "TargetImportStartedEvent" -> "Начата загрузка merged.csv в таблицу ${event.eventTableName()}."
        "TargetImportFinishedEvent" -> {
            when (event.statusValue()) {
                "SUCCESS" -> "Загрузка в таблицу ${event.eventTableName()} завершена. Загружено ${event.eventRowCount() ?: 0} строк."
                "SKIPPED" -> "Загрузка в target пропущена."
                else -> "Загрузка в таблицу ${event.eventTableName()} завершилась ошибкой: ${event.eventErrorMessage() ?: "неизвестная ошибка"}."
            }
        }
        "OutputCleanupEvent" -> "Удален временный файл ${event.eventStringValue("fileName")}."
        "RunFinishedEvent" -> {
            if (event.statusValue() == "SUCCESS") {
                "Запуск завершен успешно."
            } else {
                "Запуск завершен с ошибкой: ${event.eventErrorMessage() ?: "неизвестная ошибка"}."
            }
        }
        else -> event.toString()
    }
