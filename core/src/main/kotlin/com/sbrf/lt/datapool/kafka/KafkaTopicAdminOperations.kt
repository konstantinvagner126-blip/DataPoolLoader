package com.sbrf.lt.datapool.kafka

data class KafkaTopicCreateRequest(
    val clusterId: String,
    val topicName: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val cleanupPolicy: String? = null,
    val retentionMs: Long? = null,
    val retentionBytes: Long? = null,
)

data class KafkaTopicCreateResult(
    val cluster: KafkaClusterCatalogEntry,
    val topicName: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val cleanupPolicy: String? = null,
    val retentionMs: Long? = null,
    val retentionBytes: Long? = null,
)

interface KafkaTopicAdminOperations {
    fun createTopic(
        request: KafkaTopicCreateRequest,
    ): KafkaTopicCreateResult
}
