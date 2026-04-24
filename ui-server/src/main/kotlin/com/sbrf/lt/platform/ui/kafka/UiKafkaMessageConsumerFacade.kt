package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import java.time.Duration

internal interface UiKafkaMessageConsumerFacadeFactory {
    fun open(cluster: UiKafkaClusterConfig): UiKafkaMessageConsumerFacade
}

internal interface UiKafkaMessageConsumerFacade : AutoCloseable {
    fun loadPartitions(topicName: String): List<PartitionInfo>

    fun beginningOffsets(topicPartitions: List<TopicPartition>): Map<TopicPartition, Long?>

    fun endOffsets(topicPartitions: List<TopicPartition>): Map<TopicPartition, Long?>

    fun offsetsForTimestamps(
        timestampByPartition: Map<TopicPartition, Long>,
    ): Map<TopicPartition, Long?>

    fun readRecords(
        topicPartition: TopicPartition,
        startOffset: Long,
        limit: Int,
        pollTimeout: Duration,
    ): List<ConsumerRecord<ByteArray, ByteArray>>

    fun readRecords(
        partitionReads: List<UiKafkaPartitionReadCursor>,
        perPartitionLimit: Int,
        pollTimeout: Duration,
    ): List<ConsumerRecord<ByteArray, ByteArray>>

    fun beginningOffset(topicPartition: TopicPartition): Long? =
        beginningOffsets(listOf(topicPartition))[topicPartition]

    fun endOffset(topicPartition: TopicPartition): Long? =
        endOffsets(listOf(topicPartition))[topicPartition]

    fun offsetForTimestamp(
        topicPartition: TopicPartition,
        timestampMs: Long,
    ): Long? =
        offsetsForTimestamps(mapOf(topicPartition to timestampMs))[topicPartition]
}

internal data class UiKafkaPartitionReadCursor(
    val topicPartition: TopicPartition,
    val startOffset: Long,
)

internal class DefaultUiKafkaMessageConsumerFacadeFactory(
    private val clientFactory: UiKafkaClientFactory = DefaultUiKafkaClientFactory(),
) : UiKafkaMessageConsumerFacadeFactory {
    override fun open(cluster: UiKafkaClusterConfig): UiKafkaMessageConsumerFacade =
        KafkaConsumerUiKafkaMessageConsumerFacade(
            clientFactory.createConsumer(cluster, "browser-read"),
        )
}

private class KafkaConsumerUiKafkaMessageConsumerFacade(
    private val consumer: org.apache.kafka.clients.consumer.KafkaConsumer<ByteArray, ByteArray>,
) : UiKafkaMessageConsumerFacade {

    override fun loadPartitions(topicName: String): List<PartitionInfo> =
        consumer.partitionsFor(topicName)

    override fun beginningOffsets(topicPartitions: List<TopicPartition>): Map<TopicPartition, Long?> =
        consumer.beginningOffsets(topicPartitions).mapValues { (_, offset) -> offset }

    override fun endOffsets(topicPartitions: List<TopicPartition>): Map<TopicPartition, Long?> =
        consumer.endOffsets(topicPartitions).mapValues { (_, offset) -> offset }

    override fun offsetsForTimestamps(
        timestampByPartition: Map<TopicPartition, Long>,
    ): Map<TopicPartition, Long?> =
        consumer.offsetsForTimes(timestampByPartition)
            .mapValues { (_, offsetAndTimestamp) -> offsetAndTimestamp?.offset() }

    override fun readRecords(
        topicPartition: TopicPartition,
        startOffset: Long,
        limit: Int,
        pollTimeout: Duration,
    ): List<ConsumerRecord<ByteArray, ByteArray>> {
        consumer.assign(listOf(topicPartition))
        consumer.seek(topicPartition, startOffset)
        val result = mutableListOf<ConsumerRecord<ByteArray, ByteArray>>()
        while (result.size < limit) {
            val polled = consumer.poll(pollTimeout).records(topicPartition)
            if (polled.isEmpty()) {
                break
            }
            polled.forEach { record ->
                if (result.size < limit) {
                    result += record
                }
            }
        }
        return result
    }

    override fun readRecords(
        partitionReads: List<UiKafkaPartitionReadCursor>,
        perPartitionLimit: Int,
        pollTimeout: Duration,
    ): List<ConsumerRecord<ByteArray, ByteArray>> {
        if (partitionReads.isEmpty()) {
            return emptyList()
        }
        consumer.assign(partitionReads.map { it.topicPartition })
        partitionReads.forEach { partitionRead ->
            consumer.seek(partitionRead.topicPartition, partitionRead.startOffset)
        }
        val result = mutableListOf<ConsumerRecord<ByteArray, ByteArray>>()
        val countsByPartition = partitionReads.associate { it.topicPartition to 0 }.toMutableMap()
        val activePartitions = partitionReads.map { it.topicPartition }.toMutableSet()
        while (activePartitions.isNotEmpty()) {
            val polled = consumer.poll(pollTimeout)
            if (polled.isEmpty) {
                break
            }
            activePartitions.toList().forEach { topicPartition ->
                val partitionRecords = polled.records(topicPartition)
                if (partitionRecords.isEmpty()) {
                    return@forEach
                }
                partitionRecords.forEach { record ->
                    val currentCount = countsByPartition.getValue(topicPartition)
                    if (currentCount < perPartitionLimit) {
                        result += record
                        countsByPartition[topicPartition] = currentCount + 1
                    }
                }
                if (countsByPartition.getValue(topicPartition) >= perPartitionLimit) {
                    activePartitions.remove(topicPartition)
                }
            }
        }
        return result
    }

    override fun close() {
        consumer.close()
    }
}
