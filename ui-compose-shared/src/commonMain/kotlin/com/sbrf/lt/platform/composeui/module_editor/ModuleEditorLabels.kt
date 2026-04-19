package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse

fun buildCatalogStatus(
    state: ModuleEditorPageState,
    storage: String,
): String {
    return if (storage == "database") {
        val diagnostics = state.databaseCatalog?.diagnostics
        buildString {
            append("Каталог модулей из базы данных.")
            diagnostics?.let {
                append(" Всего: ${it.totalModules}.")
                append(" Исправных: ${it.validModules}, с предупреждениями: ${it.warningModules}, с ошибками: ${it.invalidModules}.")
                append(" Замечаний: ${it.totalIssues}.")
            }
        }
    } else {
        val filesCatalog = state.filesCatalog
        val appsRootStatus = filesCatalog?.appsRootStatus
        val diagnostics = filesCatalog?.diagnostics
        buildString {
            append(appsRootStatus?.message ?: "Состояние каталога файловых модулей пока недоступно.")
            diagnostics?.let {
                append(" Всего модулей: ${it.totalModules}.")
                append(" Исправных: ${it.validModules}, с предупреждениями: ${it.warningModules}, с ошибками: ${it.invalidModules}.")
                append(" Замечаний: ${it.totalIssues}.")
            }
        }
    }
}

fun buildDraftStatusText(
    route: ModuleEditorRouteState,
    session: ModuleEditorSessionResponse,
    hasDraftChanges: Boolean,
): String =
    if (route.storage == "database") {
        when {
            hasDraftChanges -> "Есть несохраненные локальные изменения."
            !session.workingCopyId.isNullOrBlank() -> "Есть личный черновик."
            else -> "Изменений в личном черновике нет."
        }
    } else {
        if (hasDraftChanges) {
            "Есть несохраненные изменения."
        } else {
            "Изменений нет."
        }
    }

fun buildCredentialsStatusText(status: CredentialsStatusResponse): String {
    val sourceLabel = when {
        status.uploaded -> "загружен через UI"
        status.mode.equals("FILE", ignoreCase = true) -> "файл по умолчанию"
        else -> "файл не задан"
    }
    val availability = if (status.fileAvailable) "доступен" else "не найден"
    return "$sourceLabel: ${status.displayName} ($availability)"
}

fun buildCredentialsWarningText(module: ModuleDetailsResponse): String {
    return when {
        !module.requiresCredentials -> {
            "У этого модуля нет обязательных placeholders \${...}. credential.properties нужен только для модулей и SQL-источников, где параметры вынесены во внешний файл."
        }
        module.credentialsReady -> {
            "Для модуля все обязательные placeholders \${...} сейчас разрешаются. При необходимости credential.properties можно заменить загрузкой через UI."
        }
        module.credentialsStatus.fileAvailable -> {
            val missingKeysText = module.missingCredentialKeys
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ", prefix = " Не хватает значений: ", postfix = ".")
                .orEmpty()
            "Для модуля найден credential.properties, но обязательные placeholders разрешены не полностью.$missingKeysText Также проверяются переменные окружения и JVM system properties."
        }
        else -> {
            val missingKeysText = module.missingCredentialKeys
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ", prefix = " Не хватает значений: ", postfix = ".")
                .orEmpty()
            "В конфиге модуля найдены placeholders \${...}, но подходящие значения сейчас не найдены.$missingKeysText Сначала ищется ui.defaultCredentialsFile, затем gradle/credential.properties в проекте, затем ~/.gradle/credential.properties. Если значений нет, загрузи файл через UI или задай их через env/JVM."
        }
    }
}

fun translateSourceKind(sourceKind: String?): String =
    when (sourceKind?.uppercase()) {
        "WORKING_COPY" -> "Личный черновик"
        "CURRENT_REVISION" -> "Текущая ревизия"
        null -> "-"
        else -> sourceKind
    }

fun translateValidationStatus(validationStatus: String): String =
    when (validationStatus.uppercase()) {
        "VALID" -> "Валидно"
        "WARNING" -> "Есть предупреждения"
        else -> "Есть ошибки"
    }

fun validationBadgeClass(validationStatus: String): String =
    when (validationStatus.uppercase()) {
        "VALID" -> "module-validation-badge-valid"
        "WARNING" -> "module-validation-badge-warning"
        else -> "module-validation-badge-invalid"
    }

fun buildExternalSqlAlertText(
    externalRef: String,
    storageMode: String,
): String =
    if (storageMode == "database") {
        "Обнаружена внешняя SQL-ссылка: $externalRef. Для DB-режима поддерживаются только встроенный SQL и SQL-ресурсы самого модуля."
    } else {
        "Используется внешняя SQL-ссылка: $externalRef. Она сохраняется, но полноценно управляется только через application.yml."
    }
