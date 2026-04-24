package com.sbrf.lt.platform.composeui.kafka

import kotlin.test.Test
import kotlin.test.assertEquals

class KafkaStoreMessageSupportTest {
    private val support = KafkaStoreMessageSupport(
        api = StubKafkaApi(
            readMessagesHandler = { error("Should not read messages without selected partition.") },
        ),
        stateSupport = KafkaStoreStateSupport(),
    )

    @Test
    fun `readMessages requires selected partition in selected partition scope`() {
        val current = KafkaPageState(
            selectedClusterId = "local",
            selectedTopicName = "datapool-test",
            messageReadScope = "SELECTED_PARTITION",
            messageReadMode = "LATEST",
            selectedMessagePartition = null,
            messagesLoading = true,
        )

        val updated = runKafkaSuspend { support.readMessages(current) }

        assertEquals(false, updated.messagesLoading)
        assertEquals("Выбери partition для чтения сообщений.", updated.messagesError)
    }
}
