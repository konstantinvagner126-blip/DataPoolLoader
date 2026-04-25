package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.scope
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
internal fun KafkaClusterBrokersSection(
    state: KafkaPageState,
    selectedCluster: KafkaClusterCatalogEntryResponse,
    onReloadBrokers: () -> Unit,
) {
    val brokersError = state.brokersError
    Div({ classes("kafka-cluster-metadata-panel") }) {
        Div({ classes("kafka-cluster-metadata-head") }) {
            Div {
                Div({ classes("kafka-message-pane-title") }) { Text("Brokers") }
                P({ classes("kafka-message-pane-subtitle") }) {
                    Text("${selectedCluster.name} · read-only broker metadata")
                }
            }
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (state.brokersLoading) {
                    attr("disabled", "disabled")
                }
                onClick { onReloadBrokers() }
            }) {
                Text(if (state.brokersLoading) "Обновляю..." else "Обновить")
            }
        }

        when {
            state.brokersLoading && state.brokers == null -> {
                LoadingStateCard(
                    title = "Broker metadata",
                    text = "Загружаю cluster broker nodes и controller metadata.",
                )
            }

            brokersError != null && state.brokers == null -> {
                AlertBanner(brokersError, "warning")
            }

            else -> {
                brokersError?.let { AlertBanner(it, "warning") }
                val brokers = state.brokers
                if (brokers == null || brokers.brokers.isEmpty()) {
                    EmptyStateCard(
                        title = "Broker nodes не найдены",
                        text = "Kafka не вернула broker metadata для выбранного кластера.",
                    )
                } else {
                    Div({ classes("kafka-brokers-shell") }) {
                        Div({ classes("kafka-brokers-summary-grid") }) {
                            KafkaOverviewMetric(
                                title = "Cluster",
                                value = selectedCluster.name,
                            )
                            KafkaOverviewMetric(
                                title = "Bootstrap",
                                value = selectedCluster.bootstrapServers,
                            )
                            KafkaOverviewMetric(
                                title = "Brokers",
                                value = brokers.brokers.size.toString(),
                            )
                            KafkaOverviewMetric(
                                title = "Controller",
                                value = brokers.controllerBrokerId?.toString() ?: "unknown",
                            )
                        }

                        Div({ classes("kafka-broker-card-grid") }) {
                            brokers.brokers.forEach { broker ->
                                KafkaBrokerNodeCard(broker)
                            }
                        }

                        Div({ classes("table-responsive", "kafka-cluster-metadata-table-wrap") }) {
                            Table(attrs = { classes("table", "table-sm", "align-middle", "mb-0", "kafka-brokers-table") }) {
                                Thead {
                                    Tr {
                                        Th(attrs = { scope(org.jetbrains.compose.web.attributes.Scope.Col) }) { Text("Broker") }
                                        Th(attrs = { scope(org.jetbrains.compose.web.attributes.Scope.Col) }) { Text("Endpoint") }
                                        Th(attrs = { scope(org.jetbrains.compose.web.attributes.Scope.Col) }) { Text("Role") }
                                        Th(attrs = { scope(org.jetbrains.compose.web.attributes.Scope.Col) }) { Text("Rack") }
                                    }
                                }
                                Tbody {
                                    brokers.brokers.forEach { broker ->
                                        Tr {
                                            Td { Text(broker.brokerId.toString()) }
                                            Td {
                                                Span({ classes("kafka-broker-endpoint") }) {
                                                    Text("${broker.host}:${broker.port}")
                                                }
                                            }
                                            Td {
                                                KafkaBrokerRoleBadge(broker)
                                            }
                                            Td {
                                                Span({ classes("kafka-broker-rack") }) {
                                                    Text(broker.rack ?: "—")
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
private fun KafkaBrokerNodeCard(
    broker: KafkaBrokerSummaryResponse,
) {
    Div({ classes("kafka-broker-card") }) {
        Div({ classes("kafka-broker-card-top") }) {
            Div {
                Div({ classes("kafka-broker-card-title") }) {
                    Text("Broker ${broker.brokerId}")
                }
                Div({ classes("kafka-broker-card-endpoint") }) {
                    Text("${broker.host}:${broker.port}")
                }
            }
            KafkaBrokerRoleBadge(broker)
        }
        Div({ classes("kafka-broker-card-meta") }) {
            Span { Text("Rack") }
            Span { Text(broker.rack ?: "—") }
        }
    }
}

@Composable
private fun KafkaBrokerRoleBadge(
    broker: KafkaBrokerSummaryResponse,
) {
    Span({
        classes(
            "kafka-broker-role",
            if (broker.controller) "kafka-broker-role-controller" else "kafka-broker-role-follower",
        )
    }) {
        Text(if (broker.controller) "Controller" else "Follower")
    }
}
