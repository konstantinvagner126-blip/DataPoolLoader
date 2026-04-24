package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
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
internal fun KafkaTopicDetailsPageSection(
    state: KafkaPageState,
    selectedCluster: KafkaClusterCatalogEntryResponse,
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
) {
    Div({ classes("kafka-topic-details-shell") }) {
        Div({ classes("kafka-topic-details-header") }) {
            Div({ classes("kafka-topic-details-identity") }) {
                Div({ classes("kafka-topic-details-breadcrumbs") }) {
                    A(attrs = {
                        classes("kafka-topic-details-back-link")
                        href(
                            buildKafkaPageHref(
                                clusterId = selectedCluster.id,
                                clusterSection = "topics",
                                topicQuery = state.topicQuery,
                                activePane = "overview",
                                messageReadScope = state.messageReadScope,
                                messageReadMode = state.messageReadMode,
                                selectedMessagePartition = state.selectedMessagePartition,
                            ),
                        )
                    }) {
                        Text("Topics")
                    }
                    Span({ classes("kafka-topic-details-breadcrumb-separator") }) { Text("/") }
                    Span({ classes("kafka-topic-details-breadcrumb-current") }) {
                        Text(selectedTopic.topic.name)
                    }
                }

                Div({ classes("kafka-topic-details-title-row") }) {
                    Div({ classes("kafka-topic-details-title") }) { Text(selectedTopic.topic.name) }
                    if (selectedTopic.topic.internal) {
                        Span({ classes("kafka-topic-flag") }) { Text("internal") }
                    }
                }

                P({ classes("kafka-topic-details-subtitle") }) {
                    Text(
                        "${selectedTopic.cluster.name} · ${selectedTopic.cluster.securityProtocol} · ${selectedTopic.cluster.bootstrapServers}",
                    )
                }
            }

            Div({ classes("kafka-topic-details-actions") }) {
                Button(attrs = {
                    classes("btn", "btn-outline-secondary", "btn-sm")
                    attr("type", "button")
                    onClick { onReloadTopicOverview(selectedTopic.topic.name) }
                }) {
                    Text("Обновить")
                }
            }
        }

        KafkaTopicTabStrip(
            activePane = state.activePane,
            onPaneChange = onPaneChange,
        )

        when (state.activePane) {
            "messages" -> {
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

            "consumers" -> {
                SectionCard(
                    title = "Consumers",
                    subtitle = "Topic-scoped consumer groups и partition lag для ${selectedTopic.topic.name}.",
                ) {
                    KafkaConsumerGroupsSection(selectedTopic.consumerGroups)
                }
            }

            "settings" -> {
                KafkaTopicSettingsSection(selectedTopic)
            }

            "produce" -> {
                KafkaProduceSection(
                    state = state,
                    onProducePartitionChange = onProducePartitionChange,
                    onProduceKeyChange = onProduceKeyChange,
                    onProduceHeadersChange = onProduceHeadersChange,
                    onProducePayloadChange = onProducePayloadChange,
                    onProduceMessage = onProduceMessage,
                )
            }

            else -> {
                KafkaTopicOverviewTabSection(selectedTopic)
            }
        }
    }
}

@Composable
private fun KafkaTopicTabStrip(
    activePane: String,
    onPaneChange: (String) -> Unit,
) {
    Div({ classes("kafka-topic-tab-strip") }) {
        KafkaTopicTabButton("overview", "Overview", activePane, onPaneChange)
        KafkaTopicTabButton("messages", "Messages", activePane, onPaneChange)
        KafkaTopicTabButton("consumers", "Consumers", activePane, onPaneChange)
        KafkaTopicTabButton("settings", "Settings", activePane, onPaneChange)
        KafkaTopicTabButton("produce", "Produce", activePane, onPaneChange)
    }
}

@Composable
private fun KafkaTopicTabButton(
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
private fun KafkaTopicOverviewTabSection(
    selectedTopic: KafkaTopicOverviewResponse,
) {
    SectionCard(
        title = "Overview",
        subtitle = "Базовая metadata по topic и его partition layout.",
    ) {
        Div({ classes("kafka-topic-summary-grid") }) {
            KafkaOverviewMetric("Partitions", selectedTopic.topic.partitionCount.toString())
            KafkaOverviewMetric("Replication", selectedTopic.topic.replicationFactor.toString())
            KafkaOverviewMetric("Cleanup policy", selectedTopic.topic.cleanupPolicy ?: "default")
            KafkaOverviewMetric("Retention", formatKafkaRetention(selectedTopic.topic.retentionMs, selectedTopic.topic.retentionBytes))
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

@Composable
private fun KafkaTopicSettingsSection(
    selectedTopic: KafkaTopicOverviewResponse,
) {
    SectionCard(
        title = "Settings",
        subtitle = "Read-only topic configuration shell без destructive admin actions.",
    ) {
        Div({ classes("kafka-topic-settings-grid") }) {
            KafkaOverviewMetric("Cleanup policy", selectedTopic.topic.cleanupPolicy ?: "default")
            KafkaOverviewMetric("Retention", formatKafkaRetention(selectedTopic.topic.retentionMs, selectedTopic.topic.retentionBytes))
            KafkaOverviewMetric("Internal", if (selectedTopic.topic.internal) "yes" else "no")
            KafkaOverviewMetric("Read only cluster", if (selectedTopic.cluster.readOnly) "yes" else "no")
        }

        P({ classes("kafka-placeholder-note", "mt-3") }) {
            Text("Изменение topic settings, create/delete topic и другие admin actions остаются вне текущего redesign scope.")
        }
    }
}
