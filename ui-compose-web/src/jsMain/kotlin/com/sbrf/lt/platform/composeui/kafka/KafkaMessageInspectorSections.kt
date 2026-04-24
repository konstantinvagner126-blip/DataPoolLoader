package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

@Composable
internal fun KafkaMessageResultMeta(
    messages: KafkaTopicMessageReadResponse,
) {
    Div({ classes("kafka-message-result-meta") }) {
        Div({ classes("kafka-message-consume-state") }) { Text(messages.status) }
        KafkaMessageConsumeStat("Elapsed Time", "${messages.durationMs} ms")
        KafkaMessageConsumeStat("Bytes Consumed", formatKafkaMessageBytes(messages.consumedBytes))
        KafkaMessageConsumeStat("Messages Consumed", "${messages.consumedMessages} messages consumed")
        Div({ classes("kafka-message-result-note") }) {
            Text(
                buildList {
                    add("scope ${messages.scope.lowercase().replace('_', ' ')}")
                    add("mode ${messages.mode.lowercase()}")
                    add("effective limit ${messages.effectiveLimit}")
                    messages.effectiveStartOffset?.let { add("start offset $it") }
                    add("last successful result is preserved until next explicit read")
                }.joinToString(" · "),
            )
        }
    }
}

@Composable
private fun KafkaMessageConsumeStat(
    label: String,
    value: String,
) {
    Div({ classes("kafka-message-consume-stat") }) {
        Div({ classes("kafka-message-consume-stat-label") }) { Text(label) }
        Div({ classes("kafka-message-consume-stat-value") }) { Text(value) }
    }
}

@Composable
internal fun KafkaMessageExpandedInspector(
    record: KafkaTopicMessageRecordResponse,
    activeTab: String,
    payloadMode: String,
    onActiveTabChange: (String) -> Unit,
    onPayloadModeChange: (String) -> Unit,
) {
    Div({ classes("kafka-message-inspector") }) {
        Div({ classes("kafka-message-inspector-top") }) {
            Div {
                Div({ classes("kafka-message-detail-title") }) {
                    Text("Message at offset ${record.offset}")
                }
                Div({ classes("kafka-message-detail-meta") }) {
                    Text(
                        buildList {
                            add("partition ${record.partition}")
                            add("key ${renderKafkaMessagePreview(record.key)}")
                            add("${record.headers.size} headers")
                        }.joinToString(" · "),
                    )
                }
            }
            Div({ classes("kafka-message-inspector-tabs") }) {
                KafkaMessageInspectorTabButton(KafkaMessageInspectorTabValue, "Value", activeTab, onActiveTabChange)
                KafkaMessageInspectorTabButton(KafkaMessageInspectorTabKey, "Key", activeTab, onActiveTabChange)
                KafkaMessageInspectorTabButton(KafkaMessageInspectorTabHeaders, "Headers", activeTab, onActiveTabChange)
                KafkaMessageInspectorTabButton(KafkaMessageInspectorTabMetadata, "Metadata", activeTab, onActiveTabChange)
            }
        }

        Div({ classes("kafka-message-inspector-body") }) {
            Div({ classes("kafka-message-inspector-main") }) {
                when (activeTab) {
                    KafkaMessageInspectorTabKey -> KafkaMessagePayloadInspector(
                        title = "Key",
                        payload = record.key,
                        payloadMode = payloadMode,
                        onPayloadModeChange = onPayloadModeChange,
                    )

                    KafkaMessageInspectorTabHeaders -> KafkaMessageHeadersInspector(record.headers)
                    KafkaMessageInspectorTabMetadata -> KafkaMessageMetadataInspector(record)
                    else -> KafkaMessagePayloadInspector(
                        title = "Value",
                        payload = record.value,
                        payloadMode = payloadMode,
                        onPayloadModeChange = onPayloadModeChange,
                    )
                }
            }
            Div({ classes("kafka-message-inspector-side") }) {
                KafkaMessageMetric("Timestamp", formatKafkaMessageTimestamp(record.timestamp))
                KafkaMessageMetric("Partition", record.partition.toString())
                KafkaMessageMetric("Offset", record.offset.toString())
                KafkaMessageMetric("Headers", record.headers.size.toString())
            }
        }
    }
}

@Composable
private fun KafkaMessageInspectorTabButton(
    tab: String,
    label: String,
    activeTab: String,
    onActiveTabChange: (String) -> Unit,
) {
    val tabClasses = if (tab == activeTab) {
        arrayOf("kafka-message-inspector-tab", "active")
    } else {
        arrayOf("kafka-message-inspector-tab", "idle")
    }
    Button(attrs = {
        classes(*tabClasses)
        attr("type", "button")
        onClick { onActiveTabChange(tab) }
    }) {
        Text(label)
    }
}

@Composable
private fun KafkaMessagePayloadInspector(
    title: String,
    payload: KafkaRenderedBytesResponse?,
    payloadMode: String,
    onPayloadModeChange: (String) -> Unit,
) {
    if (payload == null) {
        Div({ classes("kafka-message-empty-payload") }) {
            Text("$title is null.")
        }
        return
    }
    Div({ classes("kafka-message-payload") }) {
        Div({ classes("kafka-message-payload-actions") }) {
            Div({ classes("kafka-message-payload-toolbar") }) {
                KafkaMessagePayloadModeButton(
                    label = "Pretty",
                    active = payloadMode == KafkaMessagePayloadModePretty,
                    isDisabled = payload.jsonPrettyText == null,
                ) {
                    onPayloadModeChange(KafkaMessagePayloadModePretty)
                }
                KafkaMessagePayloadModeButton(
                    label = "Raw",
                    active = payloadMode == KafkaMessagePayloadModeRaw,
                    isDisabled = false,
                ) {
                    onPayloadModeChange(KafkaMessagePayloadModeRaw)
                }
                Button(attrs = {
                    classes("kafka-message-mini-button", "idle")
                    attr("type", "button")
                    onClick { copyKafkaPayloadToClipboard(payload, payloadMode) }
                }) {
                    Text("Copy")
                }
            }
            Span({
                classes(
                    "kafka-message-payload-badge",
                    if (payload.jsonPrettyText != null) "ok" else "plain",
                )
            }) {
                Text(if (payload.jsonPrettyText != null) "valid JSON" else "plain text fallback")
            }
        }

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
        Pre({
            classes(
                "kafka-message-payload-body",
                if (payloadMode == KafkaMessagePayloadModePretty && payload.jsonPrettyText != null) "json" else "plain",
            )
        }) {
            KafkaRenderedPayloadContent(payload, payloadMode)
        }
    }
}

@Composable
private fun KafkaMessagePayloadModeButton(
    label: String,
    active: Boolean,
    isDisabled: Boolean,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("kafka-message-mini-button", if (active) "active" else "idle")
        attr("type", "button")
        if (isDisabled) disabled()
        onClick { onClick() }
    }) {
        Text(label)
    }
}

@Composable
private fun KafkaMessageHeadersInspector(
    headers: List<KafkaTopicMessageHeaderResponse>,
) {
    if (headers.isEmpty()) {
        Div({ classes("kafka-message-empty-payload") }) {
            Text("Headers are empty.")
        }
        return
    }
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
                    headers.forEach { header ->
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

@Composable
private fun KafkaMessageMetadataInspector(
    record: KafkaTopicMessageRecordResponse,
) {
    Div({ classes("kafka-message-metadata-grid") }) {
        KafkaMessageMetric("Partition", record.partition.toString())
        KafkaMessageMetric("Offset", record.offset.toString())
        KafkaMessageMetric("Timestamp", formatKafkaMessageTimestamp(record.timestamp))
        KafkaMessageMetric("Key size", record.key?.sizeBytes?.let { "$it B" } ?: "null")
        KafkaMessageMetric("Value size", record.value?.sizeBytes?.let { "$it B" } ?: "null")
        KafkaMessageMetric("Headers", record.headers.size.toString())
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
private fun KafkaRenderedPayloadContent(
    payload: KafkaRenderedBytesResponse,
    payloadMode: String,
) {
    val jsonPrettyText = payload.jsonPrettyText.takeIf { payloadMode == KafkaMessagePayloadModePretty }
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

internal fun renderKafkaMessagePreview(payload: KafkaRenderedBytesResponse?): String {
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

private fun copyKafkaPayloadToClipboard(
    payload: KafkaRenderedBytesResponse,
    payloadMode: String,
) {
    val text = if (payloadMode == KafkaMessagePayloadModePretty) {
        payload.jsonPrettyText ?: payload.text
    } else {
        payload.text ?: payload.jsonPrettyText
    }
    if (!text.isNullOrEmpty()) {
        window.navigator.asDynamic().clipboard?.writeText(text)
    }
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
