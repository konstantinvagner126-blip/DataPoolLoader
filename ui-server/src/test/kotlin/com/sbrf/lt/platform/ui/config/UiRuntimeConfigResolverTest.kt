package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class UiRuntimeConfigResolverTest {

    @Test
    fun `resolves postgres sql console and kafka placeholders from credentials file`() {
        val configDir = Files.createTempDirectory("ui-runtime-config-")
        val credentialsFile = configDir.resolve("credential.properties").apply {
            writeText(
                """
                LOCAL_MANUAL_DB_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/postgres
                LOCAL_MANUAL_DB_USERNAME=kwdev
                LOCAL_MANUAL_DB_PASSWORD=dummy
                KAFKA_KEY_PASSWORD=key-secret
                """.trimIndent(),
            )
        }
        val caCertFile = configDir.resolve("ca.crt").apply {
            writeText(
                """
                -----BEGIN CERTIFICATE-----
                CA-CONTENT
                -----END CERTIFICATE-----
                """.trimIndent(),
            )
        }
        val clientCertFile = configDir.resolve("client.crt").apply {
            writeText(
                """
                -----BEGIN CERTIFICATE-----
                CLIENT-CONTENT
                -----END CERTIFICATE-----
                """.trimIndent(),
            )
        }
        val clientKeyFile = configDir.resolve("client.key").apply {
            writeText(
                """
                -----BEGIN PRIVATE KEY-----
                KEY-CONTENT
                -----END PRIVATE KEY-----
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
                sourceCatalog = listOf(
                    SqlConsoleSourceConfig(
                        name = "db1",
                        jdbcUrl = "\${LOCAL_MANUAL_DB_JDBC_URL}",
                        username = "\${LOCAL_MANUAL_DB_USERNAME}",
                        password = "\${LOCAL_MANUAL_DB_PASSWORD}",
                    ),
                ),
            ),
            kafka = UiKafkaConfig(
                clusters = listOf(
                    UiKafkaClusterConfig(
                        id = "local",
                        name = "Local Kafka",
                        readOnly = false,
                        properties = linkedMapOf(
                            "bootstrap.servers" to "localhost:19092",
                            "client.id" to "datapool-loader",
                            "security.protocol" to "SSL",
                            "ssl.truststore.type" to "PEM",
                            "ssl.truststore.certificates" to "\${file:${caCertFile.fileName}}",
                            "ssl.keystore.type" to "PEM",
                            "ssl.keystore.certificate.chain" to "\${file:${clientCertFile.fileName}}",
                            "ssl.keystore.key" to "\${file:${clientKeyFile.fileName}}",
                            "ssl.key.password" to "\${KAFKA_KEY_PASSWORD}",
                        ),
                    ),
                ),
            ),
        )

        val resolved = UiRuntimeConfigResolver().resolve(rawConfig)

        assertEquals("jdbc:postgresql://127.0.0.1:5432/postgres", resolved.moduleStore.postgres.jdbcUrl)
        assertEquals("kwdev", resolved.moduleStore.postgres.username)
        assertEquals("dummy", resolved.moduleStore.postgres.password)
        assertEquals("jdbc:postgresql://127.0.0.1:5432/postgres", resolved.sqlConsole.sourceCatalog.single().jdbcUrl)
        assertEquals("kwdev", resolved.sqlConsole.sourceCatalog.single().username)
        assertEquals("dummy", resolved.sqlConsole.sourceCatalog.single().password)
        assertEquals(
            caCertFile.toFile().readText(),
            resolved.kafka.clusters.single().properties["ssl.truststore.certificates"],
        )
        assertEquals(
            clientCertFile.toFile().readText(),
            resolved.kafka.clusters.single().properties["ssl.keystore.certificate.chain"],
        )
        assertEquals(
            clientKeyFile.toFile().readText(),
            resolved.kafka.clusters.single().properties["ssl.keystore.key"],
        )
        assertEquals("key-secret", resolved.kafka.clusters.single().properties["ssl.key.password"])
    }
}
