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
    val consumerGroups: KafkaTopicConsumerGroupsSummaryResponse = KafkaTopicConsumerGroupsSummaryResponse(),
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

@Serializable
data class KafkaTopicConsumerGroupsSummaryResponse(
    val status: String = "EMPTY",
    val message: String? = null,
    val groups: List<KafkaTopicConsumerGroupSummaryResponse> = emptyList(),
)

@Serializable
data class KafkaTopicConsumerGroupSummaryResponse(
    val groupId: String,
    val state: String? = null,
    val memberCount: Int? = null,
    val metadataAvailable: Boolean = true,
    val totalLag: Long? = null,
    val lagStatus: String = "OK",
    val note: String? = null,
    val partitions: List<KafkaTopicConsumerGroupPartitionLagResponse> = emptyList(),
)

@Serializable
data class KafkaTopicConsumerGroupPartitionLagResponse(
    val partition: Int,
    val committedOffset: Long? = null,
    val latestOffset: Long? = null,
    val lag: Long? = null,
)

@Serializable
data class KafkaClusterConsumerGroupsCatalogResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val status: String = "EMPTY",
    val message: String? = null,
    val groups: List<KafkaClusterConsumerGroupSummaryResponse> = emptyList(),
)

@Serializable
data class KafkaClusterConsumerGroupSummaryResponse(
    val groupId: String,
    val state: String? = null,
    val memberCount: Int? = null,
    val metadataAvailable: Boolean = true,
    val totalLag: Long? = null,
    val lagStatus: String = "OK",
    val note: String? = null,
    val topics: List<KafkaClusterConsumerGroupTopicSummaryResponse> = emptyList(),
)

@Serializable
data class KafkaClusterConsumerGroupTopicSummaryResponse(
    val topicName: String,
    val partitionCount: Int,
    val totalLag: Long? = null,
    val partitions: List<KafkaTopicConsumerGroupPartitionLagResponse> = emptyList(),
)

@Serializable
data class KafkaClusterBrokersCatalogResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val controllerBrokerId: Int? = null,
    val brokers: List<KafkaBrokerSummaryResponse> = emptyList(),
)

@Serializable
data class KafkaBrokerSummaryResponse(
    val brokerId: Int,
    val host: String,
    val port: Int,
    val rack: String? = null,
    val controller: Boolean = false,
)

@Serializable
data class KafkaTopicMessageReadRequestPayload(
    val clusterId: String,
    val topicName: String,
    val scope: String = "SELECTED_PARTITION",
    val partition: Int? = null,
    val mode: String,
    val limit: Int? = null,
    val offset: Long? = null,
    val timestampMs: Long? = null,
)

@Serializable
data class KafkaTopicMessageReadResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val topicName: String,
    val scope: String,
    val partition: Int? = null,
    val mode: String,
    val requestedLimit: Int,
    val effectiveLimit: Int,
    val requestedOffset: Long? = null,
    val requestedTimestampMs: Long? = null,
    val effectiveStartOffset: Long? = null,
    val note: String? = null,
    val records: List<KafkaTopicMessageRecordResponse> = emptyList(),
)

@Serializable
data class KafkaTopicMessageRecordResponse(
    val partition: Int,
    val offset: Long,
    val timestamp: Long? = null,
    val key: KafkaRenderedBytesResponse? = null,
    val value: KafkaRenderedBytesResponse? = null,
    val headers: List<KafkaTopicMessageHeaderResponse> = emptyList(),
)

@Serializable
data class KafkaTopicProduceRequestPayload(
    val clusterId: String,
    val topicName: String,
    val partition: Int? = null,
    val keyText: String? = null,
    val payloadText: String,
    val headers: List<KafkaTopicProduceHeaderRequestPayload> = emptyList(),
)

@Serializable
data class KafkaTopicProduceHeaderRequestPayload(
    val name: String,
    val valueText: String? = null,
)

@Serializable
data class KafkaTopicCreateRequestPayload(
    val clusterId: String,
    val topicName: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val cleanupPolicy: String? = null,
    val retentionMs: Long? = null,
    val retentionBytes: Long? = null,
)

@Serializable
data class KafkaTopicCreateResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val topicName: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val cleanupPolicy: String? = null,
    val retentionMs: Long? = null,
    val retentionBytes: Long? = null,
)

data class KafkaKeyValueDraft(
    val name: String = "",
    val value: String = "",
)

@Serializable
data class KafkaTopicProduceResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val topicName: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Long? = null,
)

@Serializable
data class KafkaSettingsResponse(
    val editableConfigPath: String? = null,
    val clusters: List<KafkaEditableClusterResponse> = emptyList(),
)

@Serializable
data class KafkaEditableClusterResponse(
    val id: String,
    val name: String,
    val readOnly: Boolean,
    val bootstrapServers: String = "",
    val clientId: String = "",
    val securityProtocol: String = "PLAINTEXT",
    val truststoreType: String = "",
    val truststoreLocation: String = "",
    val truststoreCertificates: String = "",
    val keystoreType: String = "",
    val keystoreLocation: String = "",
    val keystoreCertificateChain: String = "",
    val keystoreKey: String = "",
    val keyPassword: String = "",
    val additionalProperties: Map<String, String> = emptyMap(),
)

@Serializable
data class KafkaSettingsUpdateRequestPayload(
    val clusters: List<KafkaEditableClusterRequestPayload> = emptyList(),
)

@Serializable
data class KafkaEditableClusterRequestPayload(
    val id: String,
    val name: String,
    val readOnly: Boolean,
    val bootstrapServers: String = "",
    val clientId: String = "",
    val securityProtocol: String = "PLAINTEXT",
    val truststoreType: String = "",
    val truststoreLocation: String = "",
    val truststoreCertificates: String = "",
    val keystoreType: String = "",
    val keystoreLocation: String = "",
    val keystoreCertificateChain: String = "",
    val keystoreKey: String = "",
    val keyPassword: String = "",
    val additionalProperties: Map<String, String> = emptyMap(),
)

@Serializable
data class KafkaSettingsConnectionTestRequestPayload(
    val cluster: KafkaEditableClusterRequestPayload,
)

@Serializable
data class KafkaSettingsConnectionTestResponse(
    val success: Boolean,
    val message: String,
    val nodeCount: Int? = null,
)

@Serializable
data class KafkaTopicMessageHeaderResponse(
    val name: String,
    val value: KafkaRenderedBytesResponse? = null,
)

@Serializable
data class KafkaRenderedBytesResponse(
    val sizeBytes: Int,
    val truncated: Boolean,
    val text: String? = null,
    val jsonPrettyText: String? = null,
)

data class KafkaPageState(
    val loading: Boolean = true,
    val topicsLoading: Boolean = false,
    val consumerGroupsLoading: Boolean = false,
    val brokersLoading: Boolean = false,
    val topicOverviewLoading: Boolean = false,
    val messagesLoading: Boolean = false,
    val errorMessage: String? = null,
    val consumerGroupsError: String? = null,
    val brokersError: String? = null,
    val messagesError: String? = null,
    val runtimeContext: RuntimeContext? = null,
    val info: KafkaToolInfoResponse? = null,
    val selectedClusterId: String? = null,
    val clusterSection: String = "topics",
    val topicQuery: String = "",
    val topics: KafkaTopicsCatalogResponse? = null,
    val consumerGroups: KafkaClusterConsumerGroupsCatalogResponse? = null,
    val brokers: KafkaClusterBrokersCatalogResponse? = null,
    val selectedTopicName: String? = null,
    val topicOverview: KafkaTopicOverviewResponse? = null,
    val activePane: String = "overview",
    val createTopicFormVisible: Boolean = false,
    val createTopicLoading: Boolean = false,
    val createTopicError: String? = null,
    val createTopicNameInput: String = "",
    val createTopicPartitionsInput: String = "1",
    val createTopicReplicationFactorInput: String = "1",
    val createTopicCleanupPolicyInput: String = "",
    val createTopicRetentionMsInput: String = "",
    val createTopicRetentionBytesInput: String = "",
    val createTopicResult: KafkaTopicCreateResponse? = null,
    val selectedMessagePartition: Int? = null,
    val messageReadScope: String = "SELECTED_PARTITION",
    val messageReadMode: String = "LATEST",
    val messageLimitInput: String = "50",
    val messageOffsetInput: String = "",
    val messageTimestampInput: String = "",
    val messages: KafkaTopicMessageReadResponse? = null,
    val produceLoading: Boolean = false,
    val produceError: String? = null,
    val producePartitionInput: String = "",
    val produceKeyInput: String = "",
    val produceHeaders: List<KafkaKeyValueDraft> = emptyList(),
    val producePayloadInput: String = "",
    val produceResult: KafkaTopicProduceResponse? = null,
    val settingsLoading: Boolean = false,
    val settingsError: String? = null,
    val settings: KafkaSettingsResponse? = null,
    val settingsStatusMessage: String? = null,
    val settingsConnectionTestClusterIndex: Int? = null,
    val settingsConnectionResult: KafkaSettingsConnectionTestResponse? = null,
)
