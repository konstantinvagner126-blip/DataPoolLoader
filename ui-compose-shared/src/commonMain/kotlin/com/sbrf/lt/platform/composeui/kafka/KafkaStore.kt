package com.sbrf.lt.platform.composeui.kafka

class KafkaStore(
    private val api: KafkaApi,
) {
    suspend fun load(
        preferredClusterId: String? = null,
        preferredTopicName: String? = null,
        topicQuery: String = "",
        activePane: String = "overview",
        preferredMessageReadScope: String = "SELECTED_PARTITION",
        preferredMessageReadMode: String = "LATEST",
        preferredMessagePartition: Int? = null,
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
                activePane = normalizePane(activePane),
                messageReadScope = normalizeMessageScope(preferredMessageReadScope),
                messageReadMode = normalizeMessageMode(preferredMessageReadMode),
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
            activePane = normalizePane(activePane),
            selectedMessagePartition = resolveMessagePartition(
                preferredPartition = preferredMessagePartition,
                topicOverview = topicOverview,
            ),
            messageReadScope = normalizeMessageScope(preferredMessageReadScope),
            messageReadMode = normalizeMessageMode(preferredMessageReadMode),
            messageLimitInput = resolveInitialMessageLimit(info.maxRecordsPerRead),
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
            activePane = normalizePane(pane),
            errorMessage = null,
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
        val selectedTopicName = resolveTopicName(topics, current.selectedTopicName)
        val topicOverview = selectedTopicName?.let { api.loadTopicOverview(clusterId, it) }
        return current.copy(
            topicsLoading = false,
            topics = topics,
            selectedTopicName = selectedTopicName,
            topicOverview = topicOverview,
            topicOverviewLoading = false,
            selectedMessagePartition = resolveMessagePartition(
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
            selectedTopicName = topicName,
            topicOverview = overview,
            selectedMessagePartition = resolveMessagePartition(
                preferredPartition = current.selectedMessagePartition,
                topicOverview = overview,
            ),
            messages = null,
            produceResult = null,
        )
    }

    fun updateSelectedMessagePartition(
        current: KafkaPageState,
        partition: Int,
    ): KafkaPageState =
        current.copy(
            selectedMessagePartition = partition,
            messagesError = null,
            messages = null,
        )

    fun updateMessageReadScope(
        current: KafkaPageState,
        scope: String,
    ): KafkaPageState =
        current.copy(
            messageReadScope = normalizeMessageScope(scope),
            messagesError = null,
            messages = null,
        )

    fun updateMessageReadMode(
        current: KafkaPageState,
        mode: String,
    ): KafkaPageState =
        current.copy(
            messageReadMode = normalizeMessageMode(mode),
            messagesError = null,
            messages = null,
        )

    fun updateMessageLimitInput(
        current: KafkaPageState,
        limit: String,
    ): KafkaPageState =
        current.copy(
            messageLimitInput = limit,
            messagesError = null,
            messages = null,
        )

    fun updateMessageOffsetInput(
        current: KafkaPageState,
        offset: String,
    ): KafkaPageState =
        current.copy(
            messageOffsetInput = offset,
            messagesError = null,
            messages = null,
        )

    fun updateMessageTimestampInput(
        current: KafkaPageState,
        timestamp: String,
    ): KafkaPageState =
        current.copy(
            messageTimestampInput = timestamp,
            messagesError = null,
            messages = null,
        )

    fun startMessagesReload(current: KafkaPageState): KafkaPageState =
        current.copy(messagesLoading = true, messagesError = null)

    suspend fun readMessages(current: KafkaPageState): KafkaPageState {
        val clusterId = current.selectedClusterId ?: return current.copy(messagesLoading = false)
        val topicName = current.selectedTopicName ?: return current.copy(messagesLoading = false)
        val scope = normalizeMessageScope(current.messageReadScope)
        val partition = when (scope) {
            "ALL_PARTITIONS" -> null
            else -> current.selectedMessagePartition
                ?: return current.copy(messagesLoading = false, messagesError = "Выбери partition для чтения сообщений.")
        }
        val request = KafkaTopicMessageReadRequestPayload(
            clusterId = clusterId,
            topicName = topicName,
            scope = scope,
            partition = partition,
            mode = normalizeMessageMode(current.messageReadMode),
            limit = current.messageLimitInput.trim().takeIf { it.isNotEmpty() }?.toIntOrNull(),
            offset = current.messageOffsetInput.trim().takeIf { it.isNotEmpty() }?.toLongOrNull(),
            timestampMs = current.messageTimestampInput.trim().takeIf { it.isNotEmpty() }?.toLongOrNull(),
        )
        val messages = api.readMessages(request)
        return current.copy(
            messagesLoading = false,
            messages = messages,
        )
    }

    fun updateProducePartitionInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        current.copy(
            producePartitionInput = value,
            produceError = null,
            produceResult = null,
        )

    fun updateProduceKeyInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        current.copy(
            produceKeyInput = value,
            produceError = null,
            produceResult = null,
        )

    fun updateProduceHeadersInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        current.copy(
            produceHeadersInput = value,
            produceError = null,
            produceResult = null,
        )

    fun updateProducePayloadInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        current.copy(
            producePayloadInput = value,
            produceError = null,
            produceResult = null,
        )

    fun startProduce(current: KafkaPageState): KafkaPageState =
        current.copy(produceLoading = true, produceError = null, produceResult = null)

    suspend fun produceMessage(current: KafkaPageState): KafkaPageState {
        val clusterId = current.selectedClusterId ?: return current.copy(produceLoading = false)
        val topicName = current.selectedTopicName ?: return current.copy(produceLoading = false)
        val request = KafkaTopicProduceRequestPayload(
            clusterId = clusterId,
            topicName = topicName,
            partition = current.producePartitionInput.trim().takeIf { it.isNotEmpty() }?.toIntOrNull(),
            keyText = current.produceKeyInput.takeIf { it.isNotBlank() },
            payloadText = current.producePayloadInput,
            headers = parseProduceHeaders(current.produceHeadersInput),
        )
        val result = api.produceMessage(request)
        return current.copy(
            produceLoading = false,
            produceResult = result,
        )
    }

    fun startSettingsReload(current: KafkaPageState): KafkaPageState =
        current.copy(
            settingsLoading = true,
            settingsError = null,
            settingsStatusMessage = null,
        )

    suspend fun loadSettings(current: KafkaPageState): KafkaPageState {
        val settings = api.loadSettings()
        return current.copy(
            settingsLoading = false,
            settingsError = null,
            settings = settings,
            settingsStatusMessage = null,
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
        )
    }

    fun addSettingsCluster(current: KafkaPageState): KafkaPageState {
        val settings = current.settings ?: KafkaSettingsResponse()
        return current.copy(
            settings = settings.copy(
                clusters = settings.clusters + KafkaEditableClusterResponse(
                    id = "",
                    name = "",
                    readOnly = true,
                ),
            ),
            settingsError = null,
            settingsStatusMessage = null,
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
        )
    }

    fun removeSettingsCluster(
        current: KafkaPageState,
        clusterIndex: Int,
    ): KafkaPageState {
        val settings = current.settings ?: return current
        if (clusterIndex !in settings.clusters.indices) {
            return current
        }
        return current.copy(
            settings = settings.copy(
                clusters = settings.clusters.filterIndexed { index, _ -> index != clusterIndex },
            ),
            settingsError = null,
            settingsStatusMessage = null,
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
        )
    }

    fun updateSettingsCluster(
        current: KafkaPageState,
        clusterIndex: Int,
        transform: (KafkaEditableClusterResponse) -> KafkaEditableClusterResponse,
    ): KafkaPageState {
        val settings = current.settings ?: return current
        if (clusterIndex !in settings.clusters.indices) {
            return current
        }
        val updatedClusters = settings.clusters.mapIndexed { index, cluster ->
            if (index == clusterIndex) transform(cluster) else cluster
        }
        return current.copy(
            settings = settings.copy(clusters = updatedClusters),
            settingsError = null,
            settingsStatusMessage = null,
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
        )
    }

    fun startSettingsConnectionTest(current: KafkaPageState): KafkaPageState =
        current.copy(
            settingsLoading = true,
            settingsError = null,
            settingsStatusMessage = null,
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
        )

    suspend fun testSettingsConnection(
        current: KafkaPageState,
        clusterIndex: Int,
    ): KafkaPageState {
        val settings = current.settings ?: return current.copy(settingsLoading = false)
        val cluster = settings.clusters.getOrNull(clusterIndex) ?: return current.copy(settingsLoading = false)
        val result = api.testSettingsConnection(
            KafkaSettingsConnectionTestRequestPayload(
                cluster = cluster.toRequestPayload(),
            ),
        )
        return current.copy(
            settingsLoading = false,
            settingsConnectionTestClusterIndex = clusterIndex,
            settingsConnectionResult = result,
            settingsStatusMessage = null,
        )
    }

    fun startSettingsSave(current: KafkaPageState): KafkaPageState =
        current.copy(
            settingsLoading = true,
            settingsError = null,
            settingsStatusMessage = null,
        )

    suspend fun saveSettings(current: KafkaPageState): KafkaPageState {
        val settings = current.settings ?: return current.copy(settingsLoading = false)
        val saved = api.saveSettings(
            KafkaSettingsUpdateRequestPayload(
                clusters = settings.clusters.map { it.toRequestPayload() },
            ),
        )
        return current.copy(
            settingsLoading = false,
            settings = saved,
            settingsStatusMessage = "Настройки Kafka сохранены.",
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
        )
    }

    private fun parseProduceHeaders(rawHeaders: String): List<KafkaTopicProduceHeaderRequestPayload> =
        rawHeaders
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, line ->
                val separatorIndex = line.indexOf('=')
                require(separatorIndex > 0) {
                    "Kafka headers должны задаваться как name=value. Ошибка в строке ${index + 1}."
                }
                KafkaTopicProduceHeaderRequestPayload(
                    name = line.substring(0, separatorIndex).trim(),
                    valueText = line.substring(separatorIndex + 1),
                )
            }
            .toList()

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

    private fun resolveMessagePartition(
        preferredPartition: Int?,
        topicOverview: KafkaTopicOverviewResponse?,
    ): Int? {
        val partitions = topicOverview?.partitions.orEmpty()
        if (partitions.isEmpty()) {
            return null
        }
        return partitions.firstOrNull { it.partition == preferredPartition }?.partition ?: partitions.first().partition
    }

    private fun resolveInitialMessageLimit(maxRecordsPerRead: Int): String =
        minOf(50, maxRecordsPerRead).toString()

    private fun normalizePane(value: String): String =
        value.trim().uppercase().let { normalized ->
            when (normalized) {
                "MESSAGES" -> "messages"
                "PRODUCE" -> "produce"
                "SETTINGS" -> "settings"
                else -> "overview"
            }
        }

    private fun normalizeMessageScope(value: String): String =
        when (value.trim().uppercase()) {
            "ALL_PARTITIONS" -> "ALL_PARTITIONS"
            else -> "SELECTED_PARTITION"
        }

    private fun normalizeMessageMode(value: String): String =
        when (value.trim().uppercase()) {
            "OFFSET" -> "OFFSET"
            "TIMESTAMP" -> "TIMESTAMP"
            else -> "LATEST"
        }

    private fun KafkaEditableClusterResponse.toRequestPayload(): KafkaEditableClusterRequestPayload =
        KafkaEditableClusterRequestPayload(
            id = id,
            name = name,
            readOnly = readOnly,
            bootstrapServers = bootstrapServers,
            clientId = clientId,
            securityProtocol = securityProtocol,
            truststoreType = truststoreType,
            truststoreLocation = truststoreLocation,
            truststoreCertificates = truststoreCertificates,
            keystoreType = keystoreType,
            keystoreLocation = keystoreLocation,
            keystoreCertificateChain = keystoreCertificateChain,
            keystoreKey = keystoreKey,
            keyPassword = keyPassword,
            additionalProperties = additionalProperties,
        )
}
