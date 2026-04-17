package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class UiRuntimeConfigResolverTest {

    @Test
    fun `resolves postgres and sql console placeholders from credentials file`() {
        val configDir = Files.createTempDirectory("ui-runtime-config-")
        val credentialsFile = configDir.resolve("credential.properties").apply {
            writeText(
                """
                LOCAL_MANUAL_DB_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/postgres
                LOCAL_MANUAL_DB_USERNAME=kwdev
                LOCAL_MANUAL_DB_PASSWORD=dummy
                """.trimIndent(),
            )
        }
        val rawConfig = UiAppConfig(
            defaultCredentialsFile = credentialsFile.fileName.toString(),
            configBaseDir = configDir.toString(),
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "\${LOCAL_MANUAL_DB_JDBC_URL}",
                    username = "\${LOCAL_MANUAL_DB_USERNAME}",
                    password = "\${LOCAL_MANUAL_DB_PASSWORD}",
                ),
            ),
            sqlConsole = SqlConsoleConfig(
                sources = listOf(
                    SqlConsoleSourceConfig(
                        name = "db1",
                        jdbcUrl = "\${LOCAL_MANUAL_DB_JDBC_URL}",
                        username = "\${LOCAL_MANUAL_DB_USERNAME}",
                        password = "\${LOCAL_MANUAL_DB_PASSWORD}",
                    ),
                ),
            ),
        )

        val resolved = UiRuntimeConfigResolver().resolve(rawConfig)

        assertEquals("jdbc:postgresql://127.0.0.1:5432/postgres", resolved.moduleStore.postgres.jdbcUrl)
        assertEquals("kwdev", resolved.moduleStore.postgres.username)
        assertEquals("dummy", resolved.moduleStore.postgres.password)
        assertEquals("jdbc:postgresql://127.0.0.1:5432/postgres", resolved.sqlConsole.sources.single().jdbcUrl)
        assertEquals("kwdev", resolved.sqlConsole.sources.single().username)
        assertEquals("dummy", resolved.sqlConsole.sources.single().password)
    }
}
