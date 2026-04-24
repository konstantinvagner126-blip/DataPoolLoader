package com.sbrf.lt.datapool.kafka

data class KafkaToolInfo(
    val configured: Boolean,
    val maxRecordsPerRead: Int,
    val maxPayloadBytes: Int,
    val clusters: List<KafkaClusterCatalogEntry> = emptyList(),
)

data class KafkaClusterCatalogEntry(
    val id: String,
    val name: String,
    val readOnly: Boolean,
    val bootstrapServers: String,
    val securityProtocol: String,
)

data class KafkaTopicsCatalog(
    val clusterId: String,
    val query: String,
    val topics: List<KafkaTopicSummary> = emptyList(),
)

data class KafkaTopicSummary(
    val name: String,
    val internal: Boolean,
    val partitionCount: Int,
    val replicationFactor: Int,
    val cleanupPolicy: String? = null,
    val retentionMs: Long? = null,
    val retentionBytes: Long? = null,
)

data class KafkaTopicOverview(
    val cluster: KafkaClusterCatalogEntry,
    val topic: KafkaTopicSummary,
    val partitions: List<KafkaTopicPartitionSummary> = emptyList(),
)

data class KafkaTopicPartitionSummary(
    val partition: Int,
    val leaderId: Int? = null,
    val replicaCount: Int,
    val inSyncReplicaCount: Int,
    val earliestOffset: Long? = null,
    val latestOffset: Long? = null,
)

interface KafkaMetadataOperations {
    fun info(): KafkaToolInfo

    fun listTopics(
        clusterId: String,
        query: String = "",
    ): KafkaTopicsCatalog

    fun loadTopicOverview(
        clusterId: String,
        topicName: String,
    ): KafkaTopicOverview
}

class KafkaClusterNotFoundException(
    clusterId: String,
) : IllegalArgumentException("Kafka cluster '$clusterId' не найден в ui.kafka.clusters.")

class KafkaTopicNotFoundException(
    clusterId: String,
    topicName: String,
) : IllegalArgumentException("Kafka topic '$topicName' не найден в кластере '$clusterId'.")
