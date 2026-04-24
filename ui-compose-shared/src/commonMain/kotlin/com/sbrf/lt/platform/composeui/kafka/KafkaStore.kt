package com.sbrf.lt.platform.composeui.kafka

class KafkaStore(
    api: KafkaApi,
) {
    private val stateSupport = KafkaStoreStateSupport()
    private val loadingSupport = KafkaStoreLoadingSupport(api, stateSupport)
    private val messageSupport = KafkaStoreMessageSupport(api, stateSupport)
    private val topicAdminSupport = KafkaStoreTopicAdminSupport(api, stateSupport)
    private val produceSupport = KafkaStoreProduceSupport(api)
    private val settingsSupport = KafkaStoreSettingsSupport(api)

    suspend fun load(
        preferredClusterId: String? = null,
        preferredClusterSection: String = "topics",
        preferredTopicName: String? = null,
        topicQuery: String = "",
        activePane: String = "overview",
        preferredMessageReadScope: String = "SELECTED_PARTITION",
        preferredMessageReadMode: String = "LATEST",
        preferredMessagePartition: Int? = null,
    ): KafkaPageState =
        loadingSupport.load(
            preferredClusterId = preferredClusterId,
            preferredClusterSection = preferredClusterSection,
            preferredTopicName = preferredTopicName,
            topicQuery = topicQuery,
            activePane = activePane,
            preferredMessageReadScope = preferredMessageReadScope,
            preferredMessageReadMode = preferredMessageReadMode,
            preferredMessagePartition = preferredMessagePartition,
        )

    fun startLoading(current: KafkaPageState): KafkaPageState =
        loadingSupport.startLoading(current)

    fun updateTopicQuery(
        current: KafkaPageState,
        query: String,
    ): KafkaPageState =
        loadingSupport.updateTopicQuery(current, query)

    fun updateActivePane(
        current: KafkaPageState,
        pane: String,
    ): KafkaPageState =
        loadingSupport.updateActivePane(current, pane)

    fun updateClusterSection(
        current: KafkaPageState,
        section: String,
    ): KafkaPageState =
        loadingSupport.updateClusterSection(current, section)

    fun startConsumerGroupsReload(current: KafkaPageState): KafkaPageState =
        loadingSupport.startConsumerGroupsReload(current)

    suspend fun loadConsumerGroups(current: KafkaPageState): KafkaPageState =
        loadingSupport.loadConsumerGroups(current)

    fun startBrokersReload(current: KafkaPageState): KafkaPageState =
        loadingSupport.startBrokersReload(current)

    suspend fun loadBrokers(current: KafkaPageState): KafkaPageState =
        loadingSupport.loadBrokers(current)

    fun startTopicsReload(current: KafkaPageState): KafkaPageState =
        loadingSupport.startTopicsReload(current)

    suspend fun reloadTopics(current: KafkaPageState): KafkaPageState =
        loadingSupport.reloadTopics(current)

    fun startTopicOverviewReload(current: KafkaPageState): KafkaPageState =
        loadingSupport.startTopicOverviewReload(current)

    suspend fun loadTopicOverview(
        current: KafkaPageState,
        topicName: String,
    ): KafkaPageState =
        loadingSupport.loadTopicOverview(current, topicName)

    fun toggleCreateTopicForm(current: KafkaPageState): KafkaPageState =
        topicAdminSupport.toggleCreateTopicForm(current)

    fun updateCreateTopicNameInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        topicAdminSupport.updateCreateTopicNameInput(current, value)

    fun updateCreateTopicPartitionsInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        topicAdminSupport.updateCreateTopicPartitionsInput(current, value)

    fun updateCreateTopicReplicationFactorInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        topicAdminSupport.updateCreateTopicReplicationFactorInput(current, value)

    fun updateCreateTopicCleanupPolicyInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        topicAdminSupport.updateCreateTopicCleanupPolicyInput(current, value)

    fun updateCreateTopicRetentionMsInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        topicAdminSupport.updateCreateTopicRetentionMsInput(current, value)

    fun updateCreateTopicRetentionBytesInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        topicAdminSupport.updateCreateTopicRetentionBytesInput(current, value)

    fun startCreateTopic(current: KafkaPageState): KafkaPageState =
        topicAdminSupport.startCreateTopic(current)

    suspend fun createTopic(current: KafkaPageState): KafkaPageState =
        topicAdminSupport.createTopic(current)

    fun updateSelectedMessagePartition(
        current: KafkaPageState,
        partition: Int,
    ): KafkaPageState =
        messageSupport.updateSelectedMessagePartition(current, partition)

    fun updateMessageReadScope(
        current: KafkaPageState,
        scope: String,
    ): KafkaPageState =
        messageSupport.updateMessageReadScope(current, scope)

    fun updateMessageReadMode(
        current: KafkaPageState,
        mode: String,
    ): KafkaPageState =
        messageSupport.updateMessageReadMode(current, mode)

    fun updateMessageLimitInput(
        current: KafkaPageState,
        limit: String,
    ): KafkaPageState =
        messageSupport.updateMessageLimitInput(current, limit)

    fun updateMessageOffsetInput(
        current: KafkaPageState,
        offset: String,
    ): KafkaPageState =
        messageSupport.updateMessageOffsetInput(current, offset)

    fun updateMessageTimestampInput(
        current: KafkaPageState,
        timestamp: String,
    ): KafkaPageState =
        messageSupport.updateMessageTimestampInput(current, timestamp)

    fun startMessagesReload(current: KafkaPageState): KafkaPageState =
        messageSupport.startMessagesReload(current)

    suspend fun readMessages(current: KafkaPageState): KafkaPageState =
        messageSupport.readMessages(current)

    fun updateProducePartitionInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        produceSupport.updateProducePartitionInput(current, value)

    fun updateProduceKeyInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        produceSupport.updateProduceKeyInput(current, value)

    fun addProduceHeader(current: KafkaPageState): KafkaPageState =
        produceSupport.addProduceHeader(current)

    fun removeProduceHeader(
        current: KafkaPageState,
        index: Int,
    ): KafkaPageState =
        produceSupport.removeProduceHeader(current, index)

    fun updateProduceHeaderName(
        current: KafkaPageState,
        index: Int,
        value: String,
    ): KafkaPageState =
        produceSupport.updateProduceHeaderName(current, index, value)

    fun updateProduceHeaderValue(
        current: KafkaPageState,
        index: Int,
        value: String,
    ): KafkaPageState =
        produceSupport.updateProduceHeaderValue(current, index, value)

    fun updateProducePayloadInput(
        current: KafkaPageState,
        value: String,
    ): KafkaPageState =
        produceSupport.updateProducePayloadInput(current, value)

    fun startProduce(current: KafkaPageState): KafkaPageState =
        produceSupport.startProduce(current)

    suspend fun produceMessage(current: KafkaPageState): KafkaPageState =
        produceSupport.produceMessage(current)

    fun startSettingsReload(current: KafkaPageState): KafkaPageState =
        settingsSupport.startSettingsReload(current)

    suspend fun loadSettings(current: KafkaPageState): KafkaPageState =
        settingsSupport.loadSettings(current)

    fun addSettingsCluster(current: KafkaPageState): KafkaPageState =
        settingsSupport.addSettingsCluster(current)

    fun removeSettingsCluster(
        current: KafkaPageState,
        clusterIndex: Int,
    ): KafkaPageState =
        settingsSupport.removeSettingsCluster(current, clusterIndex)

    fun updateSettingsCluster(
        current: KafkaPageState,
        clusterIndex: Int,
        transform: (KafkaEditableClusterResponse) -> KafkaEditableClusterResponse,
    ): KafkaPageState =
        settingsSupport.updateSettingsCluster(current, clusterIndex, transform)

    fun startSettingsConnectionTest(current: KafkaPageState): KafkaPageState =
        settingsSupport.startSettingsConnectionTest(current)

    suspend fun testSettingsConnection(
        current: KafkaPageState,
        clusterIndex: Int,
    ): KafkaPageState =
        settingsSupport.testSettingsConnection(current, clusterIndex)

    fun startSettingsFilePick(
        current: KafkaPageState,
        clusterIndex: Int,
        targetProperty: String,
    ): KafkaPageState =
        settingsSupport.startSettingsFilePick(current, clusterIndex, targetProperty)

    suspend fun pickSettingsFile(
        current: KafkaPageState,
        clusterIndex: Int,
        targetProperty: String,
    ): KafkaPageState =
        settingsSupport.pickSettingsFile(current, clusterIndex, targetProperty)

    fun startSettingsSave(current: KafkaPageState): KafkaPageState =
        settingsSupport.startSettingsSave(current)

    suspend fun saveSettings(current: KafkaPageState): KafkaPageState =
        settingsSupport.saveSettings(current)
}
