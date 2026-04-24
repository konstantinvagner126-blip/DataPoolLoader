package com.sbrf.lt.platform.composeui.kafka

internal class KafkaStoreStateSupport {
    fun resolveClusterId(
        info: KafkaToolInfoResponse,
        preferredClusterId: String?,
    ): String? {
        val clusters = info.clusters
        if (clusters.isEmpty()) {
            return null
        }
        return clusters.firstOrNull { it.id == preferredClusterId }?.id ?: clusters.first().id
    }

    fun resolveTopicName(
        topics: KafkaTopicsCatalogResponse,
        preferredTopicName: String?,
    ): String? {
        val availableTopics = topics.topics
        if (availableTopics.isEmpty()) {
            return null
        }
        return availableTopics.firstOrNull { it.name == preferredTopicName }?.name
    }

    fun resolveMessagePartition(
        preferredPartition: Int?,
        topicOverview: KafkaTopicOverviewResponse?,
    ): Int? {
        val partitions = topicOverview?.partitions.orEmpty()
        if (partitions.isEmpty()) {
            return null
        }
        return partitions.firstOrNull { it.partition == preferredPartition }?.partition ?: partitions.first().partition
    }

    fun resolveInitialMessageLimit(maxRecordsPerRead: Int): String =
        minOf(50, maxRecordsPerRead).toString()

    fun normalizeClusterSection(value: String): String =
        when (value.trim().uppercase()) {
            "CONSUMER-GROUPS",
            "CONSUMER_GROUPS" -> "consumer-groups"
            "BROKERS" -> "brokers"
            else -> "topics"
        }

    fun normalizePane(value: String): String =
        value.trim().uppercase().let { normalized ->
            when (normalized) {
                "MESSAGES" -> "messages"
                "CONSUMERS" -> "consumers"
                "PRODUCE" -> "produce"
                "SETTINGS" -> "settings"
                "CLUSTER-SETTINGS",
                "CLUSTER_SETTINGS" -> "cluster-settings"
                else -> "overview"
            }
        }

    fun resolveActivePane(
        requestedPane: String,
        selectedTopicName: String?,
    ): String {
        val normalizedPane = normalizePane(requestedPane)
        return when {
            normalizedPane == "cluster-settings" -> "cluster-settings"
            selectedTopicName.isNullOrBlank() -> "overview"
            else -> normalizedPane
        }
    }

    fun normalizeMessageScope(value: String): String =
        when (value.trim().uppercase()) {
            "ALL_PARTITIONS" -> "ALL_PARTITIONS"
            else -> "SELECTED_PARTITION"
        }

    fun normalizeMessageMode(value: String): String =
        when (value.trim().uppercase()) {
            "OFFSET" -> "OFFSET"
            "TIMESTAMP" -> "TIMESTAMP"
            else -> "LATEST"
        }
}
