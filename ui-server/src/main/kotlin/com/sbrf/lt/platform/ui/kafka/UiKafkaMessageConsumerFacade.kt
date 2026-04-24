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

    fun beginningOffset(topicPartition: TopicPartition): Long?

    fun endOffset(topicPartition: TopicPartition): Long?

    fun offsetForTimestamp(
        topicPartition: TopicPartition,
        timestampMs: Long,
    ): Long?

    fun readRecords(
        topicPartition: TopicPartition,
        startOffset: Long,
        limit: Int,
        pollTimeout: Duration,
    ): List<ConsumerRecord<ByteArray, ByteArray>>
}

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

    override fun beginningOffset(topicPartition: TopicPartition): Long? =
        consumer.beginningOffsets(listOf(topicPartition))[topicPartition]

    override fun endOffset(topicPartition: TopicPartition): Long? =
        consumer.endOffsets(listOf(topicPartition))[topicPartition]

    override fun offsetForTimestamp(
        topicPartition: TopicPartition,
        timestampMs: Long,
    ): Long? =
        consumer.offsetsForTimes(mapOf(topicPartition to timestampMs))[topicPartition]?.offset()

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

    override fun close() {
        consumer.close()
    }
}
