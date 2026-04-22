package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.config.CredentialsFileLocator
import java.nio.file.Path

fun UiAppConfig.defaultCredentialsPath(): Path? {
    val configured = defaultCredentialsFile?.trim()?.takeIf { it.isNotEmpty() }?.let { configuredPath ->
        val path = Path.of(configuredPath)
        if (path.isAbsolute) {
            path
        } else {
            configBaseDir?.let { Path.of(it).resolve(path).normalize() } ?: path
        }
    }
    if (configured != null) {
        return configured
    }
    return CredentialsFileLocator.find()
}

fun UiAppConfig.appsRootPath(): Path? =
    appsRoot?.trim()?.takeIf { it.isNotEmpty() }?.let {
        Path.of(it).toAbsolutePath().normalize()
    }

fun UiModuleStoreConfig.isDatabaseMode(): Boolean = mode == UiModuleStoreMode.DATABASE

fun UiModuleStorePostgresConfig.isConfigured(): Boolean =
    !jdbcUrl.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank()

fun UiModuleStorePostgresConfig.schemaName(): String =
    schema.trim().ifEmpty { UiModuleStorePostgresConfig.DEFAULT_SCHEMA }

fun UiAppConfig.storageDirPath(): Path {
    storageDir?.trim()?.takeIf { it.isNotEmpty() }?.let { configuredStorageDir ->
        val path = Path.of(configuredStorageDir)
        if (path.isAbsolute) {
            return path.normalize()
        }
        configBaseDir?.let {
            return Path.of(it).resolve(path).normalize()
        }
        return path.toAbsolutePath().normalize()
    }
    val userHome = System.getProperty("user.home")?.trim()?.takeIf { it.isNotEmpty() }
        ?: return Path.of(".datapool-loader/ui/storage").toAbsolutePath().normalize()
    return Path.of(userHome).resolve(".datapool-loader/ui/storage").toAbsolutePath().normalize()
}
