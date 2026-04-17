package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse

/**
 * Редактируемое представление DB-модуля, которое UI получает из PostgreSQL registry.
 */
data class DatabaseEditableModule(
    val module: ModuleDetailsResponse,
    val sourceKind: String,
    val currentRevisionId: String,
    val workingCopyId: String? = null,
    val workingCopyStatus: String? = null,
    val baseRevisionId: String? = null,
)

/**
 * Внутренняя строка выборки DB-модуля из текущей revision или personal working copy.
 */
internal data class DatabaseEditableModuleRow(
    val moduleCode: String,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val validationStatus: String,
    val validationIssues: List<ModuleValidationIssueResponse>,
    val configText: String,
    val sourceKind: String,
    val currentRevisionId: String,
    val workingCopyId: String?,
    val workingCopyStatus: String?,
    val baseRevisionId: String?,
    val workingCopyJson: String?,
)

/**
 * Минимальная информация о DB-модуле, необходимая для сохранения personal working copy.
 */
internal data class DatabaseModuleForSave(
    val moduleId: String,
    val currentRevisionId: String,
    val workingCopyId: String?,
    val workingCopyStatus: String?,
)

/**
 * Данные DB-модуля и personal working copy, необходимые для публикации новой revision.
 */
internal data class ModuleForPublish(
    val moduleId: String,
    val currentRevisionId: String,
    val maxRevisionNo: Long,
    val hasWorkingCopy: Boolean,
    val baseRevisionId: String?,
    val workingCopyStatus: String?,
    val workingCopyJson: String?,
    val workingCopyYaml: String?,
    val contentHash: String?,
)

/**
 * Данные DB-модуля, необходимые для безопасного удаления из registry.
 */
internal data class ModuleForDelete(
    val moduleId: String,
    val hasActiveRun: Boolean,
)

/**
 * Черновик SQL asset перед вставкой в `module_revision_sql_asset`.
 */
internal data class SqlAssetDraft(
    val assetKey: String,
    val assetKind: String,
    val label: String,
    val sqlText: String,
)

/**
 * Результат публикации personal working copy в новую общую revision.
 */
data class PublishResult(
    val revisionId: String,
    val revisionNo: Long,
    val moduleCode: String,
)

/**
 * Запрос на создание DB-модуля в PostgreSQL registry.
 */
data class CreateModuleRequest(
    val title: String,
    val description: String? = null,
    val tags: List<String>? = null,
    val configText: String,
    val sqlFiles: Map<String, String> = emptyMap(),
    val hiddenFromUi: Boolean = true,
)

/**
 * Результат создания DB-модуля вместе с первой revision и personal working copy.
 */
data class CreateModuleResult(
    val moduleId: String,
    val moduleCode: String,
    val revisionId: String,
    val workingCopyId: String,
)

/**
 * Результат удаления DB-модуля из PostgreSQL registry.
 */
data class DeleteModuleResult(
    val moduleCode: String,
    val moduleId: String,
    val deletedBy: String,
)
