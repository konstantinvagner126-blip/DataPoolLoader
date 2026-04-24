package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

@Composable
internal fun KafkaTopicsCatalogSection(
    state: KafkaPageState,
    selectedCluster: KafkaClusterCatalogEntryResponse,
    topicsResponse: KafkaTopicsCatalogResponse?,
    onTopicQueryChange: (String) -> Unit,
    onApplyTopicQuery: () -> Unit,
) {
    val filteredTopics = topicsResponse?.topics.orEmpty()
    val topicCountLabel = when {
        state.topicsLoading && topicsResponse == null -> "Загрузка"
        filteredTopics.isEmpty() -> "0 topics"
        else -> "${filteredTopics.size} topics"
    }
    SectionCard(
        title = "Топики",
        subtitle = "Кластер: ${selectedCluster.name}",
        actions = {
            Span({ classes("badge", "text-bg-light", "kafka-topic-count-badge") }) {
                Text(topicCountLabel)
            }
        },
    ) {
        Div({ classes("kafka-topic-screen-header") }) {
            Div({ classes("kafka-topic-filter-row") }) {
                Input(type = InputType.Search, attrs = {
                    classes("form-control")
                    placeholder("topic name, prefix, internal...")
                    value(state.topicQuery)
                    onInput { onTopicQueryChange(it.value) }
                })
                Button(attrs = {
                    classes("btn", "btn-outline-secondary")
                    attr("type", "button")
                    onClick { onApplyTopicQuery() }
                }) {
                    Text("Обновить")
                }
            }

            Div({ classes("kafka-topic-filter-meta") }) {
                Span({ classes("kafka-section-caption", "mb-0") }) {
                    Text("Topic catalog")
                }
                if (state.topicQuery.isNotBlank()) {
                    Span({ classes("kafka-topic-filter-query") }) {
                        Text("Фильтр: ${state.topicQuery}")
                    }
                } else {
                    Span({ classes("kafka-topic-filter-query") }) {
                        Text("Показаны все topic'и кластера")
                    }
                }
                if (state.topicsLoading && topicsResponse != null) {
                    Span({ classes("kafka-topic-filter-status") }) {
                        Text("Обновляю каталог")
                    }
                } else {
                    Span({ classes("kafka-topic-filter-status") }) {
                        Text("Нажми на строку, чтобы открыть topic details")
                    }
                }
            }
        }

        when {
            state.topicsLoading && topicsResponse == null -> {
                P({ classes("text-secondary", "small", "mb-0", "kafka-topic-empty-note") }) {
                    Text("Загружаю список топиков.")
                }
            }

            filteredTopics.isEmpty() -> {
                P({ classes("text-secondary", "small", "mb-0", "kafka-topic-empty-note") }) {
                    Text(
                        if (state.topicQuery.isBlank()) {
                            "Для выбранного кластера топиков нет."
                        } else {
                            "Для выбранного кластера нет топиков по фильтру '${state.topicQuery}'."
                        },
                    )
                }
            }

            else -> {
                Div({ classes("table-responsive", "kafka-topic-table-wrap") }) {
                    Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-topic-table") }) {
                        Thead {
                            Tr {
                                Th { Text("Topic") }
                                Th { Text("Partitions") }
                                Th { Text("Replication") }
                                Th { Text("Cleanup") }
                                Th { Text("Retention") }
                            }
                        }
                        Tbody {
                            filteredTopics.forEach { topic ->
                                val topicHref = buildKafkaPageHref(
                                    clusterId = selectedCluster.id,
                                    clusterSection = "topics",
                                    topicName = topic.name,
                                    topicQuery = state.topicQuery,
                                    activePane = "overview",
                                    messageReadScope = state.messageReadScope,
                                    messageReadMode = state.messageReadMode,
                                    selectedMessagePartition = state.selectedMessagePartition,
                                )
                                Tr({
                                    classes(
                                        "kafka-topic-row",
                                        if (topic.name == state.selectedTopicName) {
                                            "kafka-topic-row-active"
                                        } else {
                                            "kafka-topic-row-idle"
                                        },
                                    )
                                    attr("role", "link")
                                    onClick { window.location.href = topicHref }
                                }) {
                                    Td {
                                        A(attrs = {
                                            classes("kafka-topic-link")
                                            href(topicHref)
                                        }) {
                                            Span({ classes("kafka-topic-name") }) {
                                                Text(topic.name)
                                            }
                                        }
                                        Div({ classes("kafka-topic-flags") }) {
                                            if (topic.internal) {
                                                Span({ classes("kafka-topic-flag") }) { Text("internal") }
                                            }
                                            if (topic.name == state.selectedTopicName) {
                                                Span({ classes("kafka-topic-flag", "kafka-topic-flag-active") }) {
                                                    Text("selected")
                                                }
                                            }
                                        }
                                    }
                                    Td({ classes("kafka-topic-value-cell") }) {
                                        Text(topic.partitionCount.toString())
                                    }
                                    Td({ classes("kafka-topic-value-cell") }) {
                                        Text(topic.replicationFactor.toString())
                                    }
                                    Td({ classes("kafka-topic-value-cell") }) {
                                        Text(topic.cleanupPolicy ?: "default")
                                    }
                                    Td({ classes("kafka-topic-value-cell") }) {
                                        Text(formatKafkaRetention(topic.retentionMs, topic.retentionBytes))
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
internal fun KafkaOverviewMetric(
    title: String,
    value: String,
) {
    Div({ classes("kafka-topic-summary-item") }) {
        Div({ classes("kafka-topic-summary-label") }) { Text(title) }
        Div({ classes("kafka-topic-summary-value") }) { Text(value) }
    }
}

internal fun formatKafkaRetention(
    retentionMs: Long?,
    retentionBytes: Long?,
): String {
    val timePart = retentionMs?.let { "${it} ms" }
    val sizePart = retentionBytes?.let { "${it} B" }
    return listOfNotNull(timePart, sizePart).joinToString(" · ").ifBlank { "default" }
}
