package com.sbrf.lt.platform.composeui.kafka

internal class KafkaStoreLoadingSupport(
    private val api: KafkaApi,
    private val stateSupport: KafkaStoreStateSupport,
) {
    suspend fun load(
        preferredClusterId: String?,
        preferredClusterSection: String,
        preferredTopicName: String?,
        topicQuery: String,
        activePane: String,
        preferredMessageReadScope: String,
        preferredMessageReadMode: String,
        preferredMessagePartition: Int?,
    ): KafkaPageState {
        val runtimeContext = api.loadRuntimeContext()
        val info = api.loadInfo()
        val selectedClusterId = stateSupport.resolveClusterId(info, preferredClusterId)
        if (selectedClusterId == null) {
            return KafkaPageState(
                loading = false,
                runtimeContext = runtimeContext,
                info = info,
                clusterSection = stateSupport.normalizeClusterSection(preferredClusterSection),
                topicQuery = topicQuery,
                activePane = stateSupport.normalizePane(activePane),
                messageReadScope = stateSupport.normalizeMessageScope(preferredMessageReadScope),
                messageReadMode = stateSupport.normalizeMessageMode(preferredMessageReadMode),
            )
        }

        val topics = api.loadTopics(selectedClusterId, topicQuery)
        val selectedTopicName = stateSupport.resolveTopicName(topics, preferredTopicName)
        val topicOverview = selectedTopicName?.let { api.loadTopicOverview(selectedClusterId, it) }
        val resolvedPane = stateSupport.resolveActivePane(
            requestedPane = activePane,
            selectedTopicName = selectedTopicName,
        )
        return KafkaPageState(
            loading = false,
            runtimeContext = runtimeContext,
            info = info,
            selectedClusterId = selectedClusterId,
            clusterSection = stateSupport.normalizeClusterSection(preferredClusterSection),
            topicQuery = topicQuery,
            topics = topics,
            selectedTopicName = selectedTopicName,
            topicOverview = topicOverview,
            activePane = resolvedPane,
            selectedMessagePartition = stateSupport.resolveMessagePartition(
                preferredPartition = preferredMessagePartition,
                topicOverview = topicOverview,
            ),
            messageReadScope = stateSupport.normalizeMessageScope(preferredMessageReadScope),
            messageReadMode = stateSupport.normalizeMessageMode(preferredMessageReadMode),
            messageLimitInput = stateSupport.resolveInitialMessageLimit(info.maxRecordsPerRead),
        )
    }

    fun startLoading(current: KafkaPageState): KafkaPageState =
        current.copy(loading = true, errorMessage = null)

    fun updateTopicQuery(
        current: KafkaPageState,
        query: String,
    ): KafkaPageState =
        current.copy(topicQuery = query, errorMessage = null)

    fun updateActivePane(
        current: KafkaPageState,
        pane: String,
    ): KafkaPageState =
        current.copy(
            activePane = stateSupport.resolveActivePane(pane, current.selectedTopicName),
            errorMessage = null,
            settingsError = null,
            settingsStatusMessage = null,
        )

    fun updateClusterSection(
        current: KafkaPageState,
        section: String,
    ): KafkaPageState =
        current.copy(
            clusterSection = stateSupport.normalizeClusterSection(section),
            activePane = "overview",
            errorMessage = null,
            messagesError = null,
            produceError = null,
            settingsError = null,
            settingsStatusMessage = null,
        )

    fun startTopicsReload(current: KafkaPageState): KafkaPageState =
        current.copy(
            topicsLoading = true,
            errorMessage = null,
            messagesError = null,
            produceError = null,
        )

    suspend fun reloadTopics(current: KafkaPageState): KafkaPageState {
        val clusterId = current.selectedClusterId ?: return current.copy(topicsLoading = false)
        val topics = api.loadTopics(clusterId, current.topicQuery)
        val selectedTopicName = stateSupport.resolveTopicName(topics, current.selectedTopicName)
        val topicOverview = selectedTopicName?.let { api.loadTopicOverview(clusterId, it) }
        return current.copy(
            topicsLoading = false,
            topics = topics,
            selectedTopicName = selectedTopicName,
            topicOverview = topicOverview,
            topicOverviewLoading = false,
            selectedMessagePartition = stateSupport.resolveMessagePartition(
                preferredPartition = current.selectedMessagePartition,
                topicOverview = topicOverview,
            ),
            messages = null,
            produceResult = null,
        )
    }

    fun startTopicOverviewReload(current: KafkaPageState): KafkaPageState =
        current.copy(
            topicOverviewLoading = true,
            errorMessage = null,
            messagesError = null,
            produceError = null,
        )

    suspend fun loadTopicOverview(
        current: KafkaPageState,
        topicName: String,
    ): KafkaPageState {
        val clusterId = current.selectedClusterId ?: return current.copy(topicOverviewLoading = false)
        val overview = api.loadTopicOverview(clusterId, topicName)
        return current.copy(
            topicOverviewLoading = false,
            clusterSection = "topics",
            selectedTopicName = topicName,
            topicOverview = overview,
            selectedMessagePartition = stateSupport.resolveMessagePartition(
                preferredPartition = current.selectedMessagePartition,
                topicOverview = overview,
            ),
            messages = null,
            produceResult = null,
        )
    }
}
