package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

@Composable
internal fun KafkaMessageBrowserSection(
    state: KafkaPageState,
    onMessagePartitionChange: (Int) -> Unit,
    onMessageReadScopeChange: (String) -> Unit,
    onMessageReadModeChange: (String) -> Unit,
    onMessageLimitChange: (String) -> Unit,
    onMessageOffsetChange: (String) -> Unit,
    onMessageTimestampChange: (String) -> Unit,
    onReadMessages: () -> Unit,
) {
    val topicOverview = state.topicOverview ?: return
    val partitions = topicOverview.partitions
    if (partitions.isEmpty()) {
        return
    }
    SectionCard(
        title = "Сообщения",
        subtitle = "Bounded чтение через assign + seek без commit offsets и без background consumer session.",
    ) {
        state.messagesError?.let { message ->
            AlertBanner(message, "warning")
        }
        Div({ classes("kafka-message-controls") }) {
            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Scope") }
                Select(attrs = {
                    classes("form-select")
                    onChange { onMessageReadScopeChange(it.value.orEmpty()) }
                }) {
                    Option(value = "SELECTED_PARTITION", attrs = { if (state.messageReadScope == "SELECTED_PARTITION") selected() }) {
                        Text("Selected partition")
                    }
                    Option(value = "ALL_PARTITIONS", attrs = { if (state.messageReadScope == "ALL_PARTITIONS") selected() }) {
                        Text("All partitions")
                    }
                }
            }

            if (state.messageReadScope == "SELECTED_PARTITION") {
                Div({ classes("kafka-message-control") }) {
                    P({ classes("kafka-message-control-label") }) { Text("Partition") }
                    Select(attrs = {
                        classes("form-select")
                        onChange { event -> event.value.orEmpty().toIntOrNull()?.let(onMessagePartitionChange) }
                    }) {
                        partitions.forEach { partition ->
                            Option(
                                value = partition.partition.toString(),
                                attrs = { if (partition.partition == state.selectedMessagePartition) selected() },
                            ) {
                                Text(partition.partition.toString())
                            }
                        }
                    }
                }
            }

            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Mode") }
                Select(attrs = {
                    classes("form-select")
                    onChange { onMessageReadModeChange(it.value.orEmpty()) }
                }) {
                    Option(value = "LATEST", attrs = { if (state.messageReadMode == "LATEST") selected() }) { Text("Latest records") }
                    Option(value = "OFFSET", attrs = { if (state.messageReadMode == "OFFSET") selected() }) { Text("From explicit offset") }
                    Option(value = "TIMESTAMP", attrs = { if (state.messageReadMode == "TIMESTAMP") selected() }) { Text("From timestamp") }
                }
            }

            Div({ classes("kafka-message-control", "kafka-message-limit-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Limit") }
                Input(type = InputType.Number, attrs = {
                    classes("form-control")
                    value(state.messageLimitInput)
                    onInput { onMessageLimitChange(it.value?.toString().orEmpty()) }
                })
            }

            if (state.messageReadMode == "OFFSET") {
                Div({ classes("kafka-message-control", "kafka-message-cursor-control") }) {
                    P({ classes("kafka-message-control-label") }) { Text("Offset") }
                    Input(type = InputType.Number, attrs = {
                        classes("form-control")
                        value(state.messageOffsetInput)
                        onInput { onMessageOffsetChange(it.value?.toString().orEmpty()) }
                    })
                }
            }

            if (state.messageReadMode == "TIMESTAMP") {
                Div({ classes("kafka-message-control", "kafka-message-cursor-control") }) {
                    P({ classes("kafka-message-control-label") }) { Text("Timestamp ms") }
                    Input(type = InputType.Number, attrs = {
                        classes("form-control")
                        value(state.messageTimestampInput)
                        onInput { onMessageTimestampChange(it.value?.toString().orEmpty()) }
                    })
                }
            }

            Div({ classes("kafka-message-action") }) {
                Button(attrs = {
                    classes("btn", "btn-dark")
                    attr("type", "button")
                    if (state.messagesLoading) disabled()
                    onClick { onReadMessages() }
                }) {
                    Text(if (state.messagesLoading) "Читаю..." else "Читать сообщения")
                }
            }
        }

        state.messages?.takeIf {
            it.topicName == topicOverview.topic.name &&
                it.scope == state.messageReadScope &&
                (it.scope == "ALL_PARTITIONS" || it.partition == state.selectedMessagePartition) &&
                it.mode == state.messageReadMode
        }?.let { messages ->
            messages.note?.let { note ->
                P({ classes("kafka-message-note") }) { Text(note) }
            }

            if (messages.records.isEmpty()) {
                P({ classes("text-secondary", "small", "mb-0") }) {
                    Text("Для выбранного диапазона сообщений не найдено.")
                }
            } else {
                Div({ classes("kafka-message-list") }) {
                    messages.records.forEach { record ->
                        KafkaMessageRecordCard(record)
                    }
                }
            }
        }
    }
}

@Composable
internal fun KafkaMessageRecordCard(
    record: KafkaTopicMessageRecordResponse,
) {
    Div({ classes("kafka-message-card") }) {
        Div({ classes("kafka-message-card-meta") }) {
            Text(
                buildList {
                    add("partition ${record.partition}")
                    add("offset ${record.offset}")
                    add("timestamp ${record.timestamp?.toString() ?: "n/a"}")
                    add("headers ${record.headers.size}")
                }.joinToString(" · "),
            )
        }
        record.key?.let { payload ->
            KafkaRenderedBytesBlock(
                title = "Key",
                payload = payload,
            )
        }
        record.value?.let { payload ->
            KafkaRenderedBytesBlock(
                title = "Value",
                payload = payload,
            )
        }
        if (record.headers.isNotEmpty()) {
            Div({ classes("kafka-message-headers") }) {
                P({ classes("kafka-message-section-title") }) { Text("Headers") }
                record.headers.forEach { header ->
                    Div({ classes("kafka-message-header-row") }) {
                        Span({ classes("kafka-message-header-name") }) { Text(header.name) }
                        Span({ classes("kafka-message-header-value") }) {
                            Text(header.value?.jsonPrettyText ?: header.value?.text ?: "null")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KafkaRenderedBytesBlock(
    title: String,
    payload: KafkaRenderedBytesResponse,
) {
    Div({ classes("kafka-message-payload") }) {
        P({ classes("kafka-message-section-title") }) {
            Text(
                buildList {
                    add(title)
                    add("${payload.sizeBytes} B")
                    if (payload.truncated) {
                        add("truncated")
                    }
                }.joinToString(" · "),
            )
        }
        Pre({ classes("kafka-message-payload-body") }) {
            Text(payload.jsonPrettyText ?: payload.text ?: "")
        }
    }
}
