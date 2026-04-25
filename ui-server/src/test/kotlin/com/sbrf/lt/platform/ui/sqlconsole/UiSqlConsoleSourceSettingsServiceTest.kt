package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceGroupConfig
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.config.UiSecretProvider
import com.sbrf.lt.platform.ui.config.UiSecretProviderInfo
import com.sbrf.lt.platform.ui.model.SqlConsoleEditableSourceRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsCredentialsDiagnosticsRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsUpdateRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiSqlConsoleSourceSettingsServiceTest {

    @Test
    fun `load settings hides non-placeholder password values`() {
        val current = UiAppConfig(
            sqlConsole = SqlConsoleConfig(
                sourceCatalog = listOf(
                    SqlConsoleSourceConfig(
                        name = "db1",
                        jdbcUrl = "jdbc:test",
                        username = "user",
                        password = "raw-secret",
                    ),
                ),
            ),
        )
        val service = sourceSettingsService(current)

        val settings = service.loadSettings(current)

        assertEquals("", settings.sources.single().passwordReference)
        assertTrue(settings.sources.single().passwordConfigured)
    }

    @Test
    fun `save settings preserves hidden password and hot reloads sql console catalog`() {
        val credentialsFile = Files.createTempFile("credential", ".properties")
        val current = UiAppConfig(
            sqlConsole = SqlConsoleConfig(
                sourceCatalog = listOf(
                    SqlConsoleSourceConfig(
                        name = "db1",
                        jdbcUrl = "jdbc:old",
                        username = "old_user",
                        password = "\${db1.password}",
                    ),
                ),
                groups = listOf(SqlConsoleSourceGroupConfig(name = "main", sources = listOf("db1"))),
            ),
        )
        val persistence = CapturingSqlSourcePersistence(current)
        val sqlConsoleService = SqlConsoleService(current.sqlConsole)
        val secretProvider = FakeUiSecretProvider()
        val service = UiSqlConsoleSourceSettingsService(
            uiConfigPersistenceService = persistence,
            runtimeConfigResolver = UiRuntimeConfigResolver(secretProvider = secretProvider),
            sqlConsoleService = sqlConsoleService,
            secretProvider = secretProvider,
        )

        service.saveSettings(
            request = SqlConsoleSourceSettingsUpdateRequest(
                defaultCredentialsFile = credentialsFile.toString(),
                sources = listOf(
                    SqlConsoleEditableSourceRequest(
                        originalName = "db1",
                        name = "db1",
                        jdbcUrl = "jdbc:new",
                        username = "new_user",
                        passwordReference = "",
                        keepExistingPassword = true,
                    ),
                ),
                groups = listOf(),
            ),
            currentUiConfig = current,
        )

        assertEquals("\${db1.password}", persistence.updatedSourceCatalog.single().password)
        assertEquals(listOf("db1"), sqlConsoleService.info().sourceCatalog.map { it.name })
        assertEquals("jdbc:new", persistence.updatedSourceCatalog.single().jdbcUrl)
    }

    @Test
    fun `save system keychain source stores secret reference and hides plaintext`() {
        val current = UiAppConfig(sqlConsole = SqlConsoleConfig(sourceCatalog = emptyList()))
        val persistence = CapturingSqlSourcePersistence(current)
        val secretProvider = FakeUiSecretProvider()
        val service = UiSqlConsoleSourceSettingsService(
            uiConfigPersistenceService = persistence,
            runtimeConfigResolver = UiRuntimeConfigResolver(secretProvider = secretProvider),
            sqlConsoleService = SqlConsoleService(current.sqlConsole),
            secretProvider = secretProvider,
        )

        val saved = service.saveSettings(
            request = SqlConsoleSourceSettingsUpdateRequest(
                sources = listOf(
                    SqlConsoleEditableSourceRequest(
                        name = "db1",
                        credentialsMode = "SYSTEM_KEYCHAIN",
                        jdbcUrl = "jdbc:postgresql://localhost/db",
                        username = "user",
                        passwordPlainText = "secret",
                    ),
                ),
            ),
            currentUiConfig = current,
        )

        assertEquals("secret", secretProvider.savedSecrets["sqlConsole.sources.db1.password"])
        assertEquals("\${secret:sqlConsole.sources.db1.password}", persistence.updatedSourceCatalog.single().password)
        assertEquals("", saved.sources.single().passwordPlainText)
        assertEquals("SYSTEM_KEYCHAIN", saved.sources.single().credentialsMode)
    }

    @Test
    fun `diagnose credentials reports required and missing placeholder keys without values`() {
        val credentialsFile = Files.createTempFile("credential", ".properties").apply {
            writeText(
                """
                db1.user=alice
                db1.password=secret
                """.trimIndent(),
            )
        }
        val current = UiAppConfig(
            sqlConsole = SqlConsoleConfig(
                sourceCatalog = listOf(
                    SqlConsoleSourceConfig(
                        name = "db1",
                        jdbcUrl = "jdbc:postgresql://localhost/db",
                        username = "\${db1.user}",
                        password = "\${db1.password}",
                    ),
                ),
            ),
        )
        val service = sourceSettingsService(current)

        val diagnostics = service.diagnoseCredentials(
            request = SqlConsoleSourceSettingsCredentialsDiagnosticsRequest(
                settings = SqlConsoleSourceSettingsUpdateRequest(
                    defaultCredentialsFile = credentialsFile.toString(),
                    sources = listOf(
                        SqlConsoleEditableSourceRequest(
                            originalName = "db1",
                            name = "db1",
                            jdbcUrl = "jdbc:postgresql://localhost/db",
                            username = "\${db1.user}",
                            passwordReference = "\${db1.password}",
                        ),
                        SqlConsoleEditableSourceRequest(
                            name = "db2",
                            jdbcUrl = "jdbc:postgresql://localhost/db2",
                            username = "\${db2.user}",
                            passwordReference = "\${db2.password}",
                        ),
                    ),
                ),
            ),
            currentUiConfig = current,
        )

        assertTrue(diagnostics.fileAvailable)
        assertEquals(listOf("db1.password", "db1.user", "db2.password", "db2.user"), diagnostics.requiredKeys)
        assertEquals(listOf("db2.password", "db2.user"), diagnostics.missingKeys)
        assertTrue("secret" !in diagnostics.toString())
    }

    private fun sourceSettingsService(current: UiAppConfig): UiSqlConsoleSourceSettingsService =
        sourceSettingsService(current, FakeUiSecretProvider())

    private fun sourceSettingsService(
        current: UiAppConfig,
        secretProvider: FakeUiSecretProvider,
    ): UiSqlConsoleSourceSettingsService =
        UiSqlConsoleSourceSettingsService(
            uiConfigPersistenceService = CapturingSqlSourcePersistence(current),
            runtimeConfigResolver = UiRuntimeConfigResolver(secretProvider = secretProvider),
            sqlConsoleService = SqlConsoleService(current.sqlConsole),
            secretProvider = secretProvider,
        )
}

private class FakeUiSecretProvider : UiSecretProvider {
    val savedSecrets: MutableMap<String, String> = mutableMapOf()

    override fun providerInfo(): UiSecretProviderInfo =
        UiSecretProviderInfo(
            providerId = "fake",
            displayName = "Fake secret storage",
            available = true,
        )

    override fun readSecret(key: String): String? = savedSecrets[key]

    override fun saveSecret(
        key: String,
        value: String,
    ) {
        savedSecrets[key] = value
    }
}

private class CapturingSqlSourcePersistence(
    private val current: UiAppConfig,
) : UiConfigPersistenceService() {
    var updatedSourceCatalog: List<SqlConsoleSourceConfig> = emptyList()
        private set

    override fun resolveEditableConfigPath(): Path = Path.of("/tmp/ui-application.yml")

    override fun updateSqlConsoleSourceCatalog(
        sourceCatalog: List<SqlConsoleSourceConfig>,
        groups: List<SqlConsoleSourceGroupConfig>,
        defaultCredentialsFile: String?,
    ): UiAppConfig {
        updatedSourceCatalog = sourceCatalog
        return current.copy(
            defaultCredentialsFile = defaultCredentialsFile,
            sqlConsole = current.sqlConsole.copy(
                sourceCatalog = sourceCatalog,
                groups = groups,
            ),
        )
    }
}
