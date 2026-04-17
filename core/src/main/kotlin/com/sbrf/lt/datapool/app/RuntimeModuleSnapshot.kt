package com.sbrf.lt.datapool.app

import com.sbrf.lt.datapool.model.AppConfig

/**
 * Immutable runtime-снимок модуля, подготовленный к запуску.
 * Раннер работает с этим контрактом и не зависит от исходного места хранения конфигурации.
 */
data class RuntimeModuleSnapshot(
    /** Код модуля в исходном хранилище. */
    val moduleCode: String?,
    /** Заголовок модуля для UI/логов. */
    val moduleTitle: String?,
    /** YAML-конфиг в исходном виде, если он нужен для истории или диагностики. */
    val configYaml: String?,
    /** SQL-артефакты, подготовленные вместе с конфигом для запуска. */
    val sqlFiles: Map<String, String>,
    /** Полностью разрешенный и провалидированный runtime-конфиг. */
    val appConfig: AppConfig,
    /** Откуда получен снимок: `FILES`, `DATABASE` и т.п. */
    val launchSourceKind: String,
    /** Идентификатор execution snapshot для DB-режима, если он создан. */
    val executionSnapshotId: String? = null,
    /** Человекочитаемое описание источника конфигурации для событий и логов. */
    val configLocation: String,
)
