package com.sbrf.lt.platform.composeui.kafka

class KafkaStore(
    private val api: KafkaApi,
) {
    suspend fun load(
        preferredClusterId: String? = null,
        preferredTopicName: String? = null,
        topicQuery: String = "",
    ): KafkaPageState {
        val runtimeContext = api.loadRuntimeContext()
        val info = api.loadInfo()
        val selectedClusterId = resolveClusterId(info, preferredClusterId)
        if (selectedClusterId == null) {
            return KafkaPageState(
                loading = false,
                runtimeContext = runtimeContext,
                info = info,
                topicQuery = topicQuery,
            )
        }

        val topics = api.loadTopics(selectedClusterId, topicQuery)
        val selectedTopicName = resolveTopicName(topics, preferredTopicName)
        val topicOverview = selectedTopicName?.let { api.loadTopicOverview(selectedClusterId, it) }
        return KafkaPageState(
            loading = false,
            runtimeContext = runtimeContext,
            info = info,
            selectedClusterId = selectedClusterId,
            topicQuery = topicQuery,
            topics = topics,
            selectedTopicName = selectedTopicName,
            topicOverview = topicOverview,
        )
    }

    fun startLoading(current: KafkaPageState): KafkaPageState =
        current.copy(loading = true, errorMessage = null)

    fun updateTopicQuery(
        current: KafkaPageState,
        query: String,
    ): KafkaPageState =
        current.copy(topicQuery = query, errorMessage = null)

    fun startTopicsReload(current: KafkaPageState): KafkaPageState =
        current.copy(topicsLoading = true, errorMessage = null)

    suspend fun reloadTopics(current: KafkaPageState): KafkaPageState {
        val clusterId = current.selectedClusterId ?: return current.copy(topicsLoading = false)
        val topics = api.loadTopics(clusterId, current.topicQuery)
        val selectedTopicName = resolveTopicName(topics, current.selectedTopicName)
        val topicOverview = selectedTopicName?.let { api.loadTopicOverview(clusterId, it) }
        return current.copy(
            topicsLoading = false,
            topics = topics,
            selectedTopicName = selectedTopicName,
            topicOverview = topicOverview,
            topicOverviewLoading = false,
        )
    }

    fun startTopicOverviewReload(current: KafkaPageState): KafkaPageState =
        current.copy(topicOverviewLoading = true, errorMessage = null)

    suspend fun loadTopicOverview(
        current: KafkaPageState,
        topicName: String,
    ): KafkaPageState {
        val clusterId = current.selectedClusterId ?: return current.copy(topicOverviewLoading = false)
        val overview = api.loadTopicOverview(clusterId, topicName)
        return current.copy(
            topicOverviewLoading = false,
            selectedTopicName = topicName,
            topicOverview = overview,
        )
    }

    private fun resolveClusterId(
        info: KafkaToolInfoResponse,
        preferredClusterId: String?,
    ): String? {
        val clusters = info.clusters
        if (clusters.isEmpty()) {
            return null
        }
        return clusters.firstOrNull { it.id == preferredClusterId }?.id ?: clusters.first().id
    }

    private fun resolveTopicName(
        topics: KafkaTopicsCatalogResponse,
        preferredTopicName: String?,
    ): String? {
        val availableTopics = topics.topics
        if (availableTopics.isEmpty()) {
            return null
        }
        return availableTopics.firstOrNull { it.name == preferredTopicName }?.name ?: availableTopics.first().name
    }
}
