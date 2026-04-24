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
    onTopicQueryChange: (String) -> Unit,
    onApplyTopicQuery: () -> Unit,
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
    onReloadSettings: () -> Unit,
    onAddSettingsCluster: () -> Unit,
    onRemoveSettingsCluster: (Int) -> Unit,
    onSettingsClusterChange: (Int, KafkaEditableClusterResponse) -> Unit,
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
        EmptyStateCard(
            title = "Kafka clusters не настроены",
            text = "Добавь catalog кластеров в ui.kafka.clusters, чтобы открыть Kafka-инструмент.",
        )
        return
    }

    val selectedCluster = info.clusters.firstOrNull { it.id == state.selectedClusterId } ?: info.clusters.first()
    val selectedTopic = state.topicOverview

    Div({ classes("kafka-content-shell") }) {
        KafkaClusterCatalogSection(
            info = info,
            state = state,
            selectedClusterId = selectedCluster.id,
        )

        Div({ classes("row", "g-4") }) {
            Div({ classes("col-12", "col-xl-5") }) {
                KafkaTopicsCatalogSection(
                    state = state,
                    selectedCluster = selectedCluster,
                    topicsResponse = state.topics,
                    onTopicQueryChange = onTopicQueryChange,
                    onApplyTopicQuery = onApplyTopicQuery,
                )
            }

            Div({ classes("col-12", "col-xl-7") }) {
                when {
                    state.topicOverviewLoading && selectedTopic == null -> {
                        LoadingStateCard(
                            title = "Topic overview",
                            text = "Загружаю metadata выбранного топика.",
                        )
                    }

                    selectedTopic == null -> {
                        if (state.activePane == "settings") {
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
                                    onTestSettingsConnection = onTestSettingsConnection,
                                    onSaveSettings = onSaveSettings,
                                )
                            }
                        } else {
                            EmptyStateCard(
                                title = "Topic overview",
                                text = "Выбери топик из списка слева, чтобы увидеть partition summary и topic config.",
                            )
                        }
                    }

                    else -> {
                        KafkaTopicOverviewSection(
                            state = state,
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
                            onProduceHeadersChange = onProduceHeadersChange,
                            onProducePayloadChange = onProducePayloadChange,
                            onProduceMessage = onProduceMessage,
                            onReloadSettings = onReloadSettings,
                            onAddSettingsCluster = onAddSettingsCluster,
                            onRemoveSettingsCluster = onRemoveSettingsCluster,
                            onSettingsClusterChange = onSettingsClusterChange,
                            onTestSettingsConnection = onTestSettingsConnection,
                            onSaveSettings = onSaveSettings,
                        )
                    }
                }
            }
        }
    }
}
