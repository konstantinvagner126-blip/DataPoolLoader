package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane
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
internal fun KafkaProduceSection(
    state: KafkaPageState,
    onProducePartitionChange: (String) -> Unit,
    onProduceKeyChange: (String) -> Unit,
    onAddProduceHeader: () -> Unit,
    onRemoveProduceHeader: (Int) -> Unit,
    onProduceHeaderNameChange: (Int, String) -> Unit,
    onProduceHeaderValueChange: (Int, String) -> Unit,
    onProducePayloadChange: (String) -> Unit,
    onProduceMessage: () -> Unit,
) {
    val topicOverview = state.topicOverview ?: return

    Div({ classes("kafka-produce-panel") }) {
        Div({ classes("kafka-produce-pane-head") }) {
            Div {
                Div({ classes("kafka-message-pane-title") }) { Text("Produce message") }
                P({ classes("kafka-message-pane-subtitle") }) {
                    Text("Structured editor: key, partition override, headers and payload.")
                }
            }
            Span({
                classes("kafka-tool-chip", if (topicOverview.cluster.readOnly) "lock" else "ok")
            }) {
                Text(if (topicOverview.cluster.readOnly) "read only" else "write enabled")
            }
        }

        if (topicOverview.cluster.readOnly) {
            AlertBanner(
                "Кластер помечен как readOnly. Produce path для него запрещен.",
                "warning",
            )
        } else {
            state.produceError?.let { message ->
                AlertBanner(message, "warning")
            }

            Div({ classes("kafka-produce-shell") }) {
                Div({ classes("kafka-produce-toolbar") }) {
                    Div({ classes("kafka-message-control") }) {
                        P({ classes("kafka-message-control-label") }) { Text("Partition override") }
                        Select(attrs = {
                            classes("form-select")
                            onChange { onProducePartitionChange(it.value.orEmpty()) }
                        }) {
                            Option(value = "", attrs = { if (state.producePartitionInput.isBlank()) selected() }) {
                                Text("Auto")
                            }
                            topicOverview.partitions.forEach { partition ->
                                val partitionValue = partition.partition.toString()
                                Option(
                                    value = partitionValue,
                                    attrs = { if (state.producePartitionInput == partitionValue) selected() },
                                ) {
                                    Text(partitionValue)
                                }
                            }
                        }
                    }

                    Div({ classes("kafka-message-control") }) {
                        P({ classes("kafka-message-control-label") }) { Text("Key") }
                        Input(type = InputType.Text, attrs = {
                            classes("form-control")
                            placeholder("optional")
                            value(state.produceKeyInput)
                            onInput { onProduceKeyChange(it.value?.toString().orEmpty()) }
                        })
                    }
                }

                Div({ classes("kafka-produce-editor-block", "kafka-produce-headers-panel") }) {
                    Div({ classes("kafka-produce-section-header") }) {
                        P({ classes("kafka-message-section-title") }) { Text("Headers") }
                        Button(attrs = {
                            classes("btn", "btn-outline-secondary", "btn-sm")
                            attr("type", "button")
                            onClick { onAddProduceHeader() }
                        }) {
                            Text("Добавить header")
                        }
                    }

                    if (state.produceHeaders.isEmpty()) {
                        P({ classes("kafka-placeholder-note", "mb-0") }) {
                            Text("Headers не заданы.")
                        }
                    } else {
                        Div({ classes("table-responsive", "kafka-produce-headers-table-wrap") }) {
                            Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-produce-headers-table") }) {
                                Thead {
                                    Tr {
                                        Th { Text("Name") }
                                        Th { Text("Value") }
                                        Th { Text("Action") }
                                    }
                                }
                                Tbody {
                                    state.produceHeaders.forEachIndexed { index, header ->
                                        Tr {
                                            Td {
                                                Input(type = InputType.Text, attrs = {
                                                    classes("form-control")
                                                    value(header.name)
                                                    onInput {
                                                        onProduceHeaderNameChange(index, it.value?.toString().orEmpty())
                                                    }
                                                })
                                            }
                                            Td {
                                                Input(type = InputType.Text, attrs = {
                                                    classes("form-control")
                                                    value(header.value)
                                                    onInput {
                                                        onProduceHeaderValueChange(index, it.value?.toString().orEmpty())
                                                    }
                                                })
                                            }
                                            Td({ classes("kafka-produce-header-action") }) {
                                                Button(attrs = {
                                                    classes("btn", "btn-outline-secondary", "btn-sm")
                                                    attr("type", "button")
                                                    onClick { onRemoveProduceHeader(index) }
                                                }) {
                                                    Text("Удалить")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Div({ classes("kafka-produce-editor-block") }) {
                    KafkaProducePayloadEditor(
                        topicName = topicOverview.topic.name,
                        payload = state.producePayloadInput,
                        onPayloadChange = onProducePayloadChange,
                    )
                }

                Div({ classes("kafka-produce-actions") }) {
                    Button(attrs = {
                        classes("btn", "btn-dark")
                        attr("type", "button")
                        if (state.produceLoading) disabled()
                        onClick { onProduceMessage() }
                    }) {
                        Text(if (state.produceLoading) "Отправляю..." else "Отправить сообщение")
                    }
                }

                state.produceResult?.takeIf { it.topicName == topicOverview.topic.name }?.let { result ->
                    Div({ classes("kafka-produce-result-card") }) {
                        Div({ classes("kafka-produce-result-title") }) { Text("Message delivered") }
                        Div({ classes("kafka-produce-result-line") }) {
                            Text(
                                "Sent to ${result.topicName} · partition ${result.partition} · " +
                                    "offset ${result.offset} · timestamp ${formatKafkaMessageTimestamp(result.timestamp)}",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KafkaProducePayloadEditor(
    topicName: String,
    payload: String,
    onPayloadChange: (String) -> Unit,
) {
    val formattedPayload = remember(payload) { formatKafkaProduceJsonPayload(payload) }

    Div({ classes("kafka-produce-payload-head") }) {
        P({ classes("kafka-message-section-title") }) { Text("Payload") }
        Button(attrs = {
            classes("btn", "btn-outline-secondary", "btn-sm")
            attr("type", "button")
            if (formattedPayload == null) disabled()
            onClick {
                formattedPayload?.let(onPayloadChange)
            }
        }) {
            Text("Форматировать JSON")
        }
    }
    MonacoEditorPane(
        instanceKey = "kafka-produce-payload-$topicName",
        language = "json",
        value = payload,
        classNames = listOf("editor-frame", "kafka-produce-payload-editor"),
        onValueChange = onPayloadChange,
    )
}

private fun formatKafkaProduceJsonPayload(payload: String): String? {
    val trimmed = payload.trim()
    if (trimmed.isBlank()) {
        return null
    }
    return try {
        js("JSON.stringify(JSON.parse(trimmed), null, 2)") as String
    } catch (_: dynamic) {
        null
    }
}
