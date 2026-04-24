package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun KafkaToolHeader(
    info: KafkaToolInfoResponse?,
    selectedCluster: KafkaClusterCatalogEntryResponse?,
) {
    Div({ classes("kafka-tool-header") }) {
        Div({ classes("kafka-tool-identity") }) {
            Div({
                classes("kafka-tool-icon")
                attr("aria-hidden", "true")
            })
            Div({ classes("kafka-tool-copy") }) {
                Div({ classes("kafka-tool-crumbs") }) {
                    A(attrs = {
                        classes("kafka-tool-crumb")
                        href("/")
                    }) { Text("Главная") }
                    Span({ classes("kafka-tool-crumb", "active") }) { Text("Kafka") }
                    if (selectedCluster != null) {
                        Span({ classes("kafka-tool-crumb") }) { Text(selectedCluster.name) }
                    }
                }
                Div({ classes("kafka-tool-title") }) { Text("Kafka") }
                Div({ classes("kafka-tool-subtitle") }) {
                    Text(
                        selectedCluster?.let {
                            "${it.name} · ${it.bootstrapServers}"
                        } ?: "Cluster-first tool для локальной работы с Kafka-каталогом и bounded operations.",
                    )
                }
            }
        }

        Div({ classes("kafka-tool-status") }) {
            if (selectedCluster == null) {
                Span({ classes("kafka-tool-chip", "warn") }) { Text("clusters not configured") }
            } else {
                Span({ classes("kafka-tool-chip", "accent") }) { Text(selectedCluster.securityProtocol) }
                Span({
                    classes(
                        "kafka-tool-chip",
                        if (selectedCluster.readOnly) "lock" else "ok",
                    )
                }) {
                    Text(if (selectedCluster.readOnly) "read only" else "write enabled")
                }
                Span({ classes("kafka-tool-chip") }) { Text("max read ${info?.maxRecordsPerRead ?: "?"}") }
                Span({ classes("kafka-tool-chip") }) { Text("bounded read") }
            }
        }
    }
}

@Composable
internal fun KafkaClusterSidebar(
    info: KafkaToolInfoResponse,
    state: KafkaPageState,
    selectedCluster: KafkaClusterCatalogEntryResponse,
    onClusterSectionChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Div({ classes("kafka-sidebar", "kafka-rail") }) {
        Div({ classes("kafka-rail-section") }) {
            Div({ classes("kafka-rail-label") }) { Text("Clusters") }
            Div({ classes("kafka-cluster-list") }) {
                info.clusters.forEach { cluster ->
                    A(attrs = {
                        classes(
                            "kafka-cluster-sidebar-link",
                            if (cluster.id == selectedCluster.id) {
                                "kafka-cluster-sidebar-link-active"
                            } else {
                                "kafka-cluster-sidebar-link-idle"
                            },
                        )
                        href(
                            buildKafkaPageHref(
                                clusterId = cluster.id,
                                clusterSection = state.clusterSection,
                                topicQuery = state.topicQuery,
                                activePane = if (state.activePane == "cluster-settings") "cluster-settings" else "overview",
                                messageReadScope = state.messageReadScope,
                                messageReadMode = state.messageReadMode,
                                selectedMessagePartition = state.selectedMessagePartition,
                            ),
                        )
                    }) {
                        Div({ classes("kafka-cluster-sidebar-top") }) {
                            Span({ classes("kafka-cluster-name") }) { Text(cluster.name) }
                            Span({ classes("kafka-rail-badge", "accent") }) { Text(cluster.securityProtocol) }
                        }
                        Div({ classes("kafka-cluster-bootstrap") }) { Text(cluster.bootstrapServers) }
                        if (cluster.readOnly) {
                            Div({ classes("kafka-rail-badge", "lock") }) { Text("Read only") }
                        }
                    }
                }
            }
        }

        Div({ classes("kafka-rail-section") }) {
            Div({ classes("kafka-rail-label") }) { Text("Navigation") }
            Div({ classes("kafka-section-nav") }) {
                KafkaClusterSectionButton(
                    section = "topics",
                    title = "Topics",
                    subtitle = "Catalog и topic details",
                    activeSection = state.clusterSection,
                    onClusterSectionChange = onClusterSectionChange,
                )
                KafkaClusterSectionButton(
                    section = "consumer-groups",
                    title = "Consumer Groups",
                    subtitle = "Cluster-level groups view",
                    activeSection = state.clusterSection,
                    onClusterSectionChange = onClusterSectionChange,
                )
                KafkaClusterSectionButton(
                    section = "brokers",
                    title = "Brokers",
                    subtitle = "Read-only broker metadata",
                    activeSection = state.clusterSection,
                    onClusterSectionChange = onClusterSectionChange,
                )
            }
            Div({ classes("kafka-sidebar-actions") }) {
                Button(attrs = {
                    classes(
                        "btn",
                        if (state.activePane == "cluster-settings") "btn-dark" else "btn-outline-secondary",
                    )
                    attr("type", "button")
                    onClick { onOpenSettings() }
                }) {
                    Text("Настройки")
                }
            }
        }

        state.selectedTopicName?.let { topicName ->
            Div({ classes("kafka-rail-section") }) {
                Div({ classes("kafka-rail-label") }) { Text("Selected topic") }
                A(attrs = {
                    classes("kafka-topic-list-item", "active")
                    href(
                        buildKafkaPageHref(
                            clusterId = selectedCluster.id,
                            clusterSection = "topics",
                            topicName = topicName,
                            topicQuery = state.topicQuery,
                            activePane = state.activePane,
                            messageReadScope = state.messageReadScope,
                            messageReadMode = state.messageReadMode,
                            selectedMessagePartition = state.selectedMessagePartition,
                        ),
                    )
                }) {
                    Div({ classes("kafka-topic-list-line") }) {
                        Span({ classes("kafka-topic-list-name") }) { Text(topicName) }
                    }
                    Div({ classes("kafka-topic-list-meta") }) { Text(state.activePane) }
                }
            }
        }
    }
}

@Composable
private fun KafkaClusterSectionButton(
    section: String,
    title: String,
    subtitle: String,
    activeSection: String,
    onClusterSectionChange: (String) -> Unit,
) {
    Button(attrs = {
        classes(
            "kafka-section-link",
            if (section == activeSection) "kafka-section-link-active" else "kafka-section-link-idle",
        )
        attr("type", "button")
        onClick { onClusterSectionChange(section) }
    }) {
        Span({ classes("kafka-section-link-title") }) { Text(title) }
        Span({ classes("kafka-section-link-subtitle") }) { Text(subtitle) }
    }
}
