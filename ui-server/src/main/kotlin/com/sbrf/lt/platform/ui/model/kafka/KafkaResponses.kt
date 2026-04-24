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
    val consumerGroups: KafkaTopicConsumerGroupsSummaryResponse = KafkaTopicConsumerGroupsSummaryResponse(),
)

data class KafkaTopicPartitionSummaryResponse(
    val partition: Int,
    val leaderId: Int? = null,
    val replicaCount: Int,
    val inSyncReplicaCount: Int,
    val earliestOffset: Long? = null,
    val latestOffset: Long? = null,
)

data class KafkaTopicConsumerGroupsSummaryResponse(
    val status: String = "EMPTY",
    val message: String? = null,
    val groups: List<KafkaTopicConsumerGroupSummaryResponse> = emptyList(),
)

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

data class KafkaTopicConsumerGroupPartitionLagResponse(
    val partition: Int,
    val committedOffset: Long? = null,
    val latestOffset: Long? = null,
    val lag: Long? = null,
)

data class KafkaClusterConsumerGroupsCatalogResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val status: String = "EMPTY",
    val message: String? = null,
    val groups: List<KafkaClusterConsumerGroupSummaryResponse> = emptyList(),
)

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

data class KafkaClusterConsumerGroupTopicSummaryResponse(
    val topicName: String,
    val partitionCount: Int,
    val totalLag: Long? = null,
    val partitions: List<KafkaTopicConsumerGroupPartitionLagResponse> = emptyList(),
)

data class KafkaClusterBrokersCatalogResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val controllerBrokerId: Int? = null,
    val brokers: List<KafkaBrokerSummaryResponse> = emptyList(),
)

data class KafkaBrokerSummaryResponse(
    val brokerId: Int,
    val host: String,
    val port: Int,
    val rack: String? = null,
    val controller: Boolean = false,
)

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

data class KafkaTopicMessageRecordResponse(
    val partition: Int,
    val offset: Long,
    val timestamp: Long? = null,
    val key: KafkaRenderedBytesResponse? = null,
    val value: KafkaRenderedBytesResponse? = null,
    val headers: List<KafkaTopicMessageHeaderResponse> = emptyList(),
)

data class KafkaTopicProduceRequestPayload(
    val clusterId: String,
    val topicName: String,
    val partition: Int? = null,
    val keyText: String? = null,
    val payloadText: String,
    val headers: List<KafkaTopicProduceHeaderRequestPayload> = emptyList(),
)

data class KafkaTopicProduceHeaderRequestPayload(
    val name: String,
    val valueText: String? = null,
)

data class KafkaTopicCreateRequestPayload(
    val clusterId: String,
    val topicName: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val cleanupPolicy: String? = null,
    val retentionMs: Long? = null,
    val retentionBytes: Long? = null,
)

data class KafkaTopicCreateResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val topicName: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val cleanupPolicy: String? = null,
    val retentionMs: Long? = null,
    val retentionBytes: Long? = null,
)

data class KafkaTopicProduceResponse(
    val cluster: KafkaClusterCatalogEntryResponse,
    val topicName: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Long? = null,
)

data class KafkaSettingsResponse(
    val editableConfigPath: String? = null,
    val clusters: List<KafkaEditableClusterResponse> = emptyList(),
)

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

data class KafkaSettingsUpdateRequestPayload(
    val clusters: List<KafkaEditableClusterRequestPayload> = emptyList(),
)

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

data class KafkaSettingsConnectionTestRequestPayload(
    val cluster: KafkaEditableClusterRequestPayload,
)

data class KafkaSettingsConnectionTestResponse(
    val success: Boolean,
    val message: String,
    val nodeCount: Int? = null,
)

data class KafkaSettingsFilePickRequestPayload(
    val targetProperty: String,
    val currentValue: String = "",
)

data class KafkaSettingsFilePickResponse(
    val targetProperty: String,
    val cancelled: Boolean,
    val selectedPath: String? = null,
    val configValue: String? = null,
)

data class KafkaTopicMessageHeaderResponse(
    val name: String,
    val value: KafkaRenderedBytesResponse? = null,
)

data class KafkaRenderedBytesResponse(
    val sizeBytes: Int,
    val truncated: Boolean,
    val text: String? = null,
    val jsonPrettyText: String? = null,
)
