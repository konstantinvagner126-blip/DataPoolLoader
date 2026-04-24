package com.sbrf.lt.datapool.kafka

data class KafkaTopicProduceRequest(
    val clusterId: String,
    val topicName: String,
    val partition: Int? = null,
    val keyText: String? = null,
    val payloadText: String,
    val headers: List<KafkaTopicProduceHeader> = emptyList(),
)

data class KafkaTopicProduceHeader(
    val name: String,
    val valueText: String? = null,
)

data class KafkaTopicProduceResult(
    val cluster: KafkaClusterCatalogEntry,
    val topicName: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Long? = null,
)

interface KafkaProduceOperations {
    fun produce(
        request: KafkaTopicProduceRequest,
    ): KafkaTopicProduceResult
}
