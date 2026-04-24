package com.sbrf.lt.platform.composeui.kafka

import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlinx.serialization.Serializable

@Serializable
data class KafkaToolInfoResponse(
    val configured: Boolean,
    val maxRecordsPerRead: Int,
    val maxPayloadBytes: Int,
    val clusters: List<KafkaClusterCatalogEntryResponse> = emptyList(),
)

@Serializable
data class KafkaClusterCatalogEntryResponse(
    val id: String,
    val name: String,
    val readOnly: Boolean,
    val bootstrapServers: String,
    val securityProtocol: String,
)

@Serializable
data class KafkaTopicsCatalogResponse(
    val clusterId: String,
    val query: String,
    val topics: List<KafkaTopicSummaryResponse> = emptyList(),
)

@Serializable
data class KafkaTopicSummaryResponse(
    val name: String,
    val internal: Boolean,
    val partitionCount: Int,
    val replicationFactor: Int,
    val cleanupPolicy: String? = null,
    val retentionMs: Long? = null,
    val retentionBytes: Long? = null,
)

@Serializable
data class KafkaTopicOverviewResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val topic: KafkaTopicSummaryResponse,
    val partitions: List<KafkaTopicPartitionSummaryResponse> = emptyList(),
)

@Serializable
data class KafkaTopicPartitionSummaryResponse(
    val partition: Int,
    val leaderId: Int? = null,
    val replicaCount: Int,
    val inSyncReplicaCount: Int,
    val earliestOffset: Long? = null,
    val latestOffset: Long? = null,
)

data class KafkaPageState(
    val loading: Boolean = true,
    val topicsLoading: Boolean = false,
    val topicOverviewLoading: Boolean = false,
    val errorMessage: String? = null,
    val runtimeContext: RuntimeContext? = null,
    val info: KafkaToolInfoResponse? = null,
    val selectedClusterId: String? = null,
    val topicQuery: String = "",
    val topics: KafkaTopicsCatalogResponse? = null,
    val selectedTopicName: String? = null,
    val topicOverview: KafkaTopicOverviewResponse? = null,
)
