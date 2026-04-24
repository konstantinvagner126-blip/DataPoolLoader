package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

@Composable
internal fun KafkaClusterCatalogSection(
    info: KafkaToolInfoResponse,
    state: KafkaPageState,
    selectedClusterId: String,
) {
    SectionCard(
        title = "Кластеры Kafka",
        subtitle = "Config-driven catalog. Кластер выбирается из ui.kafka.clusters без ad-hoc connection form.",
    ) {
        Div({ classes("kafka-cluster-strip") }) {
            info.clusters.forEach { cluster ->
                A(attrs = {
                    classes(
                        "kafka-cluster-link",
                        if (cluster.id == selectedClusterId) "kafka-cluster-link-active" else "kafka-cluster-link-idle",
                    )
                    href(
                        buildKafkaPageHref(
                            clusterId = cluster.id,
                            topicQuery = state.topicQuery,
                            activePane = state.activePane,
                            messageReadScope = state.messageReadScope,
                            messageReadMode = state.messageReadMode,
                            selectedMessagePartition = state.selectedMessagePartition,
                        ),
                    )
                }) {
                    Div({ classes("kafka-cluster-link-top") }) {
                        Span({ classes("kafka-cluster-name") }) { Text(cluster.name) }
                        Span({ classes("kafka-cluster-protocol") }) { Text(cluster.securityProtocol) }
                    }
                    Div({ classes("kafka-cluster-bootstrap") }) { Text(cluster.bootstrapServers) }
                    if (cluster.readOnly) {
                        Div({ classes("kafka-cluster-mode") }) { Text("Read only") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KafkaTopicsCatalogSection(
    state: KafkaPageState,
    selectedCluster: KafkaClusterCatalogEntryResponse,
    topicsResponse: KafkaTopicsCatalogResponse?,
    onTopicQueryChange: (String) -> Unit,
    onApplyTopicQuery: () -> Unit,
) {
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
                                            href(
                                                buildKafkaPageHref(
                                                    clusterId = selectedCluster.id,
                                                    topicName = topic.name,
                                                    topicQuery = state.topicQuery,
                                                    activePane = state.activePane,
                                                    messageReadScope = state.messageReadScope,
                                                    messageReadMode = state.messageReadMode,
                                                    selectedMessagePartition = state.selectedMessagePartition,
                                                ),
                                            )
                                        }) {
                                            Text(topic.name)
                                        }
                                        if (topic.internal) {
                                            Div({ classes("kafka-topic-meta") }) { Text("internal") }
                                        }
                                    }
                                    Td { Text(topic.partitionCount.toString()) }
                                    Td { Text(topic.replicationFactor.toString()) }
                                    Td { Text(formatKafkaRetention(topic.retentionMs, topic.retentionBytes)) }
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
internal fun KafkaTopicOverviewSection(
    state: KafkaPageState,
    selectedTopic: KafkaTopicOverviewResponse,
    onReloadTopicOverview: (String) -> Unit,
    onPaneChange: (String) -> Unit,
    onMessagePartitionChange: (Int) -> Unit,
    onMessageReadScopeChange: (String) -> Unit,
    onMessageReadModeChange: (String) -> Unit,
    onMessageLimitChange: (String) -> Unit,
    onMessageOffsetChange: (String) -> Unit,
    onMessageTimestampChange: (String) -> Unit,
    onReadMessages: () -> Unit,
    onProducePartitionChange: (String) -> Unit,
    onProduceKeyChange: (String) -> Unit,
    onProduceHeadersChange: (String) -> Unit,
    onProducePayloadChange: (String) -> Unit,
    onProduceMessage: () -> Unit,
    onReloadSettings: () -> Unit,
    onAddSettingsCluster: () -> Unit,
    onRemoveSettingsCluster: (Int) -> Unit,
    onSettingsClusterChange: (Int, KafkaEditableClusterResponse) -> Unit,
    onTestSettingsConnection: (Int) -> Unit,
    onSaveSettings: () -> Unit,
) {
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
            KafkaOverviewMetric("Retention", formatKafkaRetention(selectedTopic.topic.retentionMs, selectedTopic.topic.retentionBytes))
        }

        KafkaPaneTabs(
            activePane = state.activePane,
            onPaneChange = onPaneChange,
        )

        KafkaConsumerGroupsSection(selectedTopic.consumerGroups)

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

        if (state.activePane == "messages") {
            KafkaMessageBrowserSection(
                state = state,
                onMessagePartitionChange = onMessagePartitionChange,
                onMessageReadScopeChange = onMessageReadScopeChange,
                onMessageReadModeChange = onMessageReadModeChange,
                onMessageLimitChange = onMessageLimitChange,
                onMessageOffsetChange = onMessageOffsetChange,
                onMessageTimestampChange = onMessageTimestampChange,
                onReadMessages = onReadMessages,
            )
        }

        if (state.activePane == "produce") {
            KafkaProduceSection(
                state = state,
                onProducePartitionChange = onProducePartitionChange,
                onProduceKeyChange = onProduceKeyChange,
                onProduceHeadersChange = onProduceHeadersChange,
                onProducePayloadChange = onProducePayloadChange,
                onProduceMessage = onProduceMessage,
            )
        }

        if (state.activePane == "settings") {
            KafkaSettingsSection(
                state = state,
                onReloadSettings = onReloadSettings,
                onAddSettingsCluster = onAddSettingsCluster,
                onRemoveSettingsCluster = onRemoveSettingsCluster,
                onSettingsClusterChange = onSettingsClusterChange,
                onTestSettingsConnection = onTestSettingsConnection,
                onSaveSettings = onSaveSettings,
            )
        }
    }
}

@Composable
internal fun KafkaPaneTabs(
    activePane: String,
    onPaneChange: (String) -> Unit,
) {
    Div({ classes("kafka-pane-tabs") }) {
        KafkaPaneTabButton("overview", "Overview", activePane, onPaneChange)
        KafkaPaneTabButton("messages", "Messages", activePane, onPaneChange)
        KafkaPaneTabButton("produce", "Produce", activePane, onPaneChange)
        KafkaPaneTabButton("settings", "Settings", activePane, onPaneChange)
    }
}

@Composable
internal fun KafkaPaneTabButton(
    pane: String,
    label: String,
    activePane: String,
    onPaneChange: (String) -> Unit,
) {
    Button(attrs = {
        classes(
            "btn",
            "btn-sm",
            if (pane == activePane) "btn-dark" else "btn-outline-secondary",
        )
        attr("type", "button")
        onClick { onPaneChange(pane) }
    }) {
        Text(label)
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
