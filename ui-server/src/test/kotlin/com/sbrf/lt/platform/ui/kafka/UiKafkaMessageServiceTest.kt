package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadMode
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadRequest
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadScope
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiKafkaMessageServiceTest {

    @Test
    fun `reads latest records with bounded start offset and pretty json`() {
        val service = ConfigBackedKafkaMessageService(
            kafkaConfig = testKafkaConfig(),
            consumerFacadeFactory = FakeUiKafkaMessageConsumerFacadeFactory(
                partitions = mapOf("datapool-test" to listOf(0)),
                beginningOffsets = mapOf(tp("datapool-test", 0) to 0L),
                endOffsets = mapOf(tp("datapool-test", 0) to 12L),
                records = mapOf(
                    KafkaReadKey("datapool-test", 0, 9L) to listOf(
                        record(offset = 9L, value = """{"id":9}"""),
                        record(offset = 10L, value = """{"id":10}"""),
                        record(offset = 11L, value = """{"id":11}"""),
                    ),
                ),
            ),
        )

        val result = service.readMessages(
            KafkaTopicMessageReadRequest(
                clusterId = "local",
                topicName = "datapool-test",
                partition = 0,
                mode = KafkaTopicMessageReadMode.LATEST,
                limit = 3,
            ),
        )

        assertEquals(9L, result.effectiveStartOffset)
        assertEquals(3, result.records.size)
        assertEquals(9L, result.records.first().offset)
        assertNotNull(result.records.first().value?.jsonPrettyText)
    }

    @Test
    fun `clamps explicit offset to earliest available`() {
        val service = ConfigBackedKafkaMessageService(
            kafkaConfig = testKafkaConfig(),
            consumerFacadeFactory = FakeUiKafkaMessageConsumerFacadeFactory(
                partitions = mapOf("datapool-test" to listOf(0)),
                beginningOffsets = mapOf(tp("datapool-test", 0) to 5L),
                endOffsets = mapOf(tp("datapool-test", 0) to 8L),
                records = mapOf(
                    KafkaReadKey("datapool-test", 0, 5L) to listOf(
                        record(offset = 5L, value = "five"),
                        record(offset = 6L, value = "six"),
                    ),
                ),
            ),
        )

        val result = service.readMessages(
            KafkaTopicMessageReadRequest(
                clusterId = "local",
                topicName = "datapool-test",
                partition = 0,
                mode = KafkaTopicMessageReadMode.OFFSET,
                limit = 2,
                offset = 2L,
            ),
        )

        assertEquals(5L, result.effectiveStartOffset)
        assertTrue(result.note.orEmpty().contains("earliest offset 5"))
        assertEquals(listOf(5L, 6L), result.records.map { it.offset })
    }

    @Test
    fun `uses timestamp lookup when requested`() {
        val service = ConfigBackedKafkaMessageService(
            kafkaConfig = testKafkaConfig(),
            consumerFacadeFactory = FakeUiKafkaMessageConsumerFacadeFactory(
                partitions = mapOf("datapool-test" to listOf(0)),
                beginningOffsets = mapOf(tp("datapool-test", 0) to 0L),
                endOffsets = mapOf(tp("datapool-test", 0) to 20L),
                timestampOffsets = mapOf(KafkaTimestampKey("datapool-test", 0, 1_700_000_000_000L) to 14L),
                records = mapOf(
                    KafkaReadKey("datapool-test", 0, 14L) to listOf(
                        record(offset = 14L, value = """{"at":"timestamp"}"""),
                    ),
                ),
            ),
        )

        val result = service.readMessages(
            KafkaTopicMessageReadRequest(
                clusterId = "local",
                topicName = "datapool-test",
                partition = 0,
                mode = KafkaTopicMessageReadMode.TIMESTAMP,
                limit = 1,
                timestampMs = 1_700_000_000_000L,
            ),
        )

        assertEquals(14L, result.effectiveStartOffset)
        assertEquals(14L, result.records.single().offset)
    }

    @Test
    fun `reads across all partitions with bounded merged result`() {
        val service = ConfigBackedKafkaMessageService(
            kafkaConfig = testKafkaConfig(),
            consumerFacadeFactory = FakeUiKafkaMessageConsumerFacadeFactory(
                partitions = mapOf("datapool-test" to listOf(0, 1)),
                beginningOffsets = mapOf(
                    tp("datapool-test", 0) to 0L,
                    tp("datapool-test", 1) to 0L,
                ),
                endOffsets = mapOf(
                    tp("datapool-test", 0) to 9L,
                    tp("datapool-test", 1) to 10L,
                ),
                records = mapOf(
                    KafkaReadKey("datapool-test", 0, 5L) to listOf(
                        record(partition = 0, offset = 5L, value = """{"id":5}""", timestamp = 1_700_000_000_005L),
                        record(partition = 0, offset = 6L, value = """{"id":6}""", timestamp = 1_700_000_000_006L),
                        record(partition = 0, offset = 7L, value = """{"id":7}""", timestamp = 1_700_000_000_007L),
                    ),
                    KafkaReadKey("datapool-test", 1, 6L) to listOf(
                        record(partition = 1, offset = 6L, value = """{"id":16}""", timestamp = 1_700_000_000_016L),
                        record(partition = 1, offset = 7L, value = """{"id":17}""", timestamp = 1_700_000_000_017L),
                        record(partition = 1, offset = 8L, value = """{"id":18}""", timestamp = 1_700_000_000_018L),
                    ),
                ),
            ),
        )

        val result = service.readMessages(
            KafkaTopicMessageReadRequest(
                clusterId = "local",
                topicName = "datapool-test",
                scope = KafkaTopicMessageReadScope.ALL_PARTITIONS,
                partition = null,
                mode = KafkaTopicMessageReadMode.LATEST,
                limit = 4,
            ),
        )

        assertEquals(KafkaTopicMessageReadScope.ALL_PARTITIONS, result.scope)
        assertEquals(null, result.partition)
        assertEquals(4, result.records.size)
        assertEquals(listOf(1, 1, 1, 0), result.records.map { it.partition })
        assertEquals(listOf(8L, 7L, 6L, 7L), result.records.map { it.offset })
        assertEquals("DONE", result.status)
        assertEquals(4, result.consumedMessages)
        assertTrue(result.consumedBytes > 0)
        assertEquals("Результат собран по всем partition и отсортирован по timestamp.", result.note)
    }

    @Test
    fun `all partitions read uses bulk consumer path`() {
        val facadeFactory = FakeUiKafkaMessageConsumerFacadeFactory(
            partitions = mapOf("datapool-test" to listOf(0, 1)),
            beginningOffsets = mapOf(
                tp("datapool-test", 0) to 0L,
                tp("datapool-test", 1) to 0L,
            ),
            endOffsets = mapOf(
                tp("datapool-test", 0) to 4L,
                tp("datapool-test", 1) to 4L,
            ),
            records = mapOf(
                KafkaReadKey("datapool-test", 0, 0L) to listOf(
                    record(partition = 0, offset = 0L, value = """{"id":0}"""),
                ),
                KafkaReadKey("datapool-test", 1, 0L) to listOf(
                    record(partition = 1, offset = 0L, value = """{"id":10}"""),
                ),
            ),
        )
        val service = ConfigBackedKafkaMessageService(
            kafkaConfig = testKafkaConfig(),
            consumerFacadeFactory = facadeFactory,
        )

        service.readMessages(
            KafkaTopicMessageReadRequest(
                clusterId = "local",
                topicName = "datapool-test",
                scope = KafkaTopicMessageReadScope.ALL_PARTITIONS,
                mode = KafkaTopicMessageReadMode.OFFSET,
                limit = 2,
                offset = 0L,
            ),
        )

        assertEquals(1, facadeFactory.bulkReadCallCount)
        assertEquals(0, facadeFactory.singleReadCallCount)
    }

    @Test
    fun `honors explicit UI read limit even when config default is lower`() {
        val service = ConfigBackedKafkaMessageService(
            kafkaConfig = testKafkaConfig(),
            consumerFacadeFactory = FakeUiKafkaMessageConsumerFacadeFactory(
                partitions = mapOf("datapool-test" to listOf(0)),
                beginningOffsets = mapOf(tp("datapool-test", 0) to 0L),
                endOffsets = mapOf(tp("datapool-test", 0) to 10_000L),
                records = mapOf(
                    KafkaReadKey("datapool-test", 0, 9_500L) to List(500) { index ->
                        record(
                            partition = 0,
                            offset = 9_500L + index,
                            value = """{"id":${9_500 + index}}""",
                            timestamp = 1_700_000_100_000L + index,
                        )
                    },
                ),
            ),
        )

        val result = service.readMessages(
            KafkaTopicMessageReadRequest(
                clusterId = "local",
                topicName = "datapool-test",
                partition = 0,
                mode = KafkaTopicMessageReadMode.LATEST,
                limit = 500,
            ),
        )

        assertEquals(500, result.requestedLimit)
        assertEquals(500, result.effectiveLimit)
        assertEquals(500, result.records.size)
    }
}

private fun testKafkaConfig(): UiKafkaConfig =
    UiKafkaConfig(
        clusters = listOf(
            UiKafkaClusterConfig(
                id = "local",
                name = "Local Kafka",
                readOnly = false,
                properties = mapOf(
                    "bootstrap.servers" to "localhost:19092",
                    "security.protocol" to "PLAINTEXT",
                    "client.id" to "datapool-loader",
                ),
            ),
        ),
    )

private fun tp(topic: String, partition: Int): TopicPartition =
    TopicPartition(topic, partition)

private fun record(
    partition: Int = 0,
    offset: Long,
    value: String,
    timestamp: Long = System.currentTimeMillis(),
): ConsumerRecord<ByteArray, ByteArray> =
    ConsumerRecord(
        "datapool-test",
        partition,
        offset,
        timestamp,
        TimestampType.CREATE_TIME,
        null,
        0,
        value.toByteArray().size,
        "key-$offset".toByteArray(),
        value.toByteArray(),
        RecordHeaders().add("source", "test".toByteArray()),
        Optional.empty(),
    )

private class FakeUiKafkaMessageConsumerFacadeFactory(
    private val partitions: Map<String, List<Int>> = emptyMap(),
    private val beginningOffsets: Map<TopicPartition, Long> = emptyMap(),
    private val endOffsets: Map<TopicPartition, Long> = emptyMap(),
    private val timestampOffsets: Map<KafkaTimestampKey, Long> = emptyMap(),
    private val records: Map<KafkaReadKey, List<ConsumerRecord<ByteArray, ByteArray>>> = emptyMap(),
) : UiKafkaMessageConsumerFacadeFactory {
    var singleReadCallCount: Int = 0
        private set
    var bulkReadCallCount: Int = 0
        private set

    override fun open(cluster: UiKafkaClusterConfig): UiKafkaMessageConsumerFacade =
        object : UiKafkaMessageConsumerFacade {
            override fun loadPartitions(topicName: String): List<PartitionInfo> =
                partitions[topicName].orEmpty().map { partition ->
                    PartitionInfo(topicName, partition, Node.noNode(), emptyArray(), emptyArray(), emptyArray())
                }

            override fun beginningOffsets(topicPartitions: List<TopicPartition>): Map<TopicPartition, Long?> =
                topicPartitions.associateWith { topicPartition -> beginningOffsets[topicPartition] }

            override fun endOffsets(topicPartitions: List<TopicPartition>): Map<TopicPartition, Long?> =
                topicPartitions.associateWith { topicPartition -> endOffsets[topicPartition] }

            override fun offsetsForTimestamps(
                timestampByPartition: Map<TopicPartition, Long>,
            ): Map<TopicPartition, Long?> =
                timestampByPartition.mapValues { (topicPartition, timestampMs) ->
                    timestampOffsets[KafkaTimestampKey(topicPartition.topic(), topicPartition.partition(), timestampMs)]
                }

            override fun readRecords(
                topicPartition: TopicPartition,
                startOffset: Long,
                limit: Int,
                pollTimeout: java.time.Duration,
            ): List<ConsumerRecord<ByteArray, ByteArray>> {
                singleReadCallCount += 1
                return records[KafkaReadKey(topicPartition.topic(), topicPartition.partition(), startOffset)]
                    .orEmpty()
                    .take(limit)
            }

            override fun readRecords(
                partitionReads: List<UiKafkaPartitionReadCursor>,
                perPartitionLimit: Int,
                pollTimeout: java.time.Duration,
            ): List<ConsumerRecord<ByteArray, ByteArray>> {
                bulkReadCallCount += 1
                return partitionReads.flatMap { partitionRead ->
                    records[KafkaReadKey(
                        partitionRead.topicPartition.topic(),
                        partitionRead.topicPartition.partition(),
                        partitionRead.startOffset,
                    )]
                        .orEmpty()
                        .take(perPartitionLimit)
                }
            }

            override fun close() = Unit
        }
}

private data class KafkaReadKey(
    val topic: String,
    val partition: Int,
    val startOffset: Long,
)

private data class KafkaTimestampKey(
    val topic: String,
    val partition: Int,
    val timestampMs: Long,
)
