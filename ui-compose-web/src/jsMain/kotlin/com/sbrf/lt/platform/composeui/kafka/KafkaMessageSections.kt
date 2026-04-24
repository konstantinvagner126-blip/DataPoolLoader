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
                it.cluster.id == topicOverview.cluster.id
        }?.let { messages ->
            messages.note?.let { note ->
                P({ classes("kafka-message-note") }) { Text(note) }
            }

            Div({ classes("kafka-message-browser-meta") }) {
                P({ classes("kafka-message-browser-summary") }) {
                    Text(
                        buildList {
                            add(messages.status)
                            add("${messages.durationMs} ms")
                            add(formatKafkaMessageBytes(messages.consumedBytes))
                            add("${messages.consumedMessages} messages consumed")
                        }.joinToString(" · "),
                    )
                }
                P({ classes("kafka-message-browser-context") }) {
                    Text(
                        buildList {
                            add("scope ${messages.scope.lowercase().replace('_', ' ')}")
                            add("mode ${messages.mode.lowercase()}")
                            messages.effectiveStartOffset?.let { add("start offset $it") }
                        }.joinToString(" · "),
                    )
                }
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
                                    val rowClasses = if (recordKey == kafkaMessageRecordKey(selectedRecord)) {
                                        arrayOf("kafka-message-row", "kafka-message-row-active")
                                    } else {
                                        arrayOf("kafka-message-row")
                                    }
                                    Tr(attrs = {
                                        classes(*rowClasses)
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
                                        KafkaHeaderValueContent(header.value)
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
            KafkaRenderedPayloadContent(payload)
        }
    }
}

@Composable
private fun KafkaHeaderValueContent(payload: KafkaRenderedBytesResponse?) {
    val jsonPrettyText = payload?.jsonPrettyText
    when {
        payload == null -> Text("null")
        jsonPrettyText != null -> Div({ classes("kafka-message-header-json") }) {
            KafkaJsonHighlightedText(jsonPrettyText)
        }
        else -> Text(payload.text ?: "")
    }
}

@Composable
private fun KafkaRenderedPayloadContent(payload: KafkaRenderedBytesResponse) {
    val jsonPrettyText = payload.jsonPrettyText
    jsonPrettyText?.let {
        KafkaJsonHighlightedText(jsonPrettyText)
        return
    }
    Text(payload.text ?: "")
}

@Composable
private fun KafkaJsonHighlightedText(json: String) {
    kafkaJsonTokens(json).forEach { token ->
        if (token.kind == KafkaJsonTokenKind.WHITESPACE) {
            Text(token.text)
        } else {
            Span({ classes("kafka-json-token", token.kind.cssClassName) }) {
                Text(token.text)
            }
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

private fun formatKafkaMessageBytes(bytes: Long): String =
    when {
        bytes >= 1_048_576L -> "${((bytes * 10) / 1_048_576L) / 10.0} MB"
        bytes >= 1024L -> "${((bytes * 10) / 1024L) / 10.0} KB"
        else -> "$bytes Bytes"
    }

private data class KafkaJsonToken(
    val kind: KafkaJsonTokenKind,
    val text: String,
)

private enum class KafkaJsonTokenKind(
    val cssClassName: String,
) {
    PUNCTUATION("kafka-json-token-punctuation"),
    KEY("kafka-json-token-key"),
    STRING("kafka-json-token-string"),
    NUMBER("kafka-json-token-number"),
    BOOLEAN("kafka-json-token-boolean"),
    NULL("kafka-json-token-null"),
    WHITESPACE(""),
}

private fun kafkaJsonTokens(json: String): List<KafkaJsonToken> {
    val tokens = mutableListOf<KafkaJsonToken>()
    var index = 0
    while (index < json.length) {
        val current = json[index]
        when {
            current.isWhitespace() -> {
                val start = index
                while (index < json.length && json[index].isWhitespace()) {
                    index += 1
                }
                tokens += KafkaJsonToken(KafkaJsonTokenKind.WHITESPACE, json.substring(start, index))
            }

            current == '"' -> {
                val start = index
                index = readKafkaJsonStringEnd(json, start)
                val tokenText = json.substring(start, index)
                val kind = if (isKafkaJsonObjectKey(json, index)) {
                    KafkaJsonTokenKind.KEY
                } else {
                    KafkaJsonTokenKind.STRING
                }
                tokens += KafkaJsonToken(kind, tokenText)
            }

            current == '{' || current == '}' || current == '[' || current == ']' || current == ':' || current == ',' -> {
                tokens += KafkaJsonToken(KafkaJsonTokenKind.PUNCTUATION, current.toString())
                index += 1
            }

            json.startsWith("true", index) -> {
                tokens += KafkaJsonToken(KafkaJsonTokenKind.BOOLEAN, "true")
                index += 4
            }

            json.startsWith("false", index) -> {
                tokens += KafkaJsonToken(KafkaJsonTokenKind.BOOLEAN, "false")
                index += 5
            }

            json.startsWith("null", index) -> {
                tokens += KafkaJsonToken(KafkaJsonTokenKind.NULL, "null")
                index += 4
            }

            current == '-' || current.isDigit() -> {
                val start = index
                index += 1
                while (index < json.length && (json[index].isDigit() || json[index] in ".eE+-")) {
                    index += 1
                }
                tokens += KafkaJsonToken(KafkaJsonTokenKind.NUMBER, json.substring(start, index))
            }

            else -> {
                tokens += KafkaJsonToken(KafkaJsonTokenKind.PUNCTUATION, current.toString())
                index += 1
            }
        }
    }
    return tokens
}

private fun readKafkaJsonStringEnd(
    json: String,
    start: Int,
): Int {
    var index = start + 1
    var escaped = false
    while (index < json.length) {
        val current = json[index]
        if (escaped) {
            escaped = false
        } else if (current == '\\') {
            escaped = true
        } else if (current == '"') {
            return index + 1
        }
        index += 1
    }
    return json.length
}

private fun isKafkaJsonObjectKey(
    json: String,
    stringEndExclusive: Int,
): Boolean {
    var index = stringEndExclusive
    while (index < json.length && json[index].isWhitespace()) {
        index += 1
    }
    return index < json.length && json[index] == ':'
}
