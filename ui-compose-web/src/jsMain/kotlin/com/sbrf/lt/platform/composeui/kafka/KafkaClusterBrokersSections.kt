package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.scope
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
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
    SectionCard(
        title = "Brokers",
        subtitle = "Read-only cluster metadata по broker nodes и controller роли.",
        actions = {
            Button(attrs = {
                classes("btn", "btn-outline-secondary")
                attr("type", "button")
                onClick { onReloadBrokers() }
            }) {
                Text("Обновить")
            }
        },
    ) {
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
                    return@SectionCard
                }

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

                    Div({ classes("table-responsive", "kafka-topic-table-wrap") }) {
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
                                    val roleClasses = if (broker.controller) {
                                        arrayOf("kafka-broker-role", "kafka-broker-role-controller")
                                    } else {
                                        arrayOf("kafka-broker-role")
                                    }
                                    Tr {
                                        Td { Text(broker.brokerId.toString()) }
                                        Td {
                                            Span({ classes("kafka-broker-endpoint") }) {
                                                Text("${broker.host}:${broker.port}")
                                            }
                                        }
                                        Td {
                                            Span({
                                                classes(*roleClasses)
                                            }) {
                                                Text(if (broker.controller) "Controller" else "Follower")
                                            }
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
