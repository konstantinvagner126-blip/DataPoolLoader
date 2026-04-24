package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.model.KafkaEditableClusterRequestPayload
import com.sbrf.lt.platform.ui.model.KafkaSettingsConnectionTestRequestPayload
import com.sbrf.lt.platform.ui.model.KafkaSettingsUpdateRequestPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiKafkaSettingsServiceTest {

    @Test
    fun `loads editable Kafka settings from current config`() {
        val service = UiKafkaSettingsService(
            uiConfigPersistenceService = object : UiConfigPersistenceService() {
                override fun resolveEditableConfigPath() = java.nio.file.Path.of("/tmp/ui-application.yml")
            },
            runtimeConfigResolver = UiRuntimeConfigResolver(),
        )

        val response = service.loadSettings(
            uiConfig(
                clusters = listOf(
                    UiKafkaClusterConfig(
                        id = "local",
                        name = "Local Kafka",
                        readOnly = false,
                        properties = mapOf(
                            "bootstrap.servers" to "localhost:19092",
                            "client.id" to "datapool-loader",
                            "security.protocol" to "PLAINTEXT",
                            "request.timeout.ms" to "15000",
                        ),
                    ),
                ),
            ),
        )

        assertEquals("/tmp/ui-application.yml", response.editableConfigPath)
        assertEquals(1, response.clusters.size)
        assertEquals("local", response.clusters.single().id)
        assertEquals("localhost:19092", response.clusters.single().bootstrapServers)
        assertEquals("15000", response.clusters.single().additionalProperties["request.timeout.ms"])
    }

    @Test
    fun `saves supported Kafka cluster fields through config persistence service`() {
        val materialDir = java.nio.file.Files.createTempDirectory("kafka-settings-material")
        val caCrt = materialDir.resolve("ca.crt").also { java.nio.file.Files.writeString(it, "ca") }
        val clientCrt = materialDir.resolve("client.crt").also { java.nio.file.Files.writeString(it, "client-cert") }
        val clientKey = materialDir.resolve("client.key").also { java.nio.file.Files.writeString(it, "client-key") }
        var savedClusters: List<UiKafkaClusterConfig> = emptyList()
        val service = UiKafkaSettingsService(
            uiConfigPersistenceService = object : UiConfigPersistenceService() {
                override fun updateKafkaClusterCatalog(clusters: List<UiKafkaClusterConfig>): UiAppConfig {
                    savedClusters = clusters
                    return uiConfig(clusters = clusters)
                }
            },
            runtimeConfigResolver = UiRuntimeConfigResolver(),
        )

        val response = service.saveSettings(
            request = KafkaSettingsUpdateRequestPayload(
                clusters = listOf(
                    KafkaEditableClusterRequestPayload(
                        id = "dev",
                        name = "DEV Kafka",
                        readOnly = true,
                        bootstrapServers = "host1:9092,host2:9092",
                        clientId = "datapool-loader",
                        securityProtocol = "SSL",
                        truststoreType = "PEM",
                        truststoreCertificates = "\${file:${caCrt.toAbsolutePath()}}",
                        keystoreType = "PEM",
                        keystoreCertificateChain = "\${file:${clientCrt.toAbsolutePath()}}",
                        keystoreKey = "\${file:${clientKey.toAbsolutePath()}}",
                        keyPassword = "\${KAFKA_KEY_PASSWORD}",
                        additionalProperties = mapOf("client.dns.lookup" to "use_all_dns_ips"),
                    ),
                ),
            ),
            currentUiConfig = uiConfig(),
        )

        assertEquals(1, savedClusters.size)
        assertEquals("dev", savedClusters.single().id)
        assertEquals("host1:9092,host2:9092", savedClusters.single().properties["bootstrap.servers"])
        assertEquals("use_all_dns_ips", savedClusters.single().properties["client.dns.lookup"])
        assertEquals("SSL", response.clusters.single().securityProtocol)
    }

    @Test
    fun `connection test resolves runtime placeholders before opening admin facade`() {
        var testedBootstrapServers: String? = null
        val service = UiKafkaSettingsService(
            uiConfigPersistenceService = UiConfigPersistenceService(),
            runtimeConfigResolver = object : UiRuntimeConfigResolver() {
                override fun resolve(uiConfig: UiAppConfig): UiAppConfig =
                    uiConfig.copy(
                        kafka = uiConfig.kafka.copy(
                            clusters = uiConfig.kafka.clusters.map { cluster ->
                                cluster.copy(
                                    properties = cluster.properties.mapValues { (_, value) ->
                                        if (value == "\${KAFKA_BOOTSTRAP}") "localhost:19092" else value
                                    },
                                )
                            },
                        ),
                    )
            },
            adminFacadeFactory = object : UiKafkaAdminFacadeFactory {
                override fun open(cluster: UiKafkaClusterConfig): UiKafkaAdminFacade =
                    object : UiKafkaAdminFacade {
                        override fun loadBrokerNodeCount(): Int {
                            testedBootstrapServers = cluster.properties["bootstrap.servers"]
                            return 1
                        }

                        override fun listTopics(): List<UiKafkaTopicListing> = emptyList()

                        override fun describeTopics(topicNames: List<String>): List<UiKafkaTopicDetails> = emptyList()

                        override fun describeTopicConfigs(topicNames: List<String>): Map<String, Map<String, String>> = emptyMap()

                        override fun listConsumerGroups(): List<UiKafkaConsumerGroupListing> = emptyList()

                        override fun describeConsumerGroups(groupIds: List<String>): Map<String, UiKafkaConsumerGroupDetails> = emptyMap()

                        override fun loadConsumerGroupOffsets(groupId: String): List<UiKafkaCommittedOffset> = emptyList()

                        override fun loadOffsets(topicName: String, partitions: List<Int>): Map<Int, UiKafkaPartitionOffsets> = emptyMap()

                        override fun close() = Unit
                    }
            },
        )

        val response = service.testConnection(
            request = KafkaSettingsConnectionTestRequestPayload(
                cluster = KafkaEditableClusterRequestPayload(
                    id = "local",
                    name = "Local Kafka",
                    readOnly = false,
                    bootstrapServers = "\${KAFKA_BOOTSTRAP}",
                    securityProtocol = "PLAINTEXT",
                ),
            ),
            currentUiConfig = uiConfig(),
        )

        assertTrue(response.success)
        assertEquals("localhost:19092", testedBootstrapServers)
        assertEquals(1, response.nodeCount)
    }

    @Test
    fun `connection test reports runtime failure as non fatal result`() {
        val service = UiKafkaSettingsService(
            uiConfigPersistenceService = UiConfigPersistenceService(),
            runtimeConfigResolver = UiRuntimeConfigResolver(),
            adminFacadeFactory = object : UiKafkaAdminFacadeFactory {
                override fun open(cluster: UiKafkaClusterConfig): UiKafkaAdminFacade {
                    throw org.apache.kafka.common.errors.TimeoutException("timed out")
                }
            },
        )

        val response = service.testConnection(
            request = KafkaSettingsConnectionTestRequestPayload(
                cluster = KafkaEditableClusterRequestPayload(
                    id = "local",
                    name = "Local Kafka",
                    readOnly = false,
                    bootstrapServers = "localhost:19092",
                    securityProtocol = "PLAINTEXT",
                ),
            ),
            currentUiConfig = uiConfig(),
        )

        assertFalse(response.success)
        assertTrue(response.message.contains("не ответил вовремя"))
    }
}

private fun uiConfig(clusters: List<UiKafkaClusterConfig> = emptyList()): UiAppConfig =
    UiAppConfig(
        kafka = UiKafkaConfig(clusters = clusters),
    )
