package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaTopicProduceHeader
import com.sbrf.lt.datapool.kafka.KafkaTopicProduceRequest
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.Header
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiKafkaProduceServiceTest {

    @Test
    fun `produces single message with key headers and partition override`() {
        val sentRecords = mutableListOf<FakeProducedRecord>()
        val service = ConfigBackedKafkaProduceService(
            kafkaConfig = kafkaConfig(),
            producerFacadeFactory = FakeUiKafkaProducerFacadeFactory(
                sentRecords = sentRecords,
                metadata = RecordMetadata(
                    TopicPartition("datapool-test", 1),
                    0L,
                    42L,
                    1_700_000_000_000L,
                    0,
                    3,
                    12,
                ),
            ),
        )

        val result = service.produce(
            KafkaTopicProduceRequest(
                clusterId = "local",
                topicName = "datapool-test",
                partition = 1,
                keyText = "order-key",
                payloadText = """{"id":42}""",
                headers = listOf(
                    KafkaTopicProduceHeader("source", "test"),
                    KafkaTopicProduceHeader("traceId", "abc-42"),
                ),
            ),
        )

        assertEquals("local", result.cluster.id)
        assertEquals("datapool-test", result.topicName)
        assertEquals(1, result.partition)
        assertEquals(42L, result.offset)
        assertEquals(1_700_000_000_000L, result.timestamp)
        assertEquals(1, sentRecords.size)
        val sent = sentRecords.single()
        assertEquals("datapool-test", sent.topicName)
        assertEquals(1, sent.partition)
        assertEquals("order-key", sent.keyText)
        assertEquals("""{"id":42}""", sent.valueText)
        assertEquals(listOf("source" to "test", "traceId" to "abc-42"), sent.headers)
    }

    @Test
    fun `rejects oversized payload`() {
        val service = ConfigBackedKafkaProduceService(
            kafkaConfig = kafkaConfig(maxPayloadBytes = 4),
            producerFacadeFactory = FakeUiKafkaProducerFacadeFactory(),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            service.produce(
                KafkaTopicProduceRequest(
                    clusterId = "local",
                    topicName = "datapool-test",
                    payloadText = "payload-too-large",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("превышает лимит"))
    }

    @Test
    fun `fails for unknown cluster`() {
        val service = ConfigBackedKafkaProduceService(
            kafkaConfig = kafkaConfig(),
            producerFacadeFactory = FakeUiKafkaProducerFacadeFactory(),
        )

        assertFailsWith<KafkaClusterNotFoundException> {
            service.produce(
                KafkaTopicProduceRequest(
                    clusterId = "missing",
                    topicName = "datapool-test",
                    payloadText = "test",
                ),
            )
        }
    }
}

private fun kafkaConfig(maxPayloadBytes: Int = 1_048_576): UiKafkaConfig =
    UiKafkaConfig(
        maxPayloadBytes = maxPayloadBytes,
        clusters = listOf(
            UiKafkaClusterConfig(
                id = "local",
                name = "Local Kafka",
                readOnly = false,
                properties = mapOf(
                    "bootstrap.servers" to "localhost:19092",
                    "security.protocol" to "PLAINTEXT",
                ),
            ),
        ),
    )

private data class FakeProducedRecord(
    val topicName: String,
    val partition: Int?,
    val keyText: String?,
    val valueText: String,
    val headers: List<Pair<String, String?>> = emptyList(),
)

private class FakeUiKafkaProducerFacadeFactory(
    private val sentRecords: MutableList<FakeProducedRecord> = mutableListOf(),
    private val metadata: RecordMetadata = RecordMetadata(
        TopicPartition("datapool-test", 0),
        0L,
        0L,
        1_700_000_000_000L,
        0,
        0,
        0,
    ),
) : UiKafkaProducerFacadeFactory {
    override fun open(cluster: UiKafkaClusterConfig): UiKafkaProducerFacade =
        object : UiKafkaProducerFacade {
            override fun send(
                topicName: String,
                partition: Int?,
                key: ByteArray?,
                value: ByteArray,
                headers: List<Header>,
            ): RecordMetadata {
                sentRecords += FakeProducedRecord(
                    topicName = topicName,
                    partition = partition,
                    keyText = key?.decodeToString(),
                    valueText = value.decodeToString(),
                    headers = headers.map { it.key() to it.value()?.decodeToString() },
                )
                return metadata
            }

            override fun close() = Unit
        }
}
