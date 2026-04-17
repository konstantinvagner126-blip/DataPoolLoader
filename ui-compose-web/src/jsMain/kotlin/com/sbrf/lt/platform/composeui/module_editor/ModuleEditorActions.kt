package com.sbrf.lt.platform.composeui.module_editor

import kotlinx.serialization.Serializable

@Serializable
data class SaveModuleRequestDto(
    val configText: String,
    val sqlFiles: Map<String, String>,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
)

@Serializable
data class SaveResultResponseDto(
    val message: String,
)

@Serializable
data class EmptyRequestDto(
    val placeholder: String? = null,
)

@Serializable
data class DatabaseRunStartRequestDto(
    val launchSourceKind: String? = null,
)

@Serializable
data class DatabaseRunStartResponseDto(
    val runId: String,
    val moduleCode: String,
    val status: String,
    val requestedAt: String,
    val launchSourceKind: String,
    val executionSnapshotId: String,
    val message: String,
)

@Serializable
data class StartRunRequestDto(
    val moduleId: String,
    val configText: String,
    val sqlFiles: Map<String, String>,
)

@Serializable
data class UiRunSnapshotDto(
    val id: String,
    val moduleId: String,
    val moduleTitle: String,
    val status: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val outputDir: String? = null,
    val mergedRowCount: Long = 0,
    val summaryJson: String? = null,
    val errorMessage: String? = null,
)
