package com.sbrf.lt.platform.composeui.kafka

internal class KafkaStoreMessageSupport(
    private val api: KafkaApi,
    private val stateSupport: KafkaStoreStateSupport,
) {
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
            messageReadScope = stateSupport.normalizeMessageScope(scope),
            messagesError = null,
            messages = null,
        )

    fun updateMessageReadMode(
        current: KafkaPageState,
        mode: String,
    ): KafkaPageState =
        current.copy(
            messageReadMode = stateSupport.normalizeMessageMode(mode),
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
        val scope = stateSupport.normalizeMessageScope(current.messageReadScope)
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
            mode = stateSupport.normalizeMessageMode(current.messageReadMode),
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
}
