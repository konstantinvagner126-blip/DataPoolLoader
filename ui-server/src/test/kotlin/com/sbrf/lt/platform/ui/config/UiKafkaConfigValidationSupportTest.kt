package com.sbrf.lt.platform.ui.config

import kotlin.test.Test
import kotlin.test.assertFailsWith

class UiKafkaConfigValidationSupportTest {

    private val support = UiKafkaConfigValidationSupport()

    @Test
    fun `accepts plaintext cluster with bootstrap servers`() {
        support.validateForLoad(
            UiKafkaConfig(
                clusters = listOf(
                    UiKafkaClusterConfig(
                        id = "local",
                        name = "Local Kafka",
                        properties = mapOf(
                            "bootstrap.servers" to "localhost:19092",
                            "security.protocol" to "PLAINTEXT",
                        ),
                    ),
                ),
            ),
        )

        support.validateForRuntime(
            UiKafkaConfig(
                clusters = listOf(
                    UiKafkaClusterConfig(
                        id = "local",
                        name = "Local Kafka",
                        properties = mapOf(
                            "bootstrap.servers" to "localhost:19092",
                            "security.protocol" to "PLAINTEXT",
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `rejects duplicate cluster ids`() {
        val error = assertFailsWith<IllegalArgumentException> {
            support.validateForLoad(
                UiKafkaConfig(
                    clusters = listOf(
                        UiKafkaClusterConfig(
                            id = "local",
                            name = "Local Kafka",
                            properties = mapOf("bootstrap.servers" to "localhost:19092"),
                        ),
                        UiKafkaClusterConfig(
                            id = "local",
                            name = "Duplicate",
                            properties = mapOf("bootstrap.servers" to "localhost:29092"),
                        ),
                    ),
                ),
            )
        }

        kotlin.test.assertTrue(error.message!!.contains("дублирующиеся id"))
    }

    @Test
    fun `rejects plaintext cluster with ssl properties`() {
        val error = assertFailsWith<IllegalArgumentException> {
            support.validateForRuntime(
                UiKafkaConfig(
                    clusters = listOf(
                        UiKafkaClusterConfig(
                            id = "plain",
                            name = "Plain",
                            properties = mapOf(
                                "bootstrap.servers" to "localhost:19092",
                                "security.protocol" to "PLAINTEXT",
                                "ssl.truststore.location" to "/tmp/truststore.jks",
                            ),
                        ),
                    ),
                ),
            )
        }

        kotlin.test.assertTrue(error.message!!.contains("PLAINTEXT"))
    }

    @Test
    fun `rejects pem identity without private key`() {
        val error = assertFailsWith<IllegalArgumentException> {
            support.validateForRuntime(
                UiKafkaConfig(
                    clusters = listOf(
                        UiKafkaClusterConfig(
                            id = "pem",
                            name = "PEM",
                            properties = mapOf(
                                "bootstrap.servers" to "localhost:19092",
                                "security.protocol" to "SSL",
                                "ssl.keystore.type" to "PEM",
                                "ssl.keystore.certificate.chain" to "CERT",
                            ),
                        ),
                    ),
                ),
            )
        }

        kotlin.test.assertTrue(error.message!!.contains("ssl.keystore.key"))
    }

    @Test
    fun `accepts mtls pem cluster with file-backed placeholders after resolution`() {
        support.validateForRuntime(
            UiKafkaConfig(
                clusters = listOf(
                    UiKafkaClusterConfig(
                        id = "pem",
                        name = "PEM",
                        properties = mapOf(
                            "bootstrap.servers" to "localhost:19092",
                            "security.protocol" to "SSL",
                            "ssl.truststore.type" to "PEM",
                            "ssl.truststore.certificates" to "CA",
                            "ssl.keystore.type" to "PEM",
                            "ssl.keystore.certificate.chain" to "CERT",
                            "ssl.keystore.key" to "KEY",
                        ),
                    ),
                ),
            ),
        )
    }
}
