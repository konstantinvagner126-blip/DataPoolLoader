package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
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
    SectionCard(
        title = "Produce",
        subtitle = "Single-message produce в structured form с key, headers и optional partition override.",
    ) {
        if (topicOverview.cluster.readOnly) {
            AlertBanner(
                "Кластер помечен как readOnly. Produce path для него запрещен.",
                "warning",
            )
            return@SectionCard
        }

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
                        value(state.produceKeyInput)
                        onInput { onProduceKeyChange(it.value?.toString().orEmpty()) }
                    })
                }
            }

            Div({ classes("kafka-produce-editor-block") }) {
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
                        Text("Headers не заданы. При необходимости добавь их отдельными строками.")
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
                                                onInput { onProduceHeaderNameChange(index, it.value?.toString().orEmpty()) }
                                            })
                                        }
                                        Td {
                                            Input(type = InputType.Text, attrs = {
                                                classes("form-control")
                                                value(header.value)
                                                onInput { onProduceHeaderValueChange(index, it.value?.toString().orEmpty()) }
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
                P({ classes("kafka-message-section-title") }) { Text("Payload") }
                TextArea(attrs = {
                    classes("form-control", "kafka-produce-payload")
                    value(state.producePayloadInput)
                    onInput { onProducePayloadChange(it.value) }
                })
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
                Div({ classes("kafka-produce-result-shell") }) {
                    Div({ classes("kafka-message-detail-summary") }) {
                        KafkaMessageMetric("Topic", result.topicName)
                        KafkaMessageMetric("Partition", result.partition.toString())
                        KafkaMessageMetric("Offset", result.offset.toString())
                        KafkaMessageMetric("Timestamp", formatKafkaMessageTimestamp(result.timestamp))
                    }
                }
            }
        }
    }
}
