package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.coroutines.launch
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeKafkaPage(
    route: KafkaRouteState,
    api: KafkaApi = remember { KafkaApiClient() },
) {
    val store = remember(api) { KafkaStore(api) }
    val scope = rememberCoroutineScope()
    var state by remember(
        route.clusterId,
        route.clusterSection,
        route.topicName,
        route.topicQuery,
        route.activePane,
        route.messageReadScope,
        route.messageReadMode,
        route.selectedMessagePartition,
    ) { mutableStateOf(KafkaPageState()) }

    fun replaceBrowserRoute(nextRoute: KafkaRouteState) {
        window.history.replaceState(
            null,
            "",
            buildKafkaPageHref(
                clusterId = nextRoute.clusterId,
                clusterSection = nextRoute.clusterSection,
                topicName = nextRoute.topicName,
                topicQuery = nextRoute.topicQuery,
                activePane = nextRoute.activePane,
                messageReadScope = nextRoute.messageReadScope,
                messageReadMode = nextRoute.messageReadMode,
                selectedMessagePartition = nextRoute.selectedMessagePartition,
            ),
        )
    }

    fun currentRouteState(): KafkaRouteState =
        KafkaRouteState(
            clusterId = state.selectedClusterId,
            clusterSection = state.clusterSection,
            topicName = state.selectedTopicName?.takeIf {
                state.clusterSection == "topics" && state.activePane != "cluster-settings"
            },
            topicQuery = state.topicQuery,
            activePane = state.activePane,
            messageReadScope = state.messageReadScope,
            messageReadMode = state.messageReadMode,
            selectedMessagePartition = state.selectedMessagePartition,
        )

    suspend fun ensureSettingsLoaded() {
        if (state.settings != null) {
            return
        }
        state = store.startSettingsReload(state)
        state = runCatching {
            store.loadSettings(state)
        }.getOrElse { error ->
            state.copy(
                settingsLoading = false,
                settingsError = error.message ?: "Не удалось загрузить настройки Kafka.",
            )
        }
    }

    LaunchedEffect(
        route.clusterId,
        route.clusterSection,
        route.topicName,
        route.topicQuery,
        route.activePane,
        route.messageReadScope,
        route.messageReadMode,
        route.selectedMessagePartition,
    ) {
        state = store.startLoading(state)
        state = runCatching {
            store.load(
                preferredClusterId = route.clusterId,
                preferredClusterSection = route.clusterSection,
                preferredTopicName = route.topicName,
                topicQuery = route.topicQuery,
                activePane = route.activePane,
                preferredMessageReadScope = route.messageReadScope,
                preferredMessageReadMode = route.messageReadMode,
                preferredMessagePartition = route.selectedMessagePartition,
            )
        }.getOrElse { error ->
            KafkaPageState(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить Kafka metadata.",
            )
        }
        if (state.activePane == "cluster-settings") {
            ensureSettingsLoaded()
        }
    }

    PageScaffold(
        eyebrow = "Источники и брокеры",
        title = "Kafka",
        subtitle = "Cluster-first tool для локальной работы с Kafka-каталогом, topic metadata и bounded operations.",
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                A(attrs = {
                    classes("btn", "btn-outline-secondary")
                    href("/")
                }) { Text("На главную") }
                Button(attrs = {
                    classes("btn", "btn-dark")
                    attr("type", "button")
                    disabled()
                }) { Text("Kafka explorer") }
            }
        },
        content = {
            KafkaPageContent(
                state = state,
                onClusterSectionChange = { section ->
                    scope.launch {
                        state = store.updateClusterSection(state, section)
                        when (state.clusterSection) {
                            "consumer-groups" -> {
                                state = store.startConsumerGroupsReload(state)
                                state = runCatching {
                                    store.loadConsumerGroups(state)
                                }.getOrElse { error ->
                                    state.copy(
                                        consumerGroupsLoading = false,
                                        consumerGroupsError = error.message ?: "Не удалось загрузить cluster-level consumer groups.",
                                    )
                                }
                            }

                            "brokers" -> {
                                state = store.startBrokersReload(state)
                                state = runCatching {
                                    store.loadBrokers(state)
                                }.getOrElse { error ->
                                    state.copy(
                                        brokersLoading = false,
                                        brokersError = error.message ?: "Не удалось загрузить broker metadata.",
                                    )
                                }
                            }
                        }
                        replaceBrowserRoute(currentRouteState())
                    }
                },
                onReloadConsumerGroups = {
                    scope.launch {
                        state = store.startConsumerGroupsReload(state)
                        state = runCatching {
                            store.loadConsumerGroups(state)
                        }.getOrElse { error ->
                            state.copy(
                                consumerGroupsLoading = false,
                                consumerGroupsError = error.message ?: "Не удалось загрузить cluster-level consumer groups.",
                            )
                        }
                    }
                },
                onReloadBrokers = {
                    scope.launch {
                        state = store.startBrokersReload(state)
                        state = runCatching {
                            store.loadBrokers(state)
                        }.getOrElse { error ->
                            state.copy(
                                brokersLoading = false,
                                brokersError = error.message ?: "Не удалось загрузить broker metadata.",
                            )
                        }
                    }
                },
                onTopicQueryChange = { query ->
                    state = store.updateTopicQuery(state, query)
                },
                onApplyTopicQuery = {
                    scope.launch {
                        state = store.startTopicsReload(state)
                        state = runCatching {
                            store.reloadTopics(state)
                        }.getOrElse { error ->
                            state.copy(
                                topicsLoading = false,
                                topicOverviewLoading = false,
                                errorMessage = error.message ?: "Не удалось обновить список Kafka-топиков.",
                            )
                        }
                        replaceBrowserRoute(currentRouteState())
                    }
                },
                onToggleCreateTopicForm = {
                    state = store.toggleCreateTopicForm(state)
                },
                onCreateTopicNameChange = { value ->
                    state = store.updateCreateTopicNameInput(state, value)
                },
                onCreateTopicPartitionsChange = { value ->
                    state = store.updateCreateTopicPartitionsInput(state, value)
                },
                onCreateTopicReplicationFactorChange = { value ->
                    state = store.updateCreateTopicReplicationFactorInput(state, value)
                },
                onCreateTopicCleanupPolicyChange = { value ->
                    state = store.updateCreateTopicCleanupPolicyInput(state, value)
                },
                onCreateTopicRetentionMsChange = { value ->
                    state = store.updateCreateTopicRetentionMsInput(state, value)
                },
                onCreateTopicRetentionBytesChange = { value ->
                    state = store.updateCreateTopicRetentionBytesInput(state, value)
                },
                onCreateTopic = {
                    scope.launch {
                        state = store.startCreateTopic(state)
                        state = runCatching {
                            store.createTopic(state)
                        }.getOrElse { error ->
                            state.copy(
                                createTopicLoading = false,
                                createTopicError = error.message ?: "Не удалось создать Kafka topic.",
                            )
                        }
                        replaceBrowserRoute(currentRouteState())
                    }
                },
                onReloadTopicOverview = { topicName ->
                    scope.launch {
                        state = store.startTopicOverviewReload(state)
                        state = runCatching {
                            store.loadTopicOverview(state, topicName)
                        }.getOrElse { error ->
                            state.copy(
                                topicOverviewLoading = false,
                                errorMessage = error.message ?: "Не удалось загрузить topic overview.",
                            )
                        }
                        replaceBrowserRoute(currentRouteState())
                    }
                },
                onPaneChange = { pane ->
                    scope.launch {
                        state = store.updateActivePane(state, pane)
                        if (state.activePane == "cluster-settings") {
                            ensureSettingsLoaded()
                        }
                        replaceBrowserRoute(currentRouteState())
                    }
                },
                onMessagePartitionChange = { partition ->
                    state = store.updateSelectedMessagePartition(state, partition)
                    replaceBrowserRoute(currentRouteState())
                },
                onMessageReadScopeChange = { scopeValue ->
                    state = store.updateMessageReadScope(state, scopeValue)
                    replaceBrowserRoute(currentRouteState())
                },
                onMessageReadModeChange = { mode ->
                    state = store.updateMessageReadMode(state, mode)
                    replaceBrowserRoute(currentRouteState())
                },
                onMessageLimitChange = { limit ->
                    state = store.updateMessageLimitInput(state, limit)
                },
                onMessageOffsetChange = { offset ->
                    state = store.updateMessageOffsetInput(state, offset)
                },
                onMessageTimestampChange = { timestamp ->
                    state = store.updateMessageTimestampInput(state, timestamp)
                },
                onReadMessages = {
                    scope.launch {
                        state = store.startMessagesReload(state)
                        state = runCatching {
                            store.readMessages(state)
                        }.getOrElse { error ->
                            state.copy(
                                messagesLoading = false,
                                messagesError = error.message ?: "Не удалось прочитать сообщения Kafka.",
                            )
                        }
                    }
                },
                onProducePartitionChange = { value ->
                    state = store.updateProducePartitionInput(state, value)
                },
                onProduceKeyChange = { value ->
                    state = store.updateProduceKeyInput(state, value)
                },
                onAddProduceHeader = {
                    state = store.addProduceHeader(state)
                },
                onRemoveProduceHeader = { index ->
                    state = store.removeProduceHeader(state, index)
                },
                onProduceHeaderNameChange = { index, value ->
                    state = store.updateProduceHeaderName(state, index, value)
                },
                onProduceHeaderValueChange = { index, value ->
                    state = store.updateProduceHeaderValue(state, index, value)
                },
                onProducePayloadChange = { value ->
                    state = store.updateProducePayloadInput(state, value)
                },
                onProduceMessage = {
                    scope.launch {
                        state = store.startProduce(state)
                        state = runCatching {
                            store.produceMessage(state)
                        }.getOrElse { error ->
                            state.copy(
                                produceLoading = false,
                                produceError = error.message ?: "Не удалось отправить сообщение Kafka.",
                            )
                        }
                    }
                },
                onReloadSettings = {
                    scope.launch {
                        state = store.startSettingsReload(state)
                        state = runCatching {
                            store.loadSettings(state)
                        }.getOrElse { error ->
                            state.copy(
                                settingsLoading = false,
                                settingsError = error.message ?: "Не удалось загрузить настройки Kafka.",
                            )
                        }
                    }
                },
                onAddSettingsCluster = {
                    state = store.addSettingsCluster(state)
                },
                onRemoveSettingsCluster = { clusterIndex ->
                    state = store.removeSettingsCluster(state, clusterIndex)
                },
                onSettingsClusterChange = { clusterIndex, cluster ->
                    state = store.updateSettingsCluster(state, clusterIndex) { cluster }
                },
                onPickSettingsFile = { clusterIndex, targetProperty ->
                    scope.launch {
                        state = store.startSettingsFilePick(state, clusterIndex, targetProperty)
                        state = runCatching {
                            store.pickSettingsFile(state, clusterIndex, targetProperty)
                        }.getOrElse { error ->
                            state.copy(
                                settingsFilePickClusterIndex = null,
                                settingsFilePickTargetProperty = null,
                                settingsError = error.message ?: "Не удалось выбрать локальный файл для Kafka cluster.",
                            )
                        }
                    }
                },
                onTestSettingsConnection = { clusterIndex ->
                    scope.launch {
                        state = store.startSettingsConnectionTest(state)
                        state = runCatching {
                            store.testSettingsConnection(state, clusterIndex)
                        }.getOrElse { error ->
                            state.copy(
                                settingsLoading = false,
                                settingsError = error.message ?: "Не удалось проверить подключение к Kafka cluster.",
                            )
                        }
                    }
                },
                onSaveSettings = {
                    scope.launch {
                        state = store.startSettingsSave(state)
                        state = runCatching {
                            store.saveSettings(state)
                        }.getOrElse { error ->
                            state.copy(
                                settingsLoading = false,
                                settingsError = error.message ?: "Не удалось сохранить настройки Kafka.",
                            )
                        }
                    }
                },
            )
        },
    )
}
