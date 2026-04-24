package com.sbrf.lt.platform.composeui.kafka

internal class KafkaStoreTopicAdminSupport(
    private val api: KafkaApi,
    private val stateSupport: KafkaStoreStateSupport,
) {
    fun toggleCreateTopicForm(current: KafkaPageState): KafkaPageState =
        current.copy(
            createTopicFormVisible = !current.createTopicFormVisible,
            createTopicLoading = false,
            createTopicError = null,
            createTopicResult = null,
        )

    fun updateCreateTopicNameInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        current.copy(
            createTopicNameInput = value,
            createTopicError = null,
            createTopicResult = null,
        )

    fun updateCreateTopicPartitionsInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        current.copy(
            createTopicPartitionsInput = value,
            createTopicError = null,
            createTopicResult = null,
        )

    fun updateCreateTopicReplicationFactorInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        current.copy(
            createTopicReplicationFactorInput = value,
            createTopicError = null,
            createTopicResult = null,
        )

    fun updateCreateTopicCleanupPolicyInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        current.copy(
            createTopicCleanupPolicyInput = value,
            createTopicError = null,
            createTopicResult = null,
        )

    fun updateCreateTopicRetentionMsInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        current.copy(
            createTopicRetentionMsInput = value,
            createTopicError = null,
            createTopicResult = null,
        )

    fun updateCreateTopicRetentionBytesInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        current.copy(
            createTopicRetentionBytesInput = value,
            createTopicError = null,
            createTopicResult = null,
        )

    fun startCreateTopic(current: KafkaPageState): KafkaPageState =
        current.copy(
            createTopicLoading = true,
            createTopicError = null,
            createTopicResult = null,
        )

    suspend fun createTopic(current: KafkaPageState): KafkaPageState {
        val clusterId = current.selectedClusterId ?: return current.copy(createTopicLoading = false)
        val partitionCount = current.createTopicPartitionsInput.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Kafka partitions должны быть целым числом.")
        val replicationFactor = current.createTopicReplicationFactorInput.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Kafka replication factor должен быть целым числом.")
        val retentionMs = parseOptionalLong(
            raw = current.createTopicRetentionMsInput,
            label = "Kafka retention.ms",
        )
        val retentionBytes = parseOptionalLong(
            raw = current.createTopicRetentionBytesInput,
            label = "Kafka retention.bytes",
        )
        val request = KafkaTopicCreateRequestPayload(
            clusterId = clusterId,
            topicName = current.createTopicNameInput,
            partitionCount = partitionCount,
            replicationFactor = replicationFactor,
            cleanupPolicy = current.createTopicCleanupPolicyInput.trim().takeIf { it.isNotEmpty() },
            retentionMs = retentionMs,
            retentionBytes = retentionBytes,
        )
        val result = api.createTopic(request)
        val refreshedTopics = runCatching { api.loadTopics(clusterId, current.topicQuery) }.getOrNull()
        val refreshedOverview = runCatching { api.loadTopicOverview(clusterId, result.topicName) }.getOrNull()
        val refreshWarning = if (refreshedTopics == null || refreshedOverview == null) {
            "Топик создан, но экран не удалось полностью обновить. Обнови каталог вручную."
        } else {
            null
        }
        return current.copy(
            clusterSection = "topics",
            activePane = "overview",
            topics = refreshedTopics ?: current.topics,
            selectedTopicName = result.topicName,
            topicOverview = refreshedOverview,
            selectedMessagePartition = stateSupport.resolveMessagePartition(
                preferredPartition = current.selectedMessagePartition,
                topicOverview = refreshedOverview,
            ),
            createTopicFormVisible = false,
            createTopicLoading = false,
            createTopicError = refreshWarning,
            createTopicResult = result,
            createTopicNameInput = "",
            createTopicPartitionsInput = "1",
            createTopicReplicationFactorInput = "1",
            createTopicCleanupPolicyInput = "",
            createTopicRetentionMsInput = "",
            createTopicRetentionBytesInput = "",
            messages = null,
            messagesError = null,
            produceResult = null,
            produceError = null,
        )
    }

    private fun parseOptionalLong(
        raw: String,
        label: String,
    ): Long? {
        val normalized = raw.trim()
        if (normalized.isEmpty()) {
            return null
        }
        return normalized.toLongOrNull()
            ?: throw IllegalArgumentException("$label должен быть целым числом.")
    }
}
