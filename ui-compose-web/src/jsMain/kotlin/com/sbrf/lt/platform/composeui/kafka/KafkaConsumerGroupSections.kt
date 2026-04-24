package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

@Composable
internal fun KafkaConsumerGroupsSection(
    summary: KafkaTopicConsumerGroupsSummaryResponse,
) {
    Div({ classes("kafka-consumer-groups-section") }) {
        Div({ classes("kafka-section-caption") }) {
            Text("Consumer groups")
        }

        when {
            summary.status == "ERROR" -> {
                AlertBanner(
                    summary.message ?: "Consumer groups metadata недоступна.",
                    "warning",
                )
            }

            summary.status == "EMPTY" -> {
                P({ classes("text-secondary", "small", "mb-0") }) {
                    Text(summary.message ?: "Для этого топика нет consumer groups с committed offsets.")
                }
            }

            else -> {
                summary.message?.let { message ->
                    AlertBanner(message, "warning")
                }
                Div({ classes("kafka-consumer-group-list") }) {
                    summary.groups.forEach { group ->
                        KafkaConsumerGroupCard(group)
                    }
                }
            }
        }
    }
}

@Composable
internal fun KafkaConsumerGroupCard(
    group: KafkaTopicConsumerGroupSummaryResponse,
) {
    var expanded by remember(group.groupId) { mutableStateOf(false) }
    Div({ classes("kafka-consumer-group-card") }) {
        Div({ classes("kafka-consumer-group-card-top") }) {
            Div({ classes("kafka-consumer-group-identity") }) {
                Div({ classes("kafka-consumer-group-name") }) { Text(group.groupId) }
                Div({ classes("kafka-consumer-group-meta") }) {
                    Text(
                        buildList {
                            add(group.state ?: "state n/a")
                            add(
                                if (group.metadataAvailable) {
                                    "${group.memberCount ?: 0} members"
                                } else {
                                    "members n/a"
                                },
                            )
                            add("lag ${group.totalLag?.toString() ?: "n/a"}")
                            if (group.lagStatus != "OK") {
                                add(group.lagStatus.lowercase().replace('_', ' '))
                            }
                        }.joinToString(" · "),
                    )
                }
            }
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                onClick { expanded = !expanded }
            }) {
                Text(if (expanded) "Скрыть lag" else "Показать lag")
            }
        }
        group.note?.let { note ->
            P({ classes("kafka-consumer-group-note") }) { Text(note) }
        }
        if (expanded) {
            Div({ classes("table-responsive", "mt-2") }) {
                Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-consumer-group-table") }) {
                    Thead {
                        Tr {
                            Th { Text("Partition") }
                            Th { Text("Committed") }
                            Th { Text("Latest") }
                            Th { Text("Lag") }
                        }
                    }
                    Tbody {
                        group.partitions.forEach { partition ->
                            Tr {
                                Td { Text(partition.partition.toString()) }
                                Td { Text(partition.committedOffset?.toString() ?: "n/a") }
                                Td { Text(partition.latestOffset?.toString() ?: "n/a") }
                                Td { Text(partition.lag?.toString() ?: "n/a") }
                            }
                        }
                    }
                }
            }
        }
    }
}
