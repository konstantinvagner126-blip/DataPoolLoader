package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div

@Composable
internal fun KafkaPageContent(
    state: KafkaPageState,
    onClusterSectionChange: (String) -> Unit,
    onReloadConsumerGroups: () -> Unit,
    onReloadBrokers: () -> Unit,
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
    onReloadSettings: () -> Unit,
    onAddSettingsCluster: () -> Unit,
    onRemoveSettingsCluster: (Int) -> Unit,
    onSettingsClusterChange: (Int, KafkaEditableClusterResponse) -> Unit,
    onPickSettingsFile: (Int, String) -> Unit,
    onTestSettingsConnection: (Int) -> Unit,
    onSaveSettings: () -> Unit,
) {
    state.errorMessage?.let { AlertBanner(it, "warning") }

    if (state.loading && state.info == null) {
        LoadingStateCard(
            title = "Kafka metadata",
            text = "Загружаю каталог кластеров и список топиков.",
        )
        return
    }

    val info = state.info
    if (info == null || !info.configured || info.clusters.isEmpty()) {
        Div({ classes("kafka-page-stack") }) {
            KafkaToolHeader(info = info, selectedCluster = null)
            if (state.activePane == "cluster-settings") {
                SectionCard(
                    title = "Настройки Kafka",
                    subtitle = "Редактирование cluster catalog в управляемом ui-конфиге.",
                ) {
                    KafkaSettingsSection(
                        state = state,
                        onReloadSettings = onReloadSettings,
                        onAddSettingsCluster = onAddSettingsCluster,
                        onRemoveSettingsCluster = onRemoveSettingsCluster,
                        onSettingsClusterChange = onSettingsClusterChange,
                        onPickSettingsFile = onPickSettingsFile,
                        onTestSettingsConnection = onTestSettingsConnection,
                        onSaveSettings = onSaveSettings,
                    )
                }
            } else {
                EmptyStateCard(
                    title = "Kafka clusters не настроены",
                    text = "Добавь catalog кластеров в ui.kafka.clusters, чтобы открыть Kafka-инструмент.",
                )
                Div({ classes("mt-3") }) {
                    SectionCard(
                        title = "Первый cluster",
                        subtitle = "Открой настройки и добавь первый Kafka cluster без ручного page reload.",
                    ) {
                        org.jetbrains.compose.web.dom.Button(attrs = {
                            classes("btn", "btn-dark")
                            attr("type", "button")
                            onClick { onPaneChange("cluster-settings") }
                        }) {
                            org.jetbrains.compose.web.dom.Text("Открыть настройки Kafka")
                        }
                    }
                }
            }
        }
        return
    }

    val selectedCluster = info.clusters.firstOrNull { it.id == state.selectedClusterId } ?: info.clusters.first()
    val selectedTopic = state.topicOverview
    Div({ classes("kafka-page-stack") }) {
        KafkaToolHeader(info = info, selectedCluster = selectedCluster)

        Div({ classes("kafka-shell") }) {
            KafkaClusterSidebar(
                info = info,
                state = state,
                selectedCluster = selectedCluster,
                onClusterSectionChange = onClusterSectionChange,
                onOpenSettings = { onPaneChange("cluster-settings") },
            )

            Div({ classes("kafka-main") }) {
                when {
                    state.activePane == "cluster-settings" -> {
                        SectionCard(
                            title = "Настройки Kafka",
                            subtitle = "Редактирование cluster catalog в управляемом ui-конфиге.",
                        ) {
                            KafkaSettingsSection(
                                state = state,
                                onReloadSettings = onReloadSettings,
                                onAddSettingsCluster = onAddSettingsCluster,
                                onRemoveSettingsCluster = onRemoveSettingsCluster,
                                onSettingsClusterChange = onSettingsClusterChange,
                                onPickSettingsFile = onPickSettingsFile,
                                onTestSettingsConnection = onTestSettingsConnection,
                                onSaveSettings = onSaveSettings,
                            )
                        }
                    }

                    state.clusterSection == "consumer-groups" -> {
                        KafkaClusterConsumerGroupsSection(
                            state = state,
                            selectedCluster = selectedCluster,
                            onReloadConsumerGroups = onReloadConsumerGroups,
                        )
                    }

                    state.clusterSection == "brokers" -> {
                        KafkaClusterBrokersSection(
                            state = state,
                            selectedCluster = selectedCluster,
                            onReloadBrokers = onReloadBrokers,
                        )
                    }

                    else -> {
                        when {
                            state.topicOverviewLoading && selectedTopic == null -> {
                                LoadingStateCard(
                                    title = "Topic details",
                                    text = "Загружаю metadata выбранного топика.",
                                )
                            }

                            selectedTopic == null -> {
                                KafkaTopicsCatalogSection(
                                    state = state,
                                    selectedCluster = selectedCluster,
                                    topicsResponse = state.topics,
                                    onTopicQueryChange = onTopicQueryChange,
                                    onApplyTopicQuery = onApplyTopicQuery,
                                    onToggleCreateTopicForm = onToggleCreateTopicForm,
                                    onCreateTopicNameChange = onCreateTopicNameChange,
                                    onCreateTopicPartitionsChange = onCreateTopicPartitionsChange,
                                    onCreateTopicReplicationFactorChange = onCreateTopicReplicationFactorChange,
                                    onCreateTopicCleanupPolicyChange = onCreateTopicCleanupPolicyChange,
                                    onCreateTopicRetentionMsChange = onCreateTopicRetentionMsChange,
                                    onCreateTopicRetentionBytesChange = onCreateTopicRetentionBytesChange,
                                    onCreateTopic = onCreateTopic,
                                )
                            }

                            else -> {
                                KafkaTopicDetailsPageSection(
                                    state = state,
                                    selectedCluster = selectedCluster,
                                    selectedTopic = selectedTopic,
                                    onReloadTopicOverview = onReloadTopicOverview,
                                    onPaneChange = onPaneChange,
                                    onMessagePartitionChange = onMessagePartitionChange,
                                    onMessageReadScopeChange = onMessageReadScopeChange,
                                    onMessageReadModeChange = onMessageReadModeChange,
                                    onMessageLimitChange = onMessageLimitChange,
                                    onMessageOffsetChange = onMessageOffsetChange,
                                    onMessageTimestampChange = onMessageTimestampChange,
                                    onReadMessages = onReadMessages,
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
                        }
                    }
                }
            }
        }
    }
}
