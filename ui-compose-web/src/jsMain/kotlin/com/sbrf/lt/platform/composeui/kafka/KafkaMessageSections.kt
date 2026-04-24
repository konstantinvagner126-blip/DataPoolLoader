package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

internal const val KafkaMessageInspectorTabValue = "VALUE"
internal const val KafkaMessageInspectorTabKey = "KEY"
internal const val KafkaMessageInspectorTabHeaders = "HEADERS"
internal const val KafkaMessageInspectorTabMetadata = "METADATA"
internal const val KafkaMessagePayloadModePretty = "PRETTY"
internal const val KafkaMessagePayloadModeRaw = "RAW"

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
    Div({ classes("kafka-message-panel") }) {
        Div({ classes("kafka-message-pane-head") }) {
            Div {
                Div({ classes("kafka-message-pane-title") }) { Text("Messages") }
                P({ classes("kafka-message-pane-subtitle") }) {
                    Text("Bounded read via assign + seek. Controls are draft until explicit read.")
                }
            }
            Span({ classes("kafka-tool-chip", "warn") }) { Text("no commit") }
        }

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
                    classes("btn", "btn-dark", "kafka-message-read-button")
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
                it.cluster.id == topicOverview.cluster.id
        }?.let { messages ->
            messages.note?.let { note ->
                P({ classes("kafka-message-note") }) { Text(note) }
            }

            KafkaMessageResultMeta(messages)

            if (messages.records.isEmpty()) {
                P({ classes("text-secondary", "small", "mb-0") }) {
                    Text("Для выбранного диапазона сообщений не найдено.")
                }
            } else {
                val recordsKey = messages.records.joinToString("|") { kafkaMessageRecordKey(it) }
                var selectedRecordKey by remember(
                    messages.topicName,
                    messages.scope,
                    messages.mode,
                    recordsKey,
                ) {
                    mutableStateOf(messages.records.firstOrNull()?.let(::kafkaMessageRecordKey))
                }
                val selectedRecord = messages.records
                    .firstOrNull { kafkaMessageRecordKey(it) == selectedRecordKey }
                    ?: messages.records.first()
                var activeInspectorTab by remember(selectedRecordKey) {
                    mutableStateOf(KafkaMessageInspectorTabValue)
                }
                var payloadMode by remember(selectedRecordKey, activeInspectorTab) {
                    mutableStateOf(KafkaMessagePayloadModePretty)
                }

                Div({ classes("kafka-message-browser-shell") }) {
                    Div({ classes("table-responsive", "kafka-message-table-wrap") }) {
                        Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-message-table") }) {
                            Thead {
                                Tr {
                                    Th { Text("") }
                                    Th { Text("Offset") }
                                    Th { Text("Partition") }
                                    Th { Text("Timestamp") }
                                    Th { Text("Key Preview") }
                                    Th { Text("Value Preview") }
                                    Th { Text("Headers") }
                                }
                            }
                            Tbody {
                                messages.records.forEach { record ->
                                    val recordKey = kafkaMessageRecordKey(record)
                                    val rowClasses = if (recordKey == kafkaMessageRecordKey(selectedRecord)) {
                                        arrayOf("kafka-message-row", "kafka-message-row-active")
                                    } else {
                                        arrayOf("kafka-message-row")
                                    }
                                    Tr(attrs = {
                                        classes(*rowClasses)
                                        onClick {
                                            selectedRecordKey = recordKey
                                        }
                                    }) {
                                        Td({ classes("kafka-message-expander-cell") }) {
                                            Button(attrs = {
                                                classes("kafka-message-expander")
                                                attr("type", "button")
                                                onClick {
                                                    selectedRecordKey = recordKey
                                                }
                                            }) {
                                                Text(if (recordKey == kafkaMessageRecordKey(selectedRecord)) "-" else "+")
                                            }
                                        }
                                        Td({ classes("kafka-message-cell-mono") }) { Text(record.offset.toString()) }
                                        Td({ classes("kafka-message-cell-mono") }) { Text(record.partition.toString()) }
                                        Td { Text(formatKafkaMessageTimestamp(record.timestamp)) }
                                        Td {
                                            Div({ classes("kafka-message-preview") }) {
                                                Text(renderKafkaMessagePreview(record.key))
                                            }
                                        }
                                        Td {
                                            Div({ classes("kafka-message-preview") }) {
                                                Text(renderKafkaMessagePreview(record.value))
                                            }
                                        }
                                        Td({ classes("kafka-message-cell-mono") }) { Text(record.headers.size.toString()) }
                                    }
                                    if (recordKey == kafkaMessageRecordKey(selectedRecord)) {
                                        Tr({ classes("kafka-message-expanded-row") }) {
                                            Td(attrs = {
                                                attr("colspan", "7")
                                            }) {
                                                KafkaMessageExpandedInspector(
                                                    record = record,
                                                    activeTab = activeInspectorTab,
                                                    payloadMode = payloadMode,
                                                    onActiveTabChange = { activeInspectorTab = it },
                                                    onPayloadModeChange = { payloadMode = it },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun kafkaMessageRecordKey(record: KafkaTopicMessageRecordResponse): String =
    "${record.partition}:${record.offset}"
