package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

@Composable
internal fun KafkaProduceSection(
    state: KafkaPageState,
    onProducePartitionChange: (String) -> Unit,
    onProduceKeyChange: (String) -> Unit,
    onProduceHeadersChange: (String) -> Unit,
    onProducePayloadChange: (String) -> Unit,
    onProduceMessage: () -> Unit,
) {
    val topicOverview = state.topicOverview ?: return
    SectionCard(
        title = "Produce",
        subtitle = "Single-message produce с key, headers и optional partition override.",
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

        Div({ classes("kafka-produce-grid") }) {
            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Partition override") }
                Input(type = InputType.Number, attrs = {
                    classes("form-control")
                    placeholder("optional")
                    value(state.producePartitionInput)
                    onInput { onProducePartitionChange(it.value?.toString().orEmpty()) }
                })
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

        Div({ classes("kafka-produce-textarea") }) {
            P({ classes("kafka-message-control-label") }) { Text("Headers") }
            TextArea(attrs = {
                classes("form-control", "kafka-produce-headers")
                placeholder("name=value, one per line")
                value(state.produceHeadersInput)
                onInput { onProduceHeadersChange(it.value) }
            })
        }

        Div({ classes("kafka-produce-textarea") }) {
            P({ classes("kafka-message-control-label") }) { Text("Payload") }
            TextArea(attrs = {
                classes("form-control", "kafka-produce-payload")
                placeholder("message body")
                value(state.producePayloadInput)
                onInput { onProducePayloadChange(it.value) }
            })
        }

        Div({ classes("kafka-message-action") }) {
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
            Div({ classes("kafka-produce-result") }) {
                Text(
                    "Сообщение отправлено: partition ${result.partition} · offset ${result.offset} · timestamp ${result.timestamp ?: "n/a"}",
                )
            }
        }
    }
}
