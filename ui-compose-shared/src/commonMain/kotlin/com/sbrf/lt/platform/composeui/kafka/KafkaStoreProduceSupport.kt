package com.sbrf.lt.platform.composeui.kafka

internal class KafkaStoreProduceSupport(
    private val api: KafkaApi,
) {
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
}
