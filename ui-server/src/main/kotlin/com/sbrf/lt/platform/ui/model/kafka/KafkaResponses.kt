package com.sbrf.lt.platform.ui.model

data class KafkaToolInfoResponse(
    val configured: Boolean,
    val maxRecordsPerRead: Int,
    val maxPayloadBytes: Int,
    val clusters: List<KafkaClusterCatalogEntryResponse> = emptyList(),
)

data class KafkaClusterCatalogEntryResponse(
    val id: String,
    val name: String,
    val readOnly: Boolean,
    val bootstrapServers: String,
    val securityProtocol: String,
)

data class KafkaTopicsCatalogResponse(
    val clusterId: String,
    val query: String,
    val topics: List<KafkaTopicSummaryResponse> = emptyList(),
)

data class KafkaTopicSummaryResponse(
    val name: String,
    val internal: Boolean,
    val partitionCount: Int,
    val replicationFactor: Int,
    val cleanupPolicy: String? = null,
    val retentionMs: Long? = null,
    val retentionBytes: Long? = null,
)

data class KafkaTopicOverviewResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val topic: KafkaTopicSummaryResponse,
    val partitions: List<KafkaTopicPartitionSummaryResponse> = emptyList(),
)

data class KafkaTopicPartitionSummaryResponse(
    val partition: Int,
    val leaderId: Int? = null,
    val replicaCount: Int,
    val inSyncReplicaCount: Int,
    val earliestOffset: Long? = null,
    val latestOffset: Long? = null,
)
