package com.sbrf.lt.platform.composeui.kafka

import com.sbrf.lt.platform.composeui.model.RuntimeContext

interface KafkaApi {
    suspend fun loadRuntimeContext(): RuntimeContext

    suspend fun loadInfo(): KafkaToolInfoResponse

    suspend fun loadTopics(
        clusterId: String,
        query: String = "",
    ): KafkaTopicsCatalogResponse

    suspend fun loadTopicOverview(
        clusterId: String,
        topicName: String,
    ): KafkaTopicOverviewResponse

    suspend fun readMessages(
        request: KafkaTopicMessageReadRequestPayload,
    ): KafkaTopicMessageReadResponse

    suspend fun produceMessage(
        request: KafkaTopicProduceRequestPayload,
    ): KafkaTopicProduceResponse

    suspend fun loadSettings(): KafkaSettingsResponse

    suspend fun saveSettings(
        request: KafkaSettingsUpdateRequestPayload,
    ): KafkaSettingsResponse

    suspend fun testSettingsConnection(
        request: KafkaSettingsConnectionTestRequestPayload,
    ): KafkaSettingsConnectionTestResponse
}
