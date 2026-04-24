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
    val consumerGroups: KafkaTopicConsumerGroupsSummary = KafkaTopicConsumerGroupsSummary(),
)

data class KafkaTopicPartitionSummary(
    val partition: Int,
    val leaderId: Int? = null,
    val replicaCount: Int,
    val inSyncReplicaCount: Int,
    val earliestOffset: Long? = null,
    val latestOffset: Long? = null,
)

data class KafkaTopicConsumerGroupsSummary(
    val status: KafkaTopicConsumerGroupsStatus = KafkaTopicConsumerGroupsStatus.EMPTY,
    val message: String? = null,
    val groups: List<KafkaTopicConsumerGroupSummary> = emptyList(),
)

enum class KafkaTopicConsumerGroupsStatus {
    AVAILABLE,
    EMPTY,
    ERROR,
}

data class KafkaTopicConsumerGroupSummary(
    val groupId: String,
    val state: String? = null,
    val memberCount: Int? = null,
    val metadataAvailable: Boolean = true,
    val totalLag: Long? = null,
    val lagStatus: KafkaTopicConsumerGroupLagStatus = KafkaTopicConsumerGroupLagStatus.OK,
    val note: String? = null,
    val partitions: List<KafkaTopicConsumerGroupPartitionLag> = emptyList(),
)

enum class KafkaTopicConsumerGroupLagStatus {
    OK,
    PARTIAL,
    AUTHORIZATION_FAILED,
    TIMEOUT,
    ERROR,
}

data class KafkaTopicConsumerGroupPartitionLag(
    val partition: Int,
    val committedOffset: Long? = null,
    val latestOffset: Long? = null,
    val lag: Long? = null,
)

data class KafkaClusterConsumerGroupsCatalog(
    val cluster: KafkaClusterCatalogEntry,
    val status: KafkaClusterConsumerGroupsStatus = KafkaClusterConsumerGroupsStatus.EMPTY,
    val message: String? = null,
    val groups: List<KafkaClusterConsumerGroupSummary> = emptyList(),
)

enum class KafkaClusterConsumerGroupsStatus {
    AVAILABLE,
    EMPTY,
    ERROR,
}

data class KafkaClusterConsumerGroupSummary(
    val groupId: String,
    val state: String? = null,
    val memberCount: Int? = null,
    val metadataAvailable: Boolean = true,
    val totalLag: Long? = null,
    val lagStatus: KafkaTopicConsumerGroupLagStatus = KafkaTopicConsumerGroupLagStatus.OK,
    val note: String? = null,
    val topics: List<KafkaClusterConsumerGroupTopicSummary> = emptyList(),
)

data class KafkaClusterConsumerGroupTopicSummary(
    val topicName: String,
    val partitionCount: Int,
    val totalLag: Long? = null,
    val partitions: List<KafkaTopicConsumerGroupPartitionLag> = emptyList(),
)

data class KafkaClusterBrokersCatalog(
    val cluster: KafkaClusterCatalogEntry,
    val controllerBrokerId: Int? = null,
    val brokers: List<KafkaBrokerSummary> = emptyList(),
)

data class KafkaBrokerSummary(
    val brokerId: Int,
    val host: String,
    val port: Int,
    val rack: String? = null,
    val controller: Boolean = false,
)

interface KafkaMetadataOperations {
    fun info(): KafkaToolInfo

    fun listTopics(
        clusterId: String,
        query: String = "",
    ): KafkaTopicsCatalog

    fun listConsumerGroups(
        clusterId: String,
    ): KafkaClusterConsumerGroupsCatalog

    fun listBrokers(
        clusterId: String,
    ): KafkaClusterBrokersCatalog

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
