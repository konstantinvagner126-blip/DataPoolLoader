package com.sbrf.lt.platform.composeui.kafka

import com.sbrf.lt.platform.composeui.model.DatabaseConnectionStatus
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeActorState
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal fun sampleKafkaRuntimeContext(): RuntimeContext =
    RuntimeContext(
        requestedMode = ModuleStoreMode.FILES,
        effectiveMode = ModuleStoreMode.FILES,
        actor = RuntimeActorState(
            resolved = true,
            message = "ok",
        ),
        database = DatabaseConnectionStatus(
            configured = true,
            available = true,
            schema = "public",
            message = "ok",
        ),
    )

internal fun sampleKafkaInfo(): KafkaToolInfoResponse =
    KafkaToolInfoResponse(
        configured = true,
        maxRecordsPerRead = 100,
        maxPayloadBytes = 1_048_576,
        clusters = listOf(
            KafkaClusterCatalogEntryResponse(
                id = "local",
                name = "Local Kafka",
                readOnly = false,
                bootstrapServers = "localhost:19092",
                securityProtocol = "PLAINTEXT",
            ),
        ),
    )

internal fun sampleKafkaTopics(query: String = ""): KafkaTopicsCatalogResponse =
    KafkaTopicsCatalogResponse(
        clusterId = "local",
        query = query,
        topics = listOf(
            KafkaTopicSummaryResponse(
                name = "datapool-test",
                internal = false,
                partitionCount = 2,
                replicationFactor = 1,
                cleanupPolicy = "delete",
                retentionMs = 60_000,
            ),
        ),
    )

internal fun sampleKafkaOverview(): KafkaTopicOverviewResponse =
    KafkaTopicOverviewResponse(
        cluster = sampleKafkaInfo().clusters.first(),
        topic = sampleKafkaTopics().topics.first(),
        partitions = listOf(
            KafkaTopicPartitionSummaryResponse(
                partition = 0,
                leaderId = 1,
                replicaCount = 1,
                inSyncReplicaCount = 1,
                earliestOffset = 0,
                latestOffset = 12,
            ),
            KafkaTopicPartitionSummaryResponse(
                partition = 1,
                leaderId = 1,
                replicaCount = 1,
                inSyncReplicaCount = 1,
                earliestOffset = 4,
                latestOffset = 27,
            ),
        ),
    )

internal fun sampleKafkaConsumerGroupsCatalog(): KafkaClusterConsumerGroupsCatalogResponse =
    KafkaClusterConsumerGroupsCatalogResponse(
        cluster = sampleKafkaInfo().clusters.first(),
        status = "AVAILABLE",
        groups = listOf(
            KafkaClusterConsumerGroupSummaryResponse(
                groupId = "datapool-test-group",
                state = "STABLE",
                memberCount = 2,
                totalLag = 9,
                lagStatus = "OK",
                topics = listOf(
                    KafkaClusterConsumerGroupTopicSummaryResponse(
                        topicName = "datapool-test",
                        partitionCount = 2,
                        totalLag = 9,
                        partitions = listOf(
                            KafkaTopicConsumerGroupPartitionLagResponse(
                                partition = 0,
                                committedOffset = 10,
                                latestOffset = 12,
                                lag = 2,
                            ),
                            KafkaTopicConsumerGroupPartitionLagResponse(
                                partition = 1,
                                committedOffset = 20,
                                latestOffset = 27,
                                lag = 7,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

internal fun sampleKafkaBrokersCatalog(): KafkaClusterBrokersCatalogResponse =
    KafkaClusterBrokersCatalogResponse(
        cluster = sampleKafkaInfo().clusters.first(),
        controllerBrokerId = 1,
        brokers = listOf(
            KafkaBrokerSummaryResponse(
                brokerId = 1,
                host = "broker-1.local",
                port = 19092,
                rack = "rack-a",
                controller = true,
            ),
            KafkaBrokerSummaryResponse(
                brokerId = 2,
                host = "broker-2.local",
                port = 19092,
                rack = null,
                controller = false,
            ),
        ),
    )

internal class StubKafkaApi(
    private val runtimeContextHandler: suspend () -> RuntimeContext = { sampleKafkaRuntimeContext() },
    private val infoHandler: suspend () -> KafkaToolInfoResponse = { sampleKafkaInfo() },
    private val loadTopicsHandler: suspend (String, String) -> KafkaTopicsCatalogResponse = { _, query ->
        sampleKafkaTopics(query)
    },
    private val loadConsumerGroupsHandler: suspend (String) -> KafkaClusterConsumerGroupsCatalogResponse = {
        sampleKafkaConsumerGroupsCatalog()
    },
    private val loadBrokersHandler: suspend (String) -> KafkaClusterBrokersCatalogResponse = {
        sampleKafkaBrokersCatalog()
    },
    private val loadTopicOverviewHandler: suspend (String, String) -> KafkaTopicOverviewResponse = { _, _ ->
        sampleKafkaOverview()
    },
    private val createTopicHandler: suspend (KafkaTopicCreateRequestPayload) -> KafkaTopicCreateResponse = {
        error("createTopic not configured")
    },
    private val readMessagesHandler: suspend (KafkaTopicMessageReadRequestPayload) -> KafkaTopicMessageReadResponse = {
        error("readMessages not configured")
    },
    private val produceMessageHandler: suspend (KafkaTopicProduceRequestPayload) -> KafkaTopicProduceResponse = {
        error("produceMessage not configured")
    },
    private val loadSettingsHandler: suspend () -> KafkaSettingsResponse = { KafkaSettingsResponse() },
    private val saveSettingsHandler: suspend (KafkaSettingsUpdateRequestPayload) -> KafkaSettingsResponse = {
        error("saveSettings not configured")
    },
    private val testSettingsConnectionHandler: suspend (KafkaSettingsConnectionTestRequestPayload) -> KafkaSettingsConnectionTestResponse = {
        error("testSettingsConnection not configured")
    },
    private val pickSettingsFileHandler: suspend (KafkaSettingsFilePickRequestPayload) -> KafkaSettingsFilePickResponse = {
        error("pickSettingsFile not configured")
    },
) : KafkaApi {
    override suspend fun loadRuntimeContext(): RuntimeContext = runtimeContextHandler()

    override suspend fun loadInfo(): KafkaToolInfoResponse = infoHandler()

    override suspend fun loadTopics(
        clusterId: String,
        query: String,
    ): KafkaTopicsCatalogResponse = loadTopicsHandler(clusterId, query)

    override suspend fun loadConsumerGroups(
        clusterId: String,
    ): KafkaClusterConsumerGroupsCatalogResponse = loadConsumerGroupsHandler(clusterId)

    override suspend fun loadBrokers(
        clusterId: String,
    ): KafkaClusterBrokersCatalogResponse = loadBrokersHandler(clusterId)

    override suspend fun loadTopicOverview(
        clusterId: String,
        topicName: String,
    ): KafkaTopicOverviewResponse = loadTopicOverviewHandler(clusterId, topicName)

    override suspend fun createTopic(
        request: KafkaTopicCreateRequestPayload,
    ): KafkaTopicCreateResponse = createTopicHandler(request)

    override suspend fun readMessages(
        request: KafkaTopicMessageReadRequestPayload,
    ): KafkaTopicMessageReadResponse = readMessagesHandler(request)

    override suspend fun produceMessage(
        request: KafkaTopicProduceRequestPayload,
    ): KafkaTopicProduceResponse = produceMessageHandler(request)

    override suspend fun loadSettings(): KafkaSettingsResponse = loadSettingsHandler()

    override suspend fun saveSettings(
        request: KafkaSettingsUpdateRequestPayload,
    ): KafkaSettingsResponse = saveSettingsHandler(request)

    override suspend fun testSettingsConnection(
        request: KafkaSettingsConnectionTestRequestPayload,
    ): KafkaSettingsConnectionTestResponse = testSettingsConnectionHandler(request)

    override suspend fun pickSettingsFile(
        request: KafkaSettingsFilePickRequestPayload,
    ): KafkaSettingsFilePickResponse = pickSettingsFileHandler(request)
}

internal fun <T> runKafkaSuspend(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                completed = result
            }
        },
    )
    return completed!!.getOrThrow()
}
