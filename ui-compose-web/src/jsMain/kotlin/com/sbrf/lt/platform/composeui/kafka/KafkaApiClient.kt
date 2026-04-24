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

    override suspend fun loadTopicOverview(
        clusterId: String,
        topicName: String,
    ): KafkaTopicOverviewResponse =
        httpClient.get(
            "/api/kafka/topic-overview?clusterId=${urlEncode(clusterId)}&topic=${urlEncode(topicName)}",
            KafkaTopicOverviewResponse.serializer(),
        )
}
