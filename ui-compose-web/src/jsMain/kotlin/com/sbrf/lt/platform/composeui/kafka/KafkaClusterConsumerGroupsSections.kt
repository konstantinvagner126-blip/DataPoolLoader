package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
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
internal fun KafkaClusterConsumerGroupsSection(
    state: KafkaPageState,
    selectedCluster: KafkaClusterCatalogEntryResponse,
    onReloadConsumerGroups: () -> Unit,
) {
    val consumerGroups = state.consumerGroups
    if (state.consumerGroupsLoading && consumerGroups == null) {
        LoadingStateCard(
            title = "Consumer Groups",
            text = "Загружаю cluster-level consumer groups и lag metadata.",
        )
        return
    }

    Div({ classes("kafka-cluster-metadata-panel") }) {
        Div({ classes("kafka-cluster-metadata-head") }) {
            Div {
                Div({ classes("kafka-message-pane-title") }) { Text("Consumer Groups") }
                P({ classes("kafka-message-pane-subtitle") }) {
                    Text("${selectedCluster.name} · cluster-level lag metadata")
                }
            }
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (state.consumerGroupsLoading) {
                    attr("disabled", "disabled")
                }
                onClick { onReloadConsumerGroups() }
            }) {
                Text(if (state.consumerGroupsLoading) "Обновляю..." else "Обновить")
            }
        }

        state.consumerGroupsError?.let { message ->
            AlertBanner(message, "warning")
        }

        when {
            consumerGroups == null -> {
                P({ classes("text-secondary", "small", "mb-0") }) {
                    Text("Для выбранного кластера consumer groups еще не загружены.")
                }
            }

            consumerGroups.status == "ERROR" -> {
                AlertBanner(
                    consumerGroups.message ?: "Consumer groups metadata недоступна.",
                    "warning",
                )
            }

            consumerGroups.status == "EMPTY" || consumerGroups.groups.isEmpty() -> {
                P({ classes("text-secondary", "small", "mb-0") }) {
                    Text(consumerGroups.message ?: "В кластере нет consumer groups.")
                }
            }

            else -> {
                consumerGroups.message?.let { message ->
                    AlertBanner(message, "warning")
                }

                KafkaClusterConsumerGroupsSummary(consumerGroups)

                Div({ classes("table-responsive", "kafka-cluster-metadata-table-wrap") }) {
                    Table({
                        classes(
                            "table",
                            "table-sm",
                            "align-middle",
                            "mb-0",
                            "kafka-cluster-consumer-group-table",
                        )
                    }) {
                        Thead {
                            Tr {
                                Th { Text("Group ID") }
                                Th { Text("State") }
                                Th { Text("Members") }
                                Th { Text("Topics") }
                                Th { Text("Lag") }
                                Th { Text("Details") }
                            }
                        }
                        Tbody {
                            consumerGroups.groups.forEach { group ->
                                KafkaClusterConsumerGroupRow(
                                    cluster = selectedCluster,
                                    state = state,
                                    group = group,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KafkaClusterConsumerGroupRow(
    cluster: KafkaClusterCatalogEntryResponse,
    state: KafkaPageState,
    group: KafkaClusterConsumerGroupSummaryResponse,
) {
    var expanded by remember(group.groupId) { mutableStateOf(false) }
    Tr {
        Td {
            Div({ classes("kafka-consumer-group-name") }) { Text(group.groupId) }
            Div({ classes("kafka-consumer-group-status-row") }) {
                KafkaClusterStatusBadge(group.state ?: "n/a", "neutral")
                if (!group.metadataAvailable) {
                    KafkaClusterStatusBadge("metadata partial", "warn")
                }
                if (group.lagStatus != "OK") {
                    KafkaClusterStatusBadge(group.lagStatus.lowercase().replace('_', ' '), "warn")
                }
            }
            group.note?.let { note ->
                P({ classes("kafka-consumer-group-note", "mb-0") }) { Text(note) }
            }
        }
        Td { KafkaClusterStatusBadge(group.state ?: "n/a", "neutral") }
        Td { Text(group.memberCount?.toString() ?: "n/a") }
        Td { Text(group.topics.size.toString()) }
        Td {
            Div({ classes("kafka-cluster-consumer-group-lag") }) {
                Text(group.totalLag?.toString() ?: "n/a")
            }
        }
        Td {
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                onClick { expanded = !expanded }
            }) {
                Text(if (expanded) "Скрыть" else "Показать")
            }
        }
    }
    if (expanded) {
        Tr {
            Td(attrs = {
                attr("colspan", "6")
            }) {
                Div({ classes("kafka-cluster-consumer-group-detail") }) {
                    if (group.topics.isEmpty()) {
                        P({ classes("text-secondary", "small", "mb-0") }) {
                            Text("Kafka не вернула committed offsets для этой consumer group.")
                        }
                    } else {
                        KafkaClusterConsumerGroupTopicSummary(group)
                        Div({ classes("table-responsive") }) {
                            Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-cluster-consumer-topic-table") }) {
                                Thead {
                                    Tr {
                                        Th { Text("Topic") }
                                        Th { Text("Partitions") }
                                        Th { Text("Lag") }
                                        Th { Text("Partition lag") }
                                        Th { Text("Open") }
                                    }
                                }
                                Tbody {
                                    group.topics.forEach { topic ->
                                        Tr {
                                            Td { Text(topic.topicName) }
                                            Td { Text(topic.partitionCount.toString()) }
                                            Td { Text(topic.totalLag?.toString() ?: "n/a") }
                                            Td {
                                                KafkaClusterPartitionLagList(topic.partitions)
                                            }
                                            Td {
                                                A(attrs = {
                                                    classes("kafka-topic-details-back-link")
                                                    href(
                                                        buildKafkaPageHref(
                                                            clusterId = cluster.id,
                                                            clusterSection = "topics",
                                                            topicName = topic.topicName,
                                                            topicQuery = state.topicQuery,
                                                            activePane = "consumers",
                                                            messageReadScope = state.messageReadScope,
                                                            messageReadMode = state.messageReadMode,
                                                            selectedMessagePartition = state.selectedMessagePartition,
                                                        ),
                                                    )
                                                }) {
                                                    Text("Topic consumers")
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
}

@Composable
private fun KafkaClusterConsumerGroupsSummary(
    consumerGroups: KafkaClusterConsumerGroupsCatalogResponse,
) {
    val totalMembers = consumerGroups.groups.mapNotNull { it.memberCount }.sum()
    val totalTopics = consumerGroups.groups.flatMap { group -> group.topics.map { it.topicName } }.distinct().size
    val lagKnown = consumerGroups.groups.mapNotNull { it.totalLag }
    val lagTotal = lagKnown.sum()
    val warningCount = consumerGroups.groups.count { !it.metadataAvailable || it.lagStatus != "OK" }

    Div({ classes("kafka-cluster-metadata-summary-grid") }) {
        KafkaOverviewMetric("Groups", consumerGroups.groups.size.toString())
        KafkaOverviewMetric("Members", totalMembers.toString())
        KafkaOverviewMetric("Topics", totalTopics.toString())
        KafkaOverviewMetric("Total lag", if (lagKnown.isEmpty()) "n/a" else lagTotal.toString())
        KafkaOverviewMetric("Warnings", warningCount.toString())
    }
}

@Composable
private fun KafkaClusterConsumerGroupTopicSummary(
    group: KafkaClusterConsumerGroupSummaryResponse,
) {
    Div({ classes("kafka-cluster-consumer-topic-summary") }) {
        KafkaOverviewMetric("Topics", group.topics.size.toString())
        KafkaOverviewMetric("Members", group.memberCount?.toString() ?: "n/a")
        KafkaOverviewMetric("Total lag", group.totalLag?.toString() ?: "n/a")
        KafkaOverviewMetric("Metadata", if (group.metadataAvailable) "available" else "partial")
    }
}

@Composable
private fun KafkaClusterPartitionLagList(
    partitions: List<KafkaTopicConsumerGroupPartitionLagResponse>,
) {
    if (partitions.isEmpty()) {
        Span({ classes("kafka-topic-meta") }) { Text("n/a") }
        return
    }
    Div({ classes("kafka-cluster-partition-lag-list") }) {
        partitions.take(8).forEach { partition ->
            Span({ classes("kafka-cluster-partition-lag-pill") }) {
                Text("${partition.partition}: ${partition.lag ?: "n/a"}")
            }
        }
        if (partitions.size > 8) {
            Span({ classes("kafka-cluster-partition-lag-pill", "muted") }) {
                Text("+${partitions.size - 8}")
            }
        }
    }
}

@Composable
internal fun KafkaClusterStatusBadge(
    label: String,
    level: String,
) {
    Span({
        classes(
            "kafka-cluster-status-badge",
            when (level) {
                "ok" -> "ok"
                "warn" -> "warn"
                "danger" -> "danger"
                else -> "neutral"
            },
        )
    }) {
        Text(label)
    }
}
