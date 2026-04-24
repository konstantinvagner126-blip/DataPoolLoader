package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

@Composable
internal fun KafkaTopicsCatalogSection(
    state: KafkaPageState,
    selectedCluster: KafkaClusterCatalogEntryResponse,
    topicsResponse: KafkaTopicsCatalogResponse?,
    onTopicQueryChange: (String) -> Unit,
    onApplyTopicQuery: () -> Unit,
    onToggleCreateTopicForm: () -> Unit,
    onCreateTopicNameChange: (String) -> Unit,
    onCreateTopicPartitionsChange: (String) -> Unit,
    onCreateTopicReplicationFactorChange: (String) -> Unit,
    onCreateTopicCleanupPolicyChange: (String) -> Unit,
    onCreateTopicRetentionMsChange: (String) -> Unit,
    onCreateTopicRetentionBytesChange: (String) -> Unit,
    onCreateTopic: () -> Unit,
) {
    val filteredTopics = topicsResponse?.topics.orEmpty()
    val internalTopicCount = filteredTopics.count { it.internal }
    val userTopicCount = filteredTopics.size - internalTopicCount
    val topicCountLabel = when {
        state.topicsLoading && topicsResponse == null -> "Загрузка"
        filteredTopics.isEmpty() -> "0 topics"
        else -> "${filteredTopics.size} topics"
    }
    Div({ classes("kafka-topics-panel") }) {
        Div({ classes("kafka-topics-head") }) {
            Div({ classes("kafka-topics-title-block") }) {
                P({ classes("kafka-section-caption", "mb-0") }) { Text("Topic catalog") }
                Div({ classes("kafka-topics-title-row") }) {
                    Div({ classes("kafka-topics-title") }) { Text("Topics") }
                    Span({ classes("kafka-tool-chip", "accent") }) { Text(topicCountLabel) }
                    Span({ classes("kafka-tool-chip") }) { Text("${userTopicCount} user") }
                    if (internalTopicCount > 0) {
                        Span({ classes("kafka-tool-chip", "warn") }) { Text("${internalTopicCount} internal") }
                    }
                }
                P({ classes("kafka-topics-subtitle") }) {
                    Text("${selectedCluster.name} · ${selectedCluster.bootstrapServers}")
                }
            }

            Div({ classes("kafka-topic-header-actions") }) {
                Button(attrs = {
                    classes("btn", "btn-outline-secondary", "btn-sm")
                    attr("type", "button")
                    onClick { onApplyTopicQuery() }
                }) {
                    Text(if (state.topicsLoading) "Обновляю..." else "Обновить")
                }
                Button(attrs = {
                    classes("btn", "btn-dark", "btn-sm")
                    attr("type", "button")
                    if (selectedCluster.readOnly) disabled()
                    onClick { onToggleCreateTopicForm() }
                }) {
                    Text(if (state.createTopicFormVisible) "Скрыть форму" else "Создать топик")
                }
            }
        }

        state.createTopicResult?.takeIf { it.cluster.id == selectedCluster.id }?.let { result ->
            AlertBanner(
                "Топик ${result.topicName} создан: partitions ${result.partitionCount}, replication ${result.replicationFactor}.",
                "success",
            )
        }
        state.createTopicError?.let { message ->
            AlertBanner(message, "warning")
        }

        Div({ classes("kafka-topic-filter-bar") }) {
            Div({ classes("kafka-topic-filter-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Topic filter") }
                Input(type = InputType.Search, attrs = {
                    classes("form-control", "kafka-topic-filter-input")
                    placeholder("orders, audit, __consumer_offsets")
                    value(state.topicQuery)
                    onInput { onTopicQueryChange(it.value) }
                })
            }

            Div({ classes("kafka-topic-filter-chips") }) {
                Span({ classes("kafka-tool-chip") }) {
                    Text(if (state.topicQuery.isBlank()) "all topics" else "query: ${state.topicQuery}")
                }
                Span({ classes("kafka-tool-chip") }) {
                    Text("internal shown")
                }
                if (state.topicsLoading && topicsResponse != null) {
                    Span({ classes("kafka-tool-chip", "warn") }) { Text("refreshing") }
                }
            }
        }

        if (state.createTopicFormVisible) {
            KafkaCreateTopicForm(
                state = state,
                selectedCluster = selectedCluster,
                onCreateTopicNameChange = onCreateTopicNameChange,
                onCreateTopicPartitionsChange = onCreateTopicPartitionsChange,
                onCreateTopicReplicationFactorChange = onCreateTopicReplicationFactorChange,
                onCreateTopicCleanupPolicyChange = onCreateTopicCleanupPolicyChange,
                onCreateTopicRetentionMsChange = onCreateTopicRetentionMsChange,
                onCreateTopicRetentionBytesChange = onCreateTopicRetentionBytesChange,
                onCreateTopic = onCreateTopic,
            )
        }

        when {
            state.topicsLoading && topicsResponse == null -> {
                P({ classes("text-secondary", "small", "mb-0", "kafka-topic-empty-note") }) {
                    Text("Загружаю список топиков.")
                }
            }

            filteredTopics.isEmpty() -> {
                P({ classes("text-secondary", "small", "mb-0", "kafka-topic-empty-note") }) {
                    Text(
                        if (state.topicQuery.isBlank()) {
                            "Для выбранного кластера топиков нет."
                        } else {
                            "Для выбранного кластера нет топиков по фильтру '${state.topicQuery}'."
                        },
                    )
                }
            }

            else -> {
                Div({ classes("table-responsive", "kafka-topic-table-wrap") }) {
                    Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-topic-table") }) {
                        Thead {
                            Tr {
                                Th { Text("Topic") }
                                Th { Text("Partitions") }
                                Th { Text("Replication") }
                                Th { Text("Cleanup") }
                                Th { Text("Retention") }
                            }
                        }
                        Tbody {
                            filteredTopics.forEach { topic ->
                                val topicHref = buildKafkaPageHref(
                                    clusterId = selectedCluster.id,
                                    clusterSection = "topics",
                                    topicName = topic.name,
                                    topicQuery = state.topicQuery,
                                    activePane = "overview",
                                    messageReadScope = state.messageReadScope,
                                    messageReadMode = state.messageReadMode,
                                    selectedMessagePartition = state.selectedMessagePartition,
                                )
                                Tr({
                                    classes(
                                        "kafka-topic-row",
                                        if (topic.name == state.selectedTopicName) {
                                            "kafka-topic-row-active"
                                        } else {
                                            "kafka-topic-row-idle"
                                        },
                                    )
                                    attr("role", "link")
                                    onClick { window.location.href = topicHref }
                                }) {
                                    Td {
                                        A(attrs = {
                                            classes("kafka-topic-link")
                                            href(topicHref)
                                        }) {
                                            Span({ classes("kafka-topic-name") }) {
                                                Text(topic.name)
                                            }
                                        }
                                        Div({ classes("kafka-topic-flags") }) {
                                            if (topic.internal) {
                                                Span({ classes("kafka-topic-flag") }) { Text("internal") }
                                            }
                                            if (topic.name == state.selectedTopicName) {
                                                Span({ classes("kafka-topic-flag", "kafka-topic-flag-active") }) {
                                                    Text("selected")
                                                }
                                            }
                                        }
                                    }
                                    Td({ classes("kafka-topic-value-cell") }) {
                                        Text(topic.partitionCount.toString())
                                    }
                                    Td({ classes("kafka-topic-value-cell") }) {
                                        Text(topic.replicationFactor.toString())
                                    }
                                    Td({ classes("kafka-topic-value-cell") }) {
                                        Text(topic.cleanupPolicy ?: "default")
                                    }
                                    Td({ classes("kafka-topic-value-cell") }) {
                                        Text(formatKafkaRetention(topic.retentionMs, topic.retentionBytes))
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
private fun KafkaCreateTopicForm(
    state: KafkaPageState,
    selectedCluster: KafkaClusterCatalogEntryResponse,
    onCreateTopicNameChange: (String) -> Unit,
    onCreateTopicPartitionsChange: (String) -> Unit,
    onCreateTopicReplicationFactorChange: (String) -> Unit,
    onCreateTopicCleanupPolicyChange: (String) -> Unit,
    onCreateTopicRetentionMsChange: (String) -> Unit,
    onCreateTopicRetentionBytesChange: (String) -> Unit,
    onCreateTopic: () -> Unit,
) {
    Div({ classes("kafka-topic-create-shell") }) {
        Div({ classes("kafka-topic-create-header") }) {
            Div {
                P({ classes("kafka-message-section-title") }) { Text("Create topic") }
                P({ classes("kafka-placeholder-note", "mb-0") }) {
                    Text("Новый топик создается сразу в выбранном кластере ${selectedCluster.name}.")
                }
            }
            if (selectedCluster.readOnly) {
                Span({ classes("kafka-topic-flag") }) { Text("read only") }
            }
        }

        Div({ classes("kafka-topic-create-grid") }) {
            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Topic name") }
                Input(type = InputType.Text, attrs = {
                    classes("form-control")
                    placeholder("orders.events")
                    value(state.createTopicNameInput)
                    onInput { onCreateTopicNameChange(it.value?.toString().orEmpty()) }
                })
            }
            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Partitions") }
                Input(type = InputType.Number, attrs = {
                    classes("form-control")
                    value(state.createTopicPartitionsInput)
                    onInput { onCreateTopicPartitionsChange(it.value?.toString().orEmpty()) }
                })
            }
            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Replication factor") }
                Input(type = InputType.Number, attrs = {
                    classes("form-control")
                    value(state.createTopicReplicationFactorInput)
                    onInput { onCreateTopicReplicationFactorChange(it.value?.toString().orEmpty()) }
                })
            }
            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Cleanup policy") }
                Select(attrs = {
                    classes("form-select")
                    onChange { onCreateTopicCleanupPolicyChange(it.value.orEmpty()) }
                }) {
                    Option(value = "", attrs = { if (state.createTopicCleanupPolicyInput.isBlank()) selected() }) {
                        Text("default")
                    }
                    Option(value = "delete", attrs = { if (state.createTopicCleanupPolicyInput == "delete") selected() }) {
                        Text("delete")
                    }
                    Option(value = "compact", attrs = { if (state.createTopicCleanupPolicyInput == "compact") selected() }) {
                        Text("compact")
                    }
                    Option(value = "compact,delete", attrs = { if (state.createTopicCleanupPolicyInput == "compact,delete") selected() }) {
                        Text("compact,delete")
                    }
                }
            }
            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Retention ms") }
                Input(type = InputType.Number, attrs = {
                    classes("form-control")
                    placeholder("optional")
                    value(state.createTopicRetentionMsInput)
                    onInput { onCreateTopicRetentionMsChange(it.value?.toString().orEmpty()) }
                })
            }
            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Retention bytes") }
                Input(type = InputType.Number, attrs = {
                    classes("form-control")
                    placeholder("optional")
                    value(state.createTopicRetentionBytesInput)
                    onInput { onCreateTopicRetentionBytesChange(it.value?.toString().orEmpty()) }
                })
            }
        }

        Div({ classes("kafka-topic-create-actions") }) {
            Button(attrs = {
                classes("btn", "btn-dark")
                attr("type", "button")
                if (state.createTopicLoading || selectedCluster.readOnly) disabled()
                onClick { onCreateTopic() }
            }) {
                Text(if (state.createTopicLoading) "Создаю..." else "Создать топик")
            }
        }
    }
}

@Composable
internal fun KafkaOverviewMetric(
    title: String,
    value: String,
    note: String? = null,
) {
    Div({ classes("kafka-topic-summary-item") }) {
        Div({ classes("kafka-topic-summary-label") }) { Text(title) }
        Div({ classes("kafka-topic-summary-value") }) { Text(value) }
        note?.let {
            Div({ classes("kafka-topic-summary-note") }) { Text(it) }
        }
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
