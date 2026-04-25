package com.sbrf.lt.platform.ui.config

import java.util.concurrent.TimeUnit

data class UiSecretProviderInfo(
    val providerId: String,
    val displayName: String,
    val available: Boolean,
    val unavailableReason: String? = null,
)

interface UiSecretProvider {
    fun providerInfo(): UiSecretProviderInfo

    fun readSecret(key: String): String?

    fun secretExists(key: String): Boolean = readSecret(key) != null

    fun saveSecret(
        key: String,
        value: String,
    )
}

fun defaultUiSecretProvider(): UiSecretProvider =
    when (currentOsFamily()) {
        UiOsFamily.MACOS -> MacOsKeychainSecretProvider()
        UiOsFamily.WINDOWS -> UnsupportedUiSecretProvider(
            providerId = "windows-credential-manager",
            displayName = "Windows Credential Manager",
            reason = "Windows Credential Manager provider еще не реализован.",
        )
        UiOsFamily.LINUX -> UnsupportedUiSecretProvider(
            providerId = "linux-secret-service",
            displayName = "Linux Secret Service",
            reason = "Linux Secret Service provider еще не реализован.",
        )
        UiOsFamily.OTHER -> UnsupportedUiSecretProvider(
            providerId = "unsupported",
            displayName = "System secret storage",
            reason = "Для этой операционной системы secret provider не настроен.",
        )
    }

internal enum class UiOsFamily {
    MACOS,
    WINDOWS,
    LINUX,
    OTHER,
}

internal fun currentOsFamily(): UiOsFamily {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        "mac" in osName || "darwin" in osName -> UiOsFamily.MACOS
        "win" in osName -> UiOsFamily.WINDOWS
        "linux" in osName -> UiOsFamily.LINUX
        else -> UiOsFamily.OTHER
    }
}

internal class UnsupportedUiSecretProvider(
    private val providerId: String,
    private val displayName: String,
    private val reason: String,
) : UiSecretProvider {
    override fun providerInfo(): UiSecretProviderInfo =
        UiSecretProviderInfo(
            providerId = providerId,
            displayName = displayName,
            available = false,
            unavailableReason = reason,
        )

    override fun readSecret(key: String): String? = null

    override fun saveSecret(
        key: String,
        value: String,
    ) {
        error(reason)
    }
}

internal class MacOsKeychainSecretProvider(
    private val commandRunner: UiSecretCommandRunner = ProcessUiSecretCommandRunner(),
) : UiSecretProvider {
    override fun providerInfo(): UiSecretProviderInfo {
        val result = commandRunner.run(listOf("/usr/bin/security", "-h"))
        return UiSecretProviderInfo(
            providerId = PROVIDER_ID,
            displayName = "macOS Keychain",
            available = result.exitCode == 0 || result.exitCode == 1,
            unavailableReason = if (result.exitCode == 0 || result.exitCode == 1) {
                null
            } else {
                "Команда security недоступна."
            },
        )
    }

    override fun readSecret(key: String): String? {
        requireSecretKey(key)
        val result = commandRunner.run(
            listOf(
                "/usr/bin/security",
                "find-generic-password",
                "-s",
                SERVICE_NAME,
                "-a",
                key,
                "-w",
            ),
        )
        return result.stdout.trim().takeIf { result.exitCode == 0 && it.isNotEmpty() }
    }

    override fun saveSecret(
        key: String,
        value: String,
    ) {
        requireSecretKey(key)
        require(value.isNotBlank()) { "Secret value не должен быть пустым." }
        val result = commandRunner.run(
            listOf(
                "/usr/bin/security",
                "add-generic-password",
                "-s",
                SERVICE_NAME,
                "-a",
                key,
                "-w",
                value,
                "-U",
            ),
        )
        check(result.exitCode == 0) {
            "Не удалось сохранить secret в macOS Keychain."
        }
    }

    private companion object {
        const val PROVIDER_ID = "macos-keychain"
        const val SERVICE_NAME = "DataPoolLoader SQL Console"
    }
}

internal interface UiSecretCommandRunner {
    fun run(command: List<String>): UiSecretCommandResult
}

internal data class UiSecretCommandResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
)

internal class ProcessUiSecretCommandRunner(
    private val timeoutSeconds: Long = 8L,
) : UiSecretCommandRunner {
    override fun run(command: List<String>): UiSecretCommandResult {
        return runCatching {
            val process = ProcessBuilder(command).start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return UiSecretCommandResult(exitCode = -1, stderr = "Command timeout.")
            }
            UiSecretCommandResult(
                exitCode = process.exitValue(),
                stdout = process.inputStream.bufferedReader().readText(),
                stderr = process.errorStream.bufferedReader().readText(),
            )
        }.getOrElse { error ->
            UiSecretCommandResult(exitCode = -1, stderr = error.message.orEmpty())
        }
    }
}

fun requireSecretKey(key: String) {
    require(key.matches(SECRET_KEY_PATTERN)) {
        "Secret key должен содержать только латиницу, цифры, '.', '_' или '-'."
    }
}

private val SECRET_KEY_PATTERN = Regex("^[A-Za-z0-9_.-]+$")
