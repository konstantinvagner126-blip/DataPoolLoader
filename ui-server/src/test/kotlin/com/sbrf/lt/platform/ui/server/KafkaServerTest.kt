package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.kafka.KafkaClusterCatalogEntry
import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaMetadataOperations
import com.sbrf.lt.datapool.kafka.KafkaMessageOperations
import com.sbrf.lt.datapool.kafka.KafkaProduceOperations
import com.sbrf.lt.datapool.kafka.KafkaRenderedBytes
import com.sbrf.lt.datapool.kafka.KafkaToolInfo
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageHeader
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadRequest
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadScope
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadResult
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageRecord
import com.sbrf.lt.datapool.kafka.KafkaTopicProduceRequest
import com.sbrf.lt.datapool.kafka.KafkaTopicProduceResult
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupLagStatus
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupPartitionLag
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupsStatus
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupsSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicOverview
import com.sbrf.lt.datapool.kafka.KafkaTopicPartitionSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicsCatalog
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.kafka.UiKafkaSettingsOperations
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KafkaServerTest {

    @Test
    fun `serves kafka page redirects and metadata routes`() = testApplication {
        val projectRoot = createProject()
        val uiConfig = UiAppConfig(
            appsRoot = projectRoot.resolve("apps").toString(),
            storageDir = Files.createTempDirectory("ui-kafka-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                kafkaMetadataService = FakeKafkaMetadataOperations(),
                kafkaMessageService = FakeKafkaMessageOperations(),
                kafkaProduceService = FakeKafkaProduceOperations(),
                kafkaSettingsService = FakeKafkaSettingsOperations(),
            )
        }

        val noRedirectClient = createClient { followRedirects = false }

        val composeKafkaRedirect =
            noRedirectClient.get("/compose-kafka?clusterId=local&topic=datapool-test&query=data&pane=messages&scope=ALL_PARTITIONS&mode=OFFSET&partition=1")
        assertEquals(HttpStatusCode.Found, composeKafkaRedirect.status)
        assertEquals(
            "/static/compose-app/index.html?screen=kafka&clusterId=local&topic=datapool-test&query=data&pane=messages&scope=ALL_PARTITIONS&mode=OFFSET&partition=1",
            composeKafkaRedirect.headers[HttpHeaders.Location],
        )

        val kafkaRedirect = noRedirectClient.get("/kafka?clusterId=local&pane=settings")
        assertEquals(HttpStatusCode.Found, kafkaRedirect.status)
        assertEquals(
            "/static/compose-app/index.html?screen=kafka&clusterId=local&pane=settings",
            kafkaRedirect.headers[HttpHeaders.Location],
        )

        val info = client.get("/api/kafka/info").bodyAsText()
        assertTrue(info.contains("\"configured\":true"))
        assertTrue(info.contains("\"clusters\":[{\"id\":\"local\""))

        val topics = client.get("/api/kafka/topics?clusterId=local&query=data").bodyAsText()
        assertTrue(topics.contains("\"clusterId\":\"local\""))
        assertTrue(topics.contains("\"name\":\"datapool-test\""))

        val overview = client.get("/api/kafka/topic-overview?clusterId=local&topic=datapool-test").bodyAsText()
        assertTrue(overview.contains("\"topic\":{\"name\":\"datapool-test\""))
        assertTrue(overview.contains("\"latestOffset\":27"))
        assertTrue(overview.contains("\"consumerGroups\":{\"status\":\"AVAILABLE\""))
        assertTrue(overview.contains("\"groupId\":\"datapool-test-group\""))

        val readMessages = client.post("/api/kafka/messages/read") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """
                {"clusterId":"local","topicName":"datapool-test","scope":"SELECTED_PARTITION","partition":0,"mode":"LATEST","limit":2}
                """.trimIndent(),
            )
        }.bodyAsText()
        assertTrue(readMessages.contains("\"offset\":12"))
        assertTrue(readMessages.contains("\"partition\":0"))
        assertTrue(readMessages.contains("\"jsonPrettyText\":\"{\\n  \\\"id\\\" : 12\\n}\""))

        val readAllPartitions = client.post("/api/kafka/messages/read") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """
                {"clusterId":"local","topicName":"datapool-test","scope":"ALL_PARTITIONS","mode":"LATEST","limit":2}
                """.trimIndent(),
            )
        }.bodyAsText()
        assertTrue(readAllPartitions.contains("\"scope\":\"ALL_PARTITIONS\""))
        assertTrue(readAllPartitions.contains("\"partition\":1"))

        val produce = client.post("/api/kafka/messages/produce") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """
                {"clusterId":"local","topicName":"datapool-test","partition":1,"keyText":"order-key","payloadText":"{\"id\":42}","headers":[{"name":"source","valueText":"test"}]}
                """.trimIndent(),
            )
        }.bodyAsText()
        assertTrue(produce.contains("\"topicName\":\"datapool-test\""))
        assertTrue(produce.contains("\"partition\":1"))
        assertTrue(produce.contains("\"offset\":42"))

        val settings = client.get("/api/kafka/settings").bodyAsText()
        assertTrue(settings.contains("\"editableConfigPath\":\"/tmp/ui-application.yml\""))
        assertTrue(settings.contains("\"clusters\":[{\"id\":\"local\""))

        val connectionTest = client.post("/api/kafka/settings/test-connection") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """
                {"cluster":{"id":"local","name":"Local Kafka","readOnly":false,"bootstrapServers":"localhost:19092","securityProtocol":"PLAINTEXT"}}
                """.trimIndent(),
            )
        }.bodyAsText()
        assertTrue(connectionTest.contains("\"success\":true"))
        assertTrue(connectionTest.contains("\"nodeCount\":1"))

        val missingCluster = client.get("/api/kafka/topics?clusterId=missing")
        assertEquals(HttpStatusCode.NotFound, missingCluster.status)
        assertTrue(missingCluster.bodyAsText().contains("Kafka cluster 'missing'"))
    }
}

private class FakeKafkaMetadataOperations : KafkaMetadataOperations {
    private val cluster = KafkaClusterCatalogEntry(
        id = "local",
        name = "Local Kafka",
        readOnly = false,
        bootstrapServers = "localhost:19092",
        securityProtocol = "PLAINTEXT",
    )
    private val topic = KafkaTopicSummary(
        name = "datapool-test",
        internal = false,
        partitionCount = 2,
        replicationFactor = 1,
        cleanupPolicy = "delete",
        retentionMs = 60000,
    )

    override fun info(): KafkaToolInfo =
        KafkaToolInfo(
            configured = true,
            maxRecordsPerRead = 100,
            maxPayloadBytes = 1_048_576,
            clusters = listOf(cluster),
        )

    override fun listTopics(
        clusterId: String,
        query: String,
    ): KafkaTopicsCatalog {
        if (clusterId != "local") {
            throw KafkaClusterNotFoundException(clusterId)
        }
        return KafkaTopicsCatalog(
            clusterId = clusterId,
            query = query,
            topics = if (query.isBlank() || topic.name.contains(query, ignoreCase = true)) listOf(topic) else emptyList(),
        )
    }

    override fun loadTopicOverview(
        clusterId: String,
        topicName: String,
    ): KafkaTopicOverview {
        if (clusterId != "local") {
            throw KafkaClusterNotFoundException(clusterId)
        }
        return KafkaTopicOverview(
            cluster = cluster,
            topic = topic,
            partitions = listOf(
                KafkaTopicPartitionSummary(
                    partition = 0,
                    leaderId = 1,
                    replicaCount = 1,
                    inSyncReplicaCount = 1,
                    earliestOffset = 0,
                    latestOffset = 12,
                ),
                KafkaTopicPartitionSummary(
                    partition = 1,
                    leaderId = 1,
                    replicaCount = 1,
                    inSyncReplicaCount = 1,
                    earliestOffset = 4,
                    latestOffset = 27,
                ),
            ),
            consumerGroups = KafkaTopicConsumerGroupsSummary(
                status = KafkaTopicConsumerGroupsStatus.AVAILABLE,
                groups = listOf(
                    KafkaTopicConsumerGroupSummary(
                        groupId = "datapool-test-group",
                        state = "STABLE",
                        memberCount = 1,
                        totalLag = 5,
                        lagStatus = KafkaTopicConsumerGroupLagStatus.OK,
                        partitions = listOf(
                            KafkaTopicConsumerGroupPartitionLag(
                                partition = 0,
                                committedOffset = 7,
                                latestOffset = 12,
                                lag = 5,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}

private class FakeKafkaMessageOperations : KafkaMessageOperations {
    override fun readMessages(request: KafkaTopicMessageReadRequest): KafkaTopicMessageReadResult {
        if (request.clusterId != "local") {
            throw KafkaClusterNotFoundException(request.clusterId)
        }
        return KafkaTopicMessageReadResult(
            cluster = KafkaClusterCatalogEntry(
                id = "local",
                name = "Local Kafka",
                readOnly = false,
                bootstrapServers = "localhost:19092",
                securityProtocol = "PLAINTEXT",
            ),
            topicName = request.topicName,
            scope = request.scope,
            partition = request.partition,
            mode = request.mode,
            requestedLimit = request.limit ?: 2,
            effectiveLimit = request.limit ?: 2,
            effectiveStartOffset = if (request.scope == KafkaTopicMessageReadScope.ALL_PARTITIONS) null else 12,
            records = if (request.scope == KafkaTopicMessageReadScope.ALL_PARTITIONS) {
                listOf(
                    KafkaTopicMessageRecord(
                        partition = 1,
                        offset = 17,
                        timestamp = 1_700_000_000_100L,
                        value = KafkaRenderedBytes(
                            sizeBytes = 10,
                            truncated = false,
                            text = """{"id":17}""",
                        ),
                    ),
                    KafkaTopicMessageRecord(
                        partition = 0,
                        offset = 12,
                        timestamp = 1_700_000_000_012L,
                        value = KafkaRenderedBytes(
                            sizeBytes = 9,
                            truncated = false,
                            text = """{"id":12}""",
                            jsonPrettyText = "{\n  \"id\" : 12\n}",
                        ),
                    ),
                )
            } else listOf(
                KafkaTopicMessageRecord(
                    partition = request.partition ?: 0,
                    offset = 12,
                    timestamp = 1_700_000_000_000L,
                    key = KafkaRenderedBytes(
                        sizeBytes = 5,
                        truncated = false,
                        text = "key-12",
                    ),
                    value = KafkaRenderedBytes(
                        sizeBytes = 9,
                        truncated = false,
                        text = """{"id":12}""",
                        jsonPrettyText = "{\n  \"id\" : 12\n}",
                    ),
                    headers = listOf(
                        KafkaTopicMessageHeader(
                            name = "source",
                            value = KafkaRenderedBytes(
                                sizeBytes = 4,
                                truncated = false,
                                text = "test",
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}

private class FakeKafkaProduceOperations : KafkaProduceOperations {
    override fun produce(request: KafkaTopicProduceRequest): KafkaTopicProduceResult =
        KafkaTopicProduceResult(
            cluster = KafkaClusterCatalogEntry(
                id = request.clusterId,
                name = "Local Kafka",
                readOnly = false,
                bootstrapServers = "localhost:19092",
                securityProtocol = "PLAINTEXT",
            ),
            topicName = request.topicName,
            partition = request.partition ?: 0,
            offset = 42L,
            timestamp = 1_700_000_000_000L,
        )
}

private class FakeKafkaSettingsOperations : UiKafkaSettingsOperations {
    override fun loadSettings(uiConfig: UiAppConfig) =
        com.sbrf.lt.platform.ui.model.KafkaSettingsResponse(
            editableConfigPath = "/tmp/ui-application.yml",
            clusters = listOf(
                com.sbrf.lt.platform.ui.model.KafkaEditableClusterResponse(
                    id = "local",
                    name = "Local Kafka",
                    readOnly = false,
                    bootstrapServers = "localhost:19092",
                    securityProtocol = "PLAINTEXT",
                ),
            ),
        )

    override fun saveSettings(
        request: com.sbrf.lt.platform.ui.model.KafkaSettingsUpdateRequestPayload,
        currentUiConfig: UiAppConfig,
    ) = com.sbrf.lt.platform.ui.model.KafkaSettingsResponse(
        editableConfigPath = "/tmp/ui-application.yml",
        clusters = request.clusters.map {
            com.sbrf.lt.platform.ui.model.KafkaEditableClusterResponse(
                id = it.id,
                name = it.name,
                readOnly = it.readOnly,
                bootstrapServers = it.bootstrapServers,
                clientId = it.clientId,
                securityProtocol = it.securityProtocol,
                truststoreType = it.truststoreType,
                truststoreLocation = it.truststoreLocation,
                truststoreCertificates = it.truststoreCertificates,
                keystoreType = it.keystoreType,
                keystoreLocation = it.keystoreLocation,
                keystoreCertificateChain = it.keystoreCertificateChain,
                keystoreKey = it.keystoreKey,
                keyPassword = it.keyPassword,
                additionalProperties = it.additionalProperties,
            )
        },
    )

    override fun testConnection(
        request: com.sbrf.lt.platform.ui.model.KafkaSettingsConnectionTestRequestPayload,
        currentUiConfig: UiAppConfig,
    ) = com.sbrf.lt.platform.ui.model.KafkaSettingsConnectionTestResponse(
        success = true,
        message = "Подключение успешно. Доступно broker nodes: 1.",
        nodeCount = 1,
    )
}
