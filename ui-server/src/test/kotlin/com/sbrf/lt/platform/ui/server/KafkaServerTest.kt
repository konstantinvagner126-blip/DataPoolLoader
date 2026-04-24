package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.kafka.KafkaClusterCatalogEntry
import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaMetadataOperations
import com.sbrf.lt.datapool.kafka.KafkaToolInfo
import com.sbrf.lt.datapool.kafka.KafkaTopicOverview
import com.sbrf.lt.datapool.kafka.KafkaTopicPartitionSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicsCatalog
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.platform.ui.config.UiAppConfig
import io.ktor.client.request.get
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
            )
        }

        val noRedirectClient = createClient { followRedirects = false }

        val composeKafkaRedirect = noRedirectClient.get("/compose-kafka?clusterId=local&topic=datapool-test")
        assertEquals(HttpStatusCode.Found, composeKafkaRedirect.status)
        assertEquals(
            "/static/compose-app/index.html?screen=kafka&clusterId=local&topic=datapool-test",
            composeKafkaRedirect.headers[HttpHeaders.Location],
        )

        val kafkaRedirect = noRedirectClient.get("/kafka?clusterId=local")
        assertEquals(HttpStatusCode.Found, kafkaRedirect.status)
        assertEquals(
            "/static/compose-app/index.html?screen=kafka&clusterId=local",
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
        )
    }
}
