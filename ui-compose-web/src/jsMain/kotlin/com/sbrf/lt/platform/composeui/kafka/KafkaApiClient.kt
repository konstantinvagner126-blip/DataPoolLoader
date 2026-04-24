package com.sbrf.lt.platform.composeui.kafka

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.model.RuntimeContext

class KafkaApiClient(
    private val httpClient: ComposeHttpClient = ComposeHttpClient(),
) : KafkaApi {
    override suspend fun loadRuntimeContext(): RuntimeContext =
        httpClient.get("/api/ui/runtime-context", RuntimeContext.serializer())

    override suspend fun loadInfo(): KafkaToolInfoResponse =
        httpClient.get("/api/kafka/info", KafkaToolInfoResponse.serializer())

    override suspend fun loadTopics(
        clusterId: String,
        query: String,
    ): KafkaTopicsCatalogResponse =
        httpClient.get(
            "/api/kafka/topics?clusterId=${urlEncode(clusterId)}&query=${urlEncode(query)}",
            KafkaTopicsCatalogResponse.serializer(),
        )

    override suspend fun loadConsumerGroups(
        clusterId: String,
    ): KafkaClusterConsumerGroupsCatalogResponse =
        httpClient.get(
            "/api/kafka/consumer-groups?clusterId=${urlEncode(clusterId)}",
            KafkaClusterConsumerGroupsCatalogResponse.serializer(),
        )

    override suspend fun loadBrokers(
        clusterId: String,
    ): KafkaClusterBrokersCatalogResponse =
        httpClient.get(
            "/api/kafka/brokers?clusterId=${urlEncode(clusterId)}",
            KafkaClusterBrokersCatalogResponse.serializer(),
        )

    override suspend fun loadTopicOverview(
        clusterId: String,
        topicName: String,
    ): KafkaTopicOverviewResponse =
        httpClient.get(
            "/api/kafka/topic-overview?clusterId=${urlEncode(clusterId)}&topic=${urlEncode(topicName)}",
            KafkaTopicOverviewResponse.serializer(),
        )

    override suspend fun createTopic(
        request: KafkaTopicCreateRequestPayload,
    ): KafkaTopicCreateResponse =
        httpClient.postJson(
            path = "/api/kafka/topics/create",
            payload = request,
            serializer = KafkaTopicCreateRequestPayload.serializer(),
            deserializer = KafkaTopicCreateResponse.serializer(),
        )

    override suspend fun readMessages(
        request: KafkaTopicMessageReadRequestPayload,
    ): KafkaTopicMessageReadResponse =
        httpClient.postJson(
            path = "/api/kafka/messages/read",
            payload = request,
            serializer = KafkaTopicMessageReadRequestPayload.serializer(),
            deserializer = KafkaTopicMessageReadResponse.serializer(),
        )

    override suspend fun produceMessage(
        request: KafkaTopicProduceRequestPayload,
    ): KafkaTopicProduceResponse =
        httpClient.postJson(
            path = "/api/kafka/messages/produce",
            payload = request,
            serializer = KafkaTopicProduceRequestPayload.serializer(),
            deserializer = KafkaTopicProduceResponse.serializer(),
        )

    override suspend fun loadSettings(): KafkaSettingsResponse =
        httpClient.get("/api/kafka/settings", KafkaSettingsResponse.serializer())

    override suspend fun saveSettings(
        request: KafkaSettingsUpdateRequestPayload,
    ): KafkaSettingsResponse =
        httpClient.postJson(
            path = "/api/kafka/settings",
            payload = request,
            serializer = KafkaSettingsUpdateRequestPayload.serializer(),
            deserializer = KafkaSettingsResponse.serializer(),
        )

    override suspend fun testSettingsConnection(
        request: KafkaSettingsConnectionTestRequestPayload,
    ): KafkaSettingsConnectionTestResponse =
        httpClient.postJson(
            path = "/api/kafka/settings/test-connection",
            payload = request,
            serializer = KafkaSettingsConnectionTestRequestPayload.serializer(),
            deserializer = KafkaSettingsConnectionTestResponse.serializer(),
        )
}
