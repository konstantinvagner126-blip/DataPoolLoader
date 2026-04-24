package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
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
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.jetbrains.compose.web.dom.Table

@Composable
internal fun KafkaPageContent(
    state: KafkaPageState,
    onTopicQueryChange: (String) -> Unit,
    onApplyTopicQuery: () -> Unit,
    onReloadTopicOverview: (String) -> Unit,
) {
    state.errorMessage?.let { AlertBanner(it, "warning") }

    if (state.loading && state.info == null) {
        LoadingStateCard(
            title = "Kafka metadata",
            text = "Загружаю каталог кластеров и список топиков.",
        )
        return
    }

    val info = state.info
    if (info == null || !info.configured || info.clusters.isEmpty()) {
        EmptyStateCard(
            title = "Kafka clusters не настроены",
            text = "Добавь catalog кластеров в ui.kafka.clusters, чтобы открыть Kafka-инструмент.",
        )
        return
    }

    val selectedCluster = info.clusters.firstOrNull { it.id == state.selectedClusterId } ?: info.clusters.first()
    val selectedTopic = state.topicOverview
    val topicsResponse = state.topics

    Div({ classes("kafka-content-shell") }) {
        SectionCard(
            title = "Кластеры Kafka",
            subtitle = "Config-driven catalog. Кластер выбирается из ui.kafka.clusters без ad-hoc connection form.",
        ) {
            Div({ classes("kafka-cluster-strip") }) {
                info.clusters.forEach { cluster ->
                    A(attrs = {
                        classes(
                            "kafka-cluster-link",
                            if (cluster.id == selectedCluster.id) "kafka-cluster-link-active" else "kafka-cluster-link-idle",
                        )
                        href(buildKafkaPageHref(cluster.id))
                    }) {
                        Div({ classes("kafka-cluster-link-top") }) {
                            Span({ classes("kafka-cluster-name") }) { Text(cluster.name) }
                            Span({ classes("kafka-cluster-protocol") }) { Text(cluster.securityProtocol) }
                        }
                        Div({ classes("kafka-cluster-bootstrap") }) {
                            Text(cluster.bootstrapServers)
                        }
                        if (cluster.readOnly) {
                            Div({ classes("kafka-cluster-mode") }) { Text("Read only") }
                        }
                    }
                }
            }
        }

        Div({ classes("row", "g-4") }) {
            Div({ classes("col-12", "col-xl-5") }) {
                SectionCard(
                    title = "Топики",
                    subtitle = "Кластер: ${selectedCluster.name}",
                ) {
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

                    when {
                        state.topicsLoading && topicsResponse == null -> {
                            P({ classes("text-secondary", "small", "mb-0") }) {
                                Text("Загружаю список топиков.")
                            }
                        }

                        topicsResponse == null || topicsResponse.topics.isEmpty() -> {
                            P({ classes("text-secondary", "small", "mb-0") }) {
                                Text("Для выбранного кластера топиков по текущему фильтру нет.")
                            }
                        }

                        else -> {
                            Div({ classes("table-responsive", "kafka-topic-table-wrap") }) {
                                Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-topic-table") }) {
                                    Thead {
                                        Tr {
                                            Th { Text("Topic") }
                                            Th { Text("Partitions") }
                                            Th { Text("RF") }
                                            Th { Text("Retention") }
                                        }
                                    }
                                    Tbody {
                                        topicsResponse.topics.forEach { topic ->
                                            Tr({
                                                classes(
                                                    if (topic.name == state.selectedTopicName) {
                                                        "kafka-topic-row-active"
                                                    } else {
                                                        "kafka-topic-row-idle"
                                                    },
                                                )
                                            }) {
                                                Td {
                                                    A(attrs = {
                                                        classes("kafka-topic-link")
                                                        href(buildKafkaPageHref(selectedCluster.id, topic.name))
                                                    }) {
                                                        Text(topic.name)
                                                    }
                                                    if (topic.internal) {
                                                        Div({ classes("kafka-topic-meta") }) { Text("internal") }
                                                    }
                                                }
                                                Td { Text(topic.partitionCount.toString()) }
                                                Td { Text(topic.replicationFactor.toString()) }
                                                Td { Text(formatRetention(topic.retentionMs, topic.retentionBytes)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Div({ classes("col-12", "col-xl-7") }) {
                when {
                    state.topicOverviewLoading && selectedTopic == null -> {
                        LoadingStateCard(
                            title = "Topic overview",
                            text = "Загружаю metadata выбранного топика.",
                        )
                    }

                    selectedTopic == null -> {
                        EmptyStateCard(
                            title = "Topic overview",
                            text = "Выбери топик из списка слева, чтобы увидеть partition summary и topic config.",
                        )
                    }

                    else -> {
                        SectionCard(
                            title = selectedTopic.topic.name,
                            subtitle = "${selectedTopic.cluster.name} · ${selectedTopic.cluster.securityProtocol} · ${selectedTopic.cluster.bootstrapServers}",
                            actions = {
                                Button(attrs = {
                                    classes("btn", "btn-outline-secondary", "btn-sm")
                                    attr("type", "button")
                                    onClick { onReloadTopicOverview(selectedTopic.topic.name) }
                                }) {
                                    Text("Обновить overview")
                                }
                            },
                        ) {
                            Div({ classes("kafka-topic-summary-grid") }) {
                                KafkaOverviewMetric("Partitions", selectedTopic.topic.partitionCount.toString())
                                KafkaOverviewMetric("Replication", selectedTopic.topic.replicationFactor.toString())
                                KafkaOverviewMetric("Cleanup policy", selectedTopic.topic.cleanupPolicy ?: "default")
                                KafkaOverviewMetric("Retention", formatRetention(selectedTopic.topic.retentionMs, selectedTopic.topic.retentionBytes))
                            }

                            Div({ classes("table-responsive", "mt-3") }) {
                                Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-partition-table") }) {
                                    Thead {
                                        Tr {
                                            Th { Text("Partition") }
                                            Th { Text("Leader") }
                                            Th { Text("Replicas") }
                                            Th { Text("ISR") }
                                            Th { Text("Earliest") }
                                            Th { Text("Latest") }
                                        }
                                    }
                                    Tbody {
                                        selectedTopic.partitions.forEach { partition ->
                                            Tr {
                                                Td { Text(partition.partition.toString()) }
                                                Td { Text(partition.leaderId?.toString() ?: "n/a") }
                                                Td { Text(partition.replicaCount.toString()) }
                                                Td { Text(partition.inSyncReplicaCount.toString()) }
                                                Td { Text(partition.earliestOffset?.toString() ?: "n/a") }
                                                Td { Text(partition.latestOffset?.toString() ?: "n/a") }
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

@Composable
private fun KafkaOverviewMetric(
    title: String,
    value: String,
) {
    Div({ classes("kafka-topic-summary-item") }) {
        Div({ classes("kafka-topic-summary-label") }) { Text(title) }
        Div({ classes("kafka-topic-summary-value") }) { Text(value) }
    }
}

private fun formatRetention(
    retentionMs: Long?,
    retentionBytes: Long?,
): String {
    val timePart = retentionMs?.let { "${it} ms" }
    val sizePart = retentionBytes?.let { "${it} B" }
    return listOfNotNull(timePart, sizePart).joinToString(" · ").ifBlank { "default" }
}
