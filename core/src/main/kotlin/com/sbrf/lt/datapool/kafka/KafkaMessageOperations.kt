package com.sbrf.lt.datapool.kafka

data class KafkaTopicMessageReadRequest(
    val clusterId: String,
    val topicName: String,
    val scope: KafkaTopicMessageReadScope = KafkaTopicMessageReadScope.SELECTED_PARTITION,
    val partition: Int? = null,
    val mode: KafkaTopicMessageReadMode,
    val limit: Int? = null,
    val offset: Long? = null,
    val timestampMs: Long? = null,
)

enum class KafkaTopicMessageReadScope {
    SELECTED_PARTITION,
    ALL_PARTITIONS,
}

enum class KafkaTopicMessageReadMode {
    LATEST,
    OFFSET,
    TIMESTAMP,
}

data class KafkaTopicMessageReadResult(
    val cluster: KafkaClusterCatalogEntry,
    val topicName: String,
    val scope: KafkaTopicMessageReadScope,
    val partition: Int? = null,
    val mode: KafkaTopicMessageReadMode,
    val requestedLimit: Int,
    val effectiveLimit: Int,
    val requestedOffset: Long? = null,
    val requestedTimestampMs: Long? = null,
    val effectiveStartOffset: Long? = null,
    val note: String? = null,
    val records: List<KafkaTopicMessageRecord> = emptyList(),
)

data class KafkaTopicMessageRecord(
    val partition: Int,
    val offset: Long,
    val timestamp: Long? = null,
    val key: KafkaRenderedBytes? = null,
    val value: KafkaRenderedBytes? = null,
    val headers: List<KafkaTopicMessageHeader> = emptyList(),
)

data class KafkaTopicMessageHeader(
    val name: String,
    val value: KafkaRenderedBytes? = null,
)

data class KafkaRenderedBytes(
    val sizeBytes: Int,
    val truncated: Boolean,
    val text: String? = null,
    val jsonPrettyText: String? = null,
)

interface KafkaMessageOperations {
    fun readMessages(
        request: KafkaTopicMessageReadRequest,
    ): KafkaTopicMessageReadResult
}

class KafkaTopicPartitionNotFoundException(
    clusterId: String,
    topicName: String,
    partition: Int,
) : IllegalArgumentException(
    "Kafka partition '$partition' не найдена для топика '$topicName' в кластере '$clusterId'.",
)
