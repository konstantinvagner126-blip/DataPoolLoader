package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
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
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

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

                Div({ classes("kafka-message-browser-shell") }) {
                    Div({ classes("kafka-message-browser-meta") }) {
                        Text(
                            buildList {
                                add("${messages.records.size} records")
                                add("scope ${messages.scope.lowercase().replace('_', ' ')}")
                                add("mode ${messages.mode.lowercase()}")
                                messages.effectiveStartOffset?.let { add("start offset $it") }
                            }.joinToString(" · "),
                        )
                    }

                    Div({ classes("table-responsive", "kafka-message-table-wrap") }) {
                        Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-message-table") }) {
                            Thead {
                                Tr {
                                    Th { Text("Offset") }
                                    Th { Text("Partition") }
                                    Th { Text("Timestamp") }
                                    Th { Text("Key") }
                                    Th { Text("Value") }
                                    Th { Text("Headers") }
                                }
                            }
                            Tbody {
                                messages.records.forEach { record ->
                                    val recordKey = kafkaMessageRecordKey(record)
                                    Tr(attrs = {
                                        classes(
                                            "kafka-message-row",
                                            if (recordKey == kafkaMessageRecordKey(selectedRecord)) "kafka-message-row-active" else "",
                                        )
                                        onClick { selectedRecordKey = recordKey }
                                    }) {
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
                                }
                            }
                        }
                    }

                    KafkaMessageDetailsPane(selectedRecord)
                }
            }
        }
    }
}

@Composable
internal fun KafkaMessageDetailsPane(
    record: KafkaTopicMessageRecordResponse,
) {
    Div({ classes("kafka-message-detail-shell") }) {
        Div({ classes("kafka-message-detail-header") }) {
            Div({ classes("kafka-message-detail-title") }) {
                Text("Message details")
            }
            Div({ classes("kafka-message-detail-meta") }) {
                Text(
                    buildList {
                        add("partition ${record.partition}")
                        add("offset ${record.offset}")
                        add("timestamp ${formatKafkaMessageTimestamp(record.timestamp)}")
                    }.joinToString(" · "),
                )
            }
        }

        Div({ classes("kafka-message-detail-summary") }) {
            KafkaMessageMetric("Partition", record.partition.toString())
            KafkaMessageMetric("Offset", record.offset.toString())
            KafkaMessageMetric("Headers", record.headers.size.toString())
            KafkaMessageMetric("Timestamp", formatKafkaMessageTimestamp(record.timestamp))
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
                Div({ classes("table-responsive") }) {
                    Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-message-header-table") }) {
                        Thead {
                            Tr {
                                Th { Text("Name") }
                                Th { Text("Value") }
                            }
                        }
                        Tbody {
                            record.headers.forEach { header ->
                                Tr {
                                    Td({ classes("kafka-message-header-name") }) { Text(header.name) }
                                    Td({ classes("kafka-message-header-value") }) {
                                        Text(header.value?.jsonPrettyText ?: header.value?.text ?: "null")
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

@Composable
internal fun KafkaMessageMetric(
    label: String,
    value: String,
) {
    Div({ classes("kafka-message-metric") }) {
        P({ classes("kafka-message-control-label") }) { Text(label) }
        Div({ classes("kafka-message-metric-value") }) { Text(value) }
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

private fun kafkaMessageRecordKey(record: KafkaTopicMessageRecordResponse): String =
    "${record.partition}:${record.offset}"

private fun renderKafkaMessagePreview(payload: KafkaRenderedBytesResponse?): String {
    if (payload == null) {
        return "null"
    }
    val raw = payload.jsonPrettyText
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        ?.ifBlank { null }
        ?: payload.text
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.ifBlank { null }
        ?: ""
    if (raw.isBlank()) {
        return "—"
    }
    val compact = raw.replace('\n', ' ').replace('\r', ' ')
    return if (compact.length > 120) "${compact.take(117)}..." else compact
}

internal fun formatKafkaMessageTimestamp(timestamp: Long?): String {
    if (timestamp == null) {
        return "n/a"
    }
    return try {
        js("new Date(timestamp).toLocaleString('ru-RU')") as String
    } catch (_: dynamic) {
        timestamp.toString()
    }
}
