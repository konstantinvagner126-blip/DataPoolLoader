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
    onAddProduceHeader: () -> Unit,
    onRemoveProduceHeader: (Int) -> Unit,
    onProduceHeaderNameChange: (Int, String) -> Unit,
    onProduceHeaderValueChange: (Int, String) -> Unit,
    onProducePayloadChange: (String) -> Unit,
    onProduceMessage: () -> Unit,
) {
    Div({ classes("kafka-topic-details-shell") }) {
        Div({ classes("kafka-topic-header-panel") }) {
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
                            Text("Back to topics")
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
                    Div({ classes("kafka-topic-details-meta") }) {
                        Span({ classes("kafka-tool-chip", "accent") }) {
                            Text("${selectedTopic.topic.partitionCount} partitions")
                        }
                        Span({ classes("kafka-tool-chip") }) {
                            Text("replication ${selectedTopic.topic.replicationFactor}")
                        }
                        Span({ classes("kafka-tool-chip") }) {
                            Text(selectedTopic.topic.cleanupPolicy ?: "default cleanup")
                        }
                    }
                }

                Div({ classes("kafka-topic-details-actions") }) {
                    Button(attrs = {
                        classes("btn", "btn-outline-secondary", "btn-sm")
                        attr("type", "button")
                        onClick { onReloadTopicOverview(selectedTopic.topic.name) }
                    }) {
                        Text("Refresh topic")
                    }
                }
            }

            KafkaTopicTabStrip(
                activePane = state.activePane,
                onPaneChange = onPaneChange,
            )
        }

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
                    onAddProduceHeader = onAddProduceHeader,
                    onRemoveProduceHeader = onRemoveProduceHeader,
                    onProduceHeaderNameChange = onProduceHeaderNameChange,
                    onProduceHeaderValueChange = onProduceHeaderValueChange,
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
            "kafka-topic-tab-button",
            if (pane == activePane) "kafka-topic-tab-button-active" else "kafka-topic-tab-button-idle",
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
    val isrTotal = selectedTopic.partitions.fold(0) { sum, partition -> sum + partition.inSyncReplicaCount }
    val replicaTotal = selectedTopic.partitions.fold(0) { sum, partition -> sum + partition.replicaCount }
    val latestTotal = selectedTopic.partitions.fold(0L) { sum, partition -> sum + (partition.latestOffset ?: 0L) }

    Div({ classes("kafka-topic-overview-layout") }) {
        Div({ classes("kafka-topic-overview-panel") }) {
            Div({ classes("kafka-topic-overview-head") }) {
                Div {
                    Div({ classes("kafka-topic-overview-title") }) { Text("Topic overview") }
                    P({ classes("kafka-topic-overview-subtitle") }) {
                        Text("Read-only metadata and partition offsets.")
                    }
                }
                Span({
                    classes(
                        "kafka-tool-chip",
                        when (selectedTopic.consumerGroups.status.uppercase()) {
                            "ERROR" -> "warn"
                            "AVAILABLE" -> "ok"
                            else -> "neutral"
                        },
                    )
                }) {
                    Text("consumer metadata ${selectedTopic.consumerGroups.status.lowercase()}")
                }
            }

            Div({ classes("kafka-topic-summary-grid") }) {
                KafkaOverviewMetric("Partitions", selectedTopic.topic.partitionCount.toString(), "topic layout")
                KafkaOverviewMetric("Replication", selectedTopic.topic.replicationFactor.toString(), "ISR $isrTotal/$replicaTotal")
                KafkaOverviewMetric("Cleanup", selectedTopic.topic.cleanupPolicy ?: "default", "topic config")
                KafkaOverviewMetric("Latest offset", formatKafkaCompactNumber(latestTotal), "across partitions")
                KafkaOverviewMetric(
                    "Retention",
                    formatKafkaRetention(selectedTopic.topic.retentionMs, selectedTopic.topic.retentionBytes),
                    "time / size",
                )
            }

            Div({ classes("table-responsive", "kafka-partition-table-wrap") }) {
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
                                Td({ classes("kafka-topic-value-cell") }) { Text(partition.partition.toString()) }
                                Td({ classes("kafka-topic-value-cell") }) { Text(partition.leaderId?.toString() ?: "n/a") }
                                Td({ classes("kafka-topic-value-cell") }) { Text(partition.replicaCount.toString()) }
                                Td({ classes("kafka-topic-value-cell") }) { Text(partition.inSyncReplicaCount.toString()) }
                                Td({ classes("kafka-topic-value-cell") }) { Text(partition.earliestOffset?.toString() ?: "n/a") }
                                Td({ classes("kafka-topic-value-cell") }) { Text(partition.latestOffset?.toString() ?: "n/a") }
                            }
                        }
                    }
                }
            }
        }

        KafkaPartitionLoadPanel(selectedTopic)
    }
}

@Composable
private fun KafkaPartitionLoadPanel(
    selectedTopic: KafkaTopicOverviewResponse,
) {
    val maxLatestOffset = selectedTopic.partitions.fold(0L) { maxOffset, partition ->
        maxOf(maxOffset, partition.latestOffset ?: 0L)
    }
    Div({ classes("kafka-partition-load-panel") }) {
        Div({ classes("kafka-topic-overview-head") }) {
            Div {
                Div({ classes("kafka-topic-overview-title") }) { Text("Partition load") }
                P({ classes("kafka-topic-overview-subtitle") }) {
                    Text("Latest offsets by partition")
                }
            }
        }
        Div({ classes("kafka-partition-bars") }) {
            selectedTopic.partitions.forEach { partition ->
                val latestOffset = partition.latestOffset ?: 0L
                val fillPercent = partitionLatestOffsetPercent(latestOffset, maxLatestOffset)
                Div({ classes("kafka-partition-bar-row") }) {
                    Span({ classes("kafka-partition-bar-label") }) { Text("p${partition.partition}") }
                    Div({ classes("kafka-partition-bar-track") }) {
                        Div({
                            classes("kafka-partition-bar-fill")
                            attr("style", "width: $fillPercent%;")
                        })
                    }
                    Span({ classes("kafka-partition-bar-value") }) { Text(latestOffset.toString()) }
                }
            }
        }
        selectedTopic.consumerGroups.message?.let { message ->
            P({ classes("kafka-partition-load-note") }) { Text(message) }
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
            Text("Изменение topic settings, delete topic и другие destructive admin actions остаются вне текущего redesign scope.")
        }
    }
}

private fun formatKafkaCompactNumber(value: Long): String =
    when {
        value >= 1_000_000_000 -> "${value / 1_000_000_000}B"
        value >= 1_000_000 -> "${value / 1_000_000}M"
        value >= 1_000 -> "${value / 1_000}K"
        else -> value.toString()
    }

private fun partitionLatestOffsetPercent(
    latestOffset: Long,
    maxLatestOffset: Long,
): Int {
    if (latestOffset <= 0L || maxLatestOffset <= 0L) {
        return 0
    }
    return ((latestOffset * 100) / maxLatestOffset).toInt().coerceIn(4, 100)
}
