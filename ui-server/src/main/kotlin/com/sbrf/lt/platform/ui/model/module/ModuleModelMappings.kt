package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.datapool.module.validation.ModuleValidationIssue
import com.sbrf.lt.datapool.module.validation.ModuleValidationResult

/**
 * Преобразования между внутренними моделями модулей и UI DTO.
 */
fun ModuleDescriptor.toCatalogItemResponse(): ModuleCatalogItemResponse = ModuleCatalogItemResponse(
    id = id,
    descriptor = toMetadataDescriptorResponse(),
    validationStatus = validationStatus,
    validationIssues = validationIssues,
    hasActiveRun = false,
)

fun ModuleDescriptor.toMetadataDescriptorResponse(): ModuleMetadataDescriptorResponse =
    ModuleMetadataDescriptorResponse(
        title = title,
        description = description,
        tags = tags,
        hiddenFromUi = hiddenFromUi,
    )

/**
 * Преобразует результат валидации модуля в упрощенный набор UI-ошибок.
 */
fun ModuleValidationResult.toResponse(): List<ModuleValidationIssueResponse> =
    issues.map { it.toResponse() }

/**
 * Возвращает строковое значение статуса валидации для UI.
 */
fun ModuleValidationResult.toStatusValue(): String = status.name

/**
 * Строит общую диагностику каталога по набору карточек модулей.
 */
fun List<ModuleCatalogItemResponse>.toDiagnosticsResponse(): ModuleCatalogDiagnosticsResponse =
    ModuleCatalogDiagnosticsResponse(
        totalModules = size,
        validModules = count { it.validationStatus == "VALID" },
        warningModules = count { it.validationStatus == "WARNING" },
        invalidModules = count { it.validationStatus == "INVALID" },
        totalIssues = sumOf { it.validationIssues.size },
    )

private fun ModuleValidationIssue.toResponse(): ModuleValidationIssueResponse =
    ModuleValidationIssueResponse(
        severity = severity.name,
        message = message,
    )
