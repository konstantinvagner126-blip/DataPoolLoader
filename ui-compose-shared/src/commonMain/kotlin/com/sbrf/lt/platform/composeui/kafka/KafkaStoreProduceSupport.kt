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

    fun addProduceHeader(
        current: KafkaPageState,
    ): KafkaPageState =
        current.copy(
            produceHeaders = current.produceHeaders + KafkaKeyValueDraft(),
            produceError = null,
            produceResult = null,
        )

    fun removeProduceHeader(
        current: KafkaPageState,
        index: Int,
    ): KafkaPageState =
        current.copy(
            produceHeaders = current.produceHeaders.filterIndexed { currentIndex, _ -> currentIndex != index },
            produceError = null,
            produceResult = null,
        )

    fun updateProduceHeaderName(
        current: KafkaPageState,
        index: Int,
        value: String,
    ): KafkaPageState =
        current.copy(
            produceHeaders = current.produceHeaders.mapIndexed { currentIndex, header ->
                if (currentIndex == index) header.copy(name = value) else header
            },
            produceError = null,
            produceResult = null,
        )

    fun updateProduceHeaderValue(
        current: KafkaPageState,
        index: Int,
        value: String,
    ): KafkaPageState =
        current.copy(
            produceHeaders = current.produceHeaders.mapIndexed { currentIndex, header ->
                if (currentIndex == index) header.copy(value = value) else header
            },
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
            headers = current.produceHeaders
                .filter { it.name.isNotBlank() || it.value.isNotBlank() }
                .mapIndexed { index, header ->
                    require(header.name.isNotBlank()) {
                        "Kafka header name не должен быть пустым. Ошибка в строке ${index + 1}."
                    }
                    KafkaTopicProduceHeaderRequestPayload(
                        name = header.name.trim(),
                        valueText = header.value,
                    )
                },
        )
        val result = api.produceMessage(request)
        return current.copy(
            produceLoading = false,
            produceResult = result,
        )
    }
}
