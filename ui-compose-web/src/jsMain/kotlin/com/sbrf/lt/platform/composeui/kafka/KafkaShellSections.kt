package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun KafkaClusterSidebar(
    info: KafkaToolInfoResponse,
    state: KafkaPageState,
    selectedCluster: KafkaClusterCatalogEntryResponse,
    onClusterSectionChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Div({ classes("kafka-sidebar") }) {
        SectionCard(
            title = "Кластеры",
            subtitle = "Catalog из ui.kafka.clusters",
        ) {
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

        SectionCard(
            title = selectedCluster.name,
            subtitle = "Cluster navigation",
        ) {
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
