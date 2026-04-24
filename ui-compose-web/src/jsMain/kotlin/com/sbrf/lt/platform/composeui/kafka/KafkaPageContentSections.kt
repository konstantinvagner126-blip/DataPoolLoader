package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.kafka.KafkaPageState
import com.sbrf.lt.platform.composeui.kafka.KafkaTopicConsumerGroupSummaryResponse
import com.sbrf.lt.platform.composeui.kafka.KafkaTopicConsumerGroupsSummaryResponse
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
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
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.jetbrains.compose.web.dom.Table

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
    val topicsResponse = state.topics

    Div({ classes("kafka-content-shell") }) {
        SectionCard(
            title = "Кластеры Kafka",
            subtitle = "Config-driven catalog. Кластер выбирается из ui.kafka.clusters без ad-hoc connection form.",
        ) {
            Div({ classes("kafka-cluster-strip") }) {
                info.clusters.forEach { cluster ->
                    A(attrs = {
                        classes(
                            "kafka-cluster-link",
                            if (cluster.id == selectedCluster.id) "kafka-cluster-link-active" else "kafka-cluster-link-idle",
                        )
                        href(
                            buildKafkaPageHref(
                                clusterId = cluster.id,
                                topicQuery = state.topicQuery,
                                activePane = state.activePane,
                                messageReadScope = state.messageReadScope,
                                messageReadMode = state.messageReadMode,
                                selectedMessagePartition = state.selectedMessagePartition,
                            ),
                        )
                    }) {
                        Div({ classes("kafka-cluster-link-top") }) {
                            Span({ classes("kafka-cluster-name") }) { Text(cluster.name) }
                            Span({ classes("kafka-cluster-protocol") }) { Text(cluster.securityProtocol) }
                        }
                        Div({ classes("kafka-cluster-bootstrap") }) {
                            Text(cluster.bootstrapServers)
                        }
                        if (cluster.readOnly) {
                            Div({ classes("kafka-cluster-mode") }) { Text("Read only") }
                        }
                    }
                }
            }
        }

        Div({ classes("row", "g-4") }) {
            Div({ classes("col-12", "col-xl-5") }) {
                SectionCard(
                    title = "Топики",
                    subtitle = "Кластер: ${selectedCluster.name}",
                ) {
                    Div({ classes("kafka-topic-filter-row") }) {
                        Input(type = InputType.Search, attrs = {
                            classes("form-control")
                            placeholder("topic name, prefix, internal...")
                            value(state.topicQuery)
                            onInput { onTopicQueryChange(it.value) }
                        })
                        Button(attrs = {
                            classes("btn", "btn-outline-secondary")
                            attr("type", "button")
                            onClick { onApplyTopicQuery() }
                        }) {
                            Text("Обновить")
                        }
                    }

                    when {
                        state.topicsLoading && topicsResponse == null -> {
                            P({ classes("text-secondary", "small", "mb-0") }) {
                                Text("Загружаю список топиков.")
                            }
                        }

                        topicsResponse == null || topicsResponse.topics.isEmpty() -> {
                            P({ classes("text-secondary", "small", "mb-0") }) {
                                Text("Для выбранного кластера топиков по текущему фильтру нет.")
                            }
                        }

                        else -> {
                            Div({ classes("table-responsive", "kafka-topic-table-wrap") }) {
                                Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-topic-table") }) {
                                    Thead {
                                        Tr {
                                            Th { Text("Topic") }
                                            Th { Text("Partitions") }
                                            Th { Text("RF") }
                                            Th { Text("Retention") }
                                        }
                                    }
                                    Tbody {
                                        topicsResponse.topics.forEach { topic ->
                                            Tr({
                                                classes(
                                                    if (topic.name == state.selectedTopicName) {
                                                        "kafka-topic-row-active"
                                                    } else {
                                                        "kafka-topic-row-idle"
                                                    },
                                                )
                                            }) {
                                                Td {
                                                    A(attrs = {
                                                        classes("kafka-topic-link")
                                                        href(
                                                            buildKafkaPageHref(
                                                                clusterId = selectedCluster.id,
                                                                topicName = topic.name,
                                                                topicQuery = state.topicQuery,
                                                                activePane = state.activePane,
                                                                messageReadScope = state.messageReadScope,
                                                                messageReadMode = state.messageReadMode,
                                                                selectedMessagePartition = state.selectedMessagePartition,
                                                            ),
                                                        )
                                                    }) {
                                                        Text(topic.name)
                                                    }
                                                    if (topic.internal) {
                                                        Div({ classes("kafka-topic-meta") }) { Text("internal") }
                                                    }
                                                }
                                                Td { Text(topic.partitionCount.toString()) }
                                                Td { Text(topic.replicationFactor.toString()) }
                                                Td { Text(formatRetention(topic.retentionMs, topic.retentionBytes)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
                        SectionCard(
                            title = selectedTopic.topic.name,
                            subtitle = "${selectedTopic.cluster.name} · ${selectedTopic.cluster.securityProtocol} · ${selectedTopic.cluster.bootstrapServers}",
                            actions = {
                                Button(attrs = {
                                    classes("btn", "btn-outline-secondary", "btn-sm")
                                    attr("type", "button")
                                    onClick { onReloadTopicOverview(selectedTopic.topic.name) }
                                }) {
                                    Text("Обновить overview")
                                }
                            },
                        ) {
                            Div({ classes("kafka-topic-summary-grid") }) {
                                KafkaOverviewMetric("Partitions", selectedTopic.topic.partitionCount.toString())
                                KafkaOverviewMetric("Replication", selectedTopic.topic.replicationFactor.toString())
                                KafkaOverviewMetric("Cleanup policy", selectedTopic.topic.cleanupPolicy ?: "default")
                                KafkaOverviewMetric("Retention", formatRetention(selectedTopic.topic.retentionMs, selectedTopic.topic.retentionBytes))
                            }

                            KafkaPaneTabs(
                                activePane = state.activePane,
                                onPaneChange = onPaneChange,
                            )

                            KafkaConsumerGroupsSection(selectedTopic.consumerGroups)

                            Div({ classes("table-responsive", "mt-3") }) {
                                Table({ classes("table", "table-sm", "align-middle", "mb-0", "kafka-partition-table") }) {
                                    Thead {
                                        Tr {
                                            Th { Text("Partition") }
                                            Th { Text("Leader") }
                                            Th { Text("Replicas") }
                                            Th { Text("ISR") }
                                            Th { Text("Earliest") }
                                            Th { Text("Latest") }
                                        }
                                    }
                                    Tbody {
                                        selectedTopic.partitions.forEach { partition ->
                                            Tr {
                                                Td { Text(partition.partition.toString()) }
                                                Td { Text(partition.leaderId?.toString() ?: "n/a") }
                                                Td { Text(partition.replicaCount.toString()) }
                                                Td { Text(partition.inSyncReplicaCount.toString()) }
                                                Td { Text(partition.earliestOffset?.toString() ?: "n/a") }
                                                Td { Text(partition.latestOffset?.toString() ?: "n/a") }
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.activePane == "messages") {
                                KafkaMessageBrowserSection(
                                    state = state,
                                    onMessagePartitionChange = onMessagePartitionChange,
                                    onMessageReadScopeChange = onMessageReadScopeChange,
                                    onMessageReadModeChange = onMessageReadModeChange,
                                    onMessageLimitChange = onMessageLimitChange,
                                    onMessageOffsetChange = onMessageOffsetChange,
                                    onMessageTimestampChange = onMessageTimestampChange,
                                    onReadMessages = onReadMessages,
                                )
                            }

                            if (state.activePane == "produce") {
                                KafkaProduceSection(
                                    state = state,
                                    onProducePartitionChange = onProducePartitionChange,
                                    onProduceKeyChange = onProduceKeyChange,
                                    onProduceHeadersChange = onProduceHeadersChange,
                                    onProducePayloadChange = onProducePayloadChange,
                                    onProduceMessage = onProduceMessage,
                                )
                            }

                            if (state.activePane == "settings") {
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KafkaSettingsSection(
    state: KafkaPageState,
    onReloadSettings: () -> Unit,
    onAddSettingsCluster: () -> Unit,
    onRemoveSettingsCluster: (Int) -> Unit,
    onSettingsClusterChange: (Int, KafkaEditableClusterResponse) -> Unit,
    onTestSettingsConnection: (Int) -> Unit,
    onSaveSettings: () -> Unit,
) {
    state.settingsError?.let { message ->
        AlertBanner(message, "warning")
    }
    state.settingsStatusMessage?.let { message ->
        AlertBanner(message, "success")
    }

    Div({ classes("kafka-settings-toolbar") }) {
        Button(attrs = {
            classes("btn", "btn-outline-secondary", "btn-sm")
            attr("type", "button")
            if (state.settingsLoading) disabled()
            onClick { onReloadSettings() }
        }) { Text("Перечитать из конфига") }

        Button(attrs = {
            classes("btn", "btn-outline-secondary", "btn-sm")
            attr("type", "button")
            if (state.settingsLoading) disabled()
            onClick { onAddSettingsCluster() }
        }) { Text("Добавить cluster") }

        Button(attrs = {
            classes("btn", "btn-dark", "btn-sm")
            attr("type", "button")
            if (state.settingsLoading) disabled()
            onClick { onSaveSettings() }
        }) { Text(if (state.settingsLoading) "Сохраняю..." else "Сохранить настройки") }
    }

    state.settings?.editableConfigPath?.let { path ->
        P({ classes("kafka-settings-path") }) {
            Text("Редактируемый конфиг: $path")
        }
    }

    val settings = state.settings
    if (state.settingsLoading && settings == null) {
        P({ classes("text-secondary", "small", "mb-0") }) {
            Text("Загружаю Kafka cluster catalog.")
        }
        return
    }

    if (settings == null || settings.clusters.isEmpty()) {
        EmptyStateCard(
            title = "Kafka clusters",
            text = "В config catalog пока нет Kafka cluster entries. Добавь первый cluster и сохрани настройки.",
        )
        return
    }

    Div({ classes("kafka-settings-list") }) {
        settings.clusters.forEachIndexed { index, cluster ->
            KafkaSettingsClusterCard(
                cluster = cluster,
                busy = state.settingsLoading,
                connectionResult = if (state.settingsConnectionTestClusterIndex == index) {
                    state.settingsConnectionResult
                } else {
                    null
                },
                onClusterChange = { updated -> onSettingsClusterChange(index, updated) },
                onRemove = { onRemoveSettingsCluster(index) },
                onTestConnection = { onTestSettingsConnection(index) },
            )
        }
    }
}

@Composable
private fun KafkaPaneTabs(
    activePane: String,
    onPaneChange: (String) -> Unit,
) {
    Div({ classes("kafka-pane-tabs") }) {
        KafkaPaneTabButton("overview", "Overview", activePane, onPaneChange)
        KafkaPaneTabButton("messages", "Messages", activePane, onPaneChange)
        KafkaPaneTabButton("produce", "Produce", activePane, onPaneChange)
        KafkaPaneTabButton("settings", "Settings", activePane, onPaneChange)
    }
}

@Composable
private fun KafkaPaneTabButton(
    pane: String,
    label: String,
    activePane: String,
    onPaneChange: (String) -> Unit,
) {
    Button(attrs = {
        classes(
            "btn",
            "btn-sm",
            if (pane == activePane) "btn-dark" else "btn-outline-secondary",
        )
        attr("type", "button")
        onClick { onPaneChange(pane) }
    }) {
        Text(label)
    }
}

@Composable
private fun KafkaSettingsClusterCard(
    cluster: KafkaEditableClusterResponse,
    busy: Boolean,
    connectionResult: KafkaSettingsConnectionTestResponse?,
    onClusterChange: (KafkaEditableClusterResponse) -> Unit,
    onRemove: () -> Unit,
    onTestConnection: () -> Unit,
) {
    Div({ classes("kafka-settings-cluster-card") }) {
        Div({ classes("kafka-settings-cluster-header") }) {
            Div {
                Div({ classes("kafka-section-caption") }) { Text("Kafka cluster") }
                Div({ classes("kafka-consumer-group-name") }) {
                    Text(cluster.name.ifBlank { cluster.id.ifBlank { "Новый cluster" } })
                }
            }
            Div({ classes("kafka-settings-cluster-actions") }) {
                Button(attrs = {
                    classes("btn", "btn-outline-secondary", "btn-sm")
                    attr("type", "button")
                    if (busy) disabled()
                    onClick { onTestConnection() }
                }) { Text("Тест подключения") }
                Button(attrs = {
                    classes("btn", "btn-outline-danger", "btn-sm")
                    attr("type", "button")
                    if (busy) disabled()
                    onClick { onRemove() }
                }) { Text("Удалить") }
            }
        }

        connectionResult?.let { result ->
            AlertBanner(
                text = result.message,
                level = if (result.success) "success" else "warning",
            )
        }

        Div({ classes("kafka-settings-grid") }) {
            KafkaSettingsTextField("ID", cluster.id, "cluster id", busy) {
                onClusterChange(cluster.copy(id = it))
            }
            KafkaSettingsTextField("Name", cluster.name, "display name", busy) {
                onClusterChange(cluster.copy(name = it))
            }
            KafkaSettingsTextField("bootstrap.servers", cluster.bootstrapServers, "host1:9092,host2:9092", busy) {
                onClusterChange(cluster.copy(bootstrapServers = it))
            }
            KafkaSettingsTextField("client.id", cluster.clientId, "optional", busy) {
                onClusterChange(cluster.copy(clientId = it))
            }

            KafkaSettingsSelectField(
                label = "security.protocol",
                value = cluster.securityProtocol,
                options = listOf("PLAINTEXT", "SSL"),
                disabled = busy,
            ) { nextProtocol ->
                onClusterChange(
                    if (nextProtocol == "PLAINTEXT") {
                        cluster.copy(
                            securityProtocol = nextProtocol,
                            truststoreType = "",
                            truststoreLocation = "",
                            truststoreCertificates = "",
                            keystoreType = "",
                            keystoreLocation = "",
                            keystoreCertificateChain = "",
                            keystoreKey = "",
                            keyPassword = "",
                        )
                    } else {
                        cluster.copy(securityProtocol = nextProtocol)
                    },
                )
            }

            KafkaSettingsBooleanField("readOnly", cluster.readOnly, busy) {
                onClusterChange(cluster.copy(readOnly = it))
            }
        }

        if (cluster.securityProtocol != "PLAINTEXT") {
            Div({ classes("kafka-settings-ssl-grid") }) {
                KafkaSettingsSelectField(
                    label = "ssl.truststore.type",
                    value = cluster.truststoreType,
                    options = listOf("", "JKS", "PEM"),
                    disabled = busy,
                ) { onClusterChange(cluster.copy(truststoreType = it)) }
                KafkaSettingsTextField("ssl.truststore.location", cluster.truststoreLocation, "path to truststore", busy) {
                    onClusterChange(cluster.copy(truststoreLocation = it))
                }
                KafkaSettingsTextField(
                    "ssl.truststore.certificates",
                    cluster.truststoreCertificates,
                    "\${file:/path/to/ca.crt}",
                    busy,
                ) {
                    onClusterChange(cluster.copy(truststoreCertificates = it))
                }

                KafkaSettingsSelectField(
                    label = "ssl.keystore.type",
                    value = cluster.keystoreType,
                    options = listOf("", "JKS", "PEM"),
                    disabled = busy,
                ) { onClusterChange(cluster.copy(keystoreType = it)) }
                KafkaSettingsTextField("ssl.keystore.location", cluster.keystoreLocation, "path to keystore", busy) {
                    onClusterChange(cluster.copy(keystoreLocation = it))
                }
                KafkaSettingsTextField(
                    "ssl.keystore.certificate.chain",
                    cluster.keystoreCertificateChain,
                    "\${file:/path/to/client.crt}",
                    busy,
                ) {
                    onClusterChange(cluster.copy(keystoreCertificateChain = it))
                }
                KafkaSettingsTextField(
                    "ssl.keystore.key",
                    cluster.keystoreKey,
                    "\${file:/path/to/client.key}",
                    busy,
                ) {
                    onClusterChange(cluster.copy(keystoreKey = it))
                }
                KafkaSettingsTextField("ssl.key.password", cluster.keyPassword, "\${KAFKA_KEY_PASSWORD}", busy) {
                    onClusterChange(cluster.copy(keyPassword = it))
                }
            }
        }

        if (cluster.additionalProperties.isNotEmpty()) {
            Div({ classes("kafka-settings-additional") }) {
                P({ classes("kafka-message-section-title") }) { Text("Additional properties") }
                cluster.additionalProperties.entries.sortedBy { it.key }.forEach { entry ->
                    Div({ classes("kafka-settings-additional-row") }) {
                        Span({ classes("kafka-settings-additional-key") }) { Text(entry.key) }
                        Span({ classes("kafka-settings-additional-value") }) { Text(entry.value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KafkaSettingsTextField(
    label: String,
    value: String,
    placeholderText: String,
    disabled: Boolean,
    onChange: (String) -> Unit,
) {
    Div({ classes("kafka-settings-field") }) {
        P({ classes("kafka-message-control-label") }) { Text(label) }
        Input(type = InputType.Text, attrs = {
            classes("form-control")
            value(value)
            if (placeholderText.isNotBlank()) {
                placeholder(placeholderText)
            }
            if (disabled) disabled()
            onInput { onChange(it.value) }
        })
    }
}

@Composable
private fun KafkaSettingsSelectField(
    label: String,
    value: String,
    options: List<String>,
    disabled: Boolean,
    onChange: (String) -> Unit,
) {
    Div({ classes("kafka-settings-field") }) {
        P({ classes("kafka-message-control-label") }) { Text(label) }
        Select(attrs = {
            classes("form-select")
            if (disabled) disabled()
            onChange { onChange(it.value.orEmpty()) }
        }) {
            options.forEach { optionValue ->
                Option(
                    value = optionValue,
                    attrs = { if (optionValue == value) selected() },
                ) {
                    Text(optionValue.ifBlank { "default" })
                }
            }
        }
    }
}

@Composable
private fun KafkaSettingsBooleanField(
    label: String,
    value: Boolean,
    disabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Div({ classes("kafka-settings-field", "kafka-settings-boolean-field") }) {
        P({ classes("kafka-message-control-label") }) { Text(label) }
        Div({ classes("form-check") }) {
            Input(type = InputType.Checkbox, attrs = {
                classes("form-check-input")
                if (value) {
                    attr("checked", "checked")
                }
                if (disabled) disabled()
                onClick { onChange(!value) }
            })
            Span({ classes("form-check-label") }) {
                Text(if (value) "Enabled" else "Disabled")
            }
        }
    }
}

@Composable
private fun KafkaMessageBrowserSection(
    state: KafkaPageState,
    onMessagePartitionChange: (Int) -> Unit,
    onMessageReadScopeChange: (String) -> Unit,
    onMessageReadModeChange: (String) -> Unit,
    onMessageLimitChange: (String) -> Unit,
    onMessageOffsetChange: (String) -> Unit,
    onMessageTimestampChange: (String) -> Unit,
    onReadMessages: () -> Unit,
) {
    val topicOverview = state.topicOverview ?: return
    val partitions = topicOverview.partitions
    if (partitions.isEmpty()) {
        return
    }
    SectionCard(
        title = "Сообщения",
        subtitle = "Bounded чтение через assign + seek без commit offsets и без background consumer session.",
    ) {
        state.messagesError?.let { message ->
            AlertBanner(message, "warning")
        }
        Div({ classes("kafka-message-controls") }) {
            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Scope") }
                Select(attrs = {
                    classes("form-select")
                    onChange { onMessageReadScopeChange(it.value.orEmpty()) }
                }) {
                    Option(value = "SELECTED_PARTITION", attrs = { if (state.messageReadScope == "SELECTED_PARTITION") selected() }) {
                        Text("Selected partition")
                    }
                    Option(value = "ALL_PARTITIONS", attrs = { if (state.messageReadScope == "ALL_PARTITIONS") selected() }) {
                        Text("All partitions")
                    }
                }
            }

            if (state.messageReadScope == "SELECTED_PARTITION") {
                Div({ classes("kafka-message-control") }) {
                    P({ classes("kafka-message-control-label") }) { Text("Partition") }
                    Select(attrs = {
                        classes("form-select")
                        onChange { event -> event.value.orEmpty().toIntOrNull()?.let(onMessagePartitionChange) }
                    }) {
                        partitions.forEach { partition ->
                            Option(
                                value = partition.partition.toString(),
                                attrs = { if (partition.partition == state.selectedMessagePartition) selected() },
                            ) {
                                Text(partition.partition.toString())
                            }
                        }
                    }
                }
            }

            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Mode") }
                Select(attrs = {
                    classes("form-select")
                    onChange { onMessageReadModeChange(it.value.orEmpty()) }
                }) {
                    Option(value = "LATEST", attrs = { if (state.messageReadMode == "LATEST") selected() }) { Text("Latest records") }
                    Option(value = "OFFSET", attrs = { if (state.messageReadMode == "OFFSET") selected() }) { Text("From explicit offset") }
                    Option(value = "TIMESTAMP", attrs = { if (state.messageReadMode == "TIMESTAMP") selected() }) { Text("From timestamp") }
                }
            }

            Div({ classes("kafka-message-control", "kafka-message-limit-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Limit") }
                Input(type = InputType.Number, attrs = {
                    classes("form-control")
                    value(state.messageLimitInput)
                    onInput { onMessageLimitChange(it.value?.toString().orEmpty()) }
                })
            }

            if (state.messageReadMode == "OFFSET") {
                Div({ classes("kafka-message-control", "kafka-message-cursor-control") }) {
                    P({ classes("kafka-message-control-label") }) { Text("Offset") }
                    Input(type = InputType.Number, attrs = {
                        classes("form-control")
                        value(state.messageOffsetInput)
                        onInput { onMessageOffsetChange(it.value?.toString().orEmpty()) }
                    })
                }
            }

            if (state.messageReadMode == "TIMESTAMP") {
                Div({ classes("kafka-message-control", "kafka-message-cursor-control") }) {
                    P({ classes("kafka-message-control-label") }) { Text("Timestamp ms") }
                    Input(type = InputType.Number, attrs = {
                        classes("form-control")
                        value(state.messageTimestampInput)
                        onInput { onMessageTimestampChange(it.value?.toString().orEmpty()) }
                    })
                }
            }

            Div({ classes("kafka-message-action") }) {
                Button(attrs = {
                    classes("btn", "btn-dark")
                    attr("type", "button")
                    if (state.messagesLoading) disabled()
                    onClick { onReadMessages() }
                }) {
                    Text(if (state.messagesLoading) "Читаю..." else "Читать сообщения")
                }
            }
        }

        state.messages?.takeIf {
            it.topicName == topicOverview.topic.name &&
                it.scope == state.messageReadScope &&
                (it.scope == "ALL_PARTITIONS" || it.partition == state.selectedMessagePartition) &&
                it.mode == state.messageReadMode
        }?.let { messages ->
            messages.note?.let { note ->
                P({ classes("kafka-message-note") }) { Text(note) }
            }

            if (messages.records.isEmpty()) {
                P({ classes("text-secondary", "small", "mb-0") }) {
                    Text("Для выбранного диапазона сообщений не найдено.")
                }
            } else {
                Div({ classes("kafka-message-list") }) {
                    messages.records.forEach { record ->
                        KafkaMessageRecordCard(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun KafkaMessageRecordCard(
    record: KafkaTopicMessageRecordResponse,
) {
    Div({ classes("kafka-message-card") }) {
        Div({ classes("kafka-message-card-meta") }) {
            Text(
                buildList {
                    add("partition ${record.partition}")
                    add("offset ${record.offset}")
                    add("timestamp ${record.timestamp?.toString() ?: "n/a"}")
                    add("headers ${record.headers.size}")
                }.joinToString(" · "),
            )
        }
        record.key?.let { payload ->
            KafkaRenderedBytesBlock(
                title = "Key",
                payload = payload,
            )
        }
        record.value?.let { payload ->
            KafkaRenderedBytesBlock(
                title = "Value",
                payload = payload,
            )
        }
        if (record.headers.isNotEmpty()) {
            Div({ classes("kafka-message-headers") }) {
                P({ classes("kafka-message-section-title") }) { Text("Headers") }
                record.headers.forEach { header ->
                    Div({ classes("kafka-message-header-row") }) {
                        Span({ classes("kafka-message-header-name") }) { Text(header.name) }
                        Span({ classes("kafka-message-header-value") }) {
                            Text(header.value?.jsonPrettyText ?: header.value?.text ?: "null")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KafkaProduceSection(
    state: KafkaPageState,
    onProducePartitionChange: (String) -> Unit,
    onProduceKeyChange: (String) -> Unit,
    onProduceHeadersChange: (String) -> Unit,
    onProducePayloadChange: (String) -> Unit,
    onProduceMessage: () -> Unit,
) {
    val topicOverview = state.topicOverview ?: return
    SectionCard(
        title = "Produce",
        subtitle = "Single-message produce с key, headers и optional partition override.",
    ) {
        if (topicOverview.cluster.readOnly) {
            AlertBanner(
                "Кластер помечен как readOnly. Produce path для него запрещен.",
                "warning",
            )
            return@SectionCard
        }

        state.produceError?.let { message ->
            AlertBanner(message, "warning")
        }

        Div({ classes("kafka-produce-grid") }) {
            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Partition override") }
                Input(type = InputType.Number, attrs = {
                    classes("form-control")
                    placeholder("optional")
                    value(state.producePartitionInput)
                    onInput { onProducePartitionChange(it.value?.toString().orEmpty()) }
                })
            }

            Div({ classes("kafka-message-control") }) {
                P({ classes("kafka-message-control-label") }) { Text("Key") }
                Input(type = InputType.Text, attrs = {
                    classes("form-control")
                    value(state.produceKeyInput)
                    onInput { onProduceKeyChange(it.value?.toString().orEmpty()) }
                })
            }
        }

        Div({ classes("kafka-produce-textarea") }) {
            P({ classes("kafka-message-control-label") }) { Text("Headers") }
            TextArea(attrs = {
                classes("form-control", "kafka-produce-headers")
                placeholder("name=value, one per line")
                value(state.produceHeadersInput)
                onInput { onProduceHeadersChange(it.value) }
            })
        }

        Div({ classes("kafka-produce-textarea") }) {
            P({ classes("kafka-message-control-label") }) { Text("Payload") }
            TextArea(attrs = {
                classes("form-control", "kafka-produce-payload")
                placeholder("message body")
                value(state.producePayloadInput)
                onInput { onProducePayloadChange(it.value) }
            })
        }

        Div({ classes("kafka-message-action") }) {
            Button(attrs = {
                classes("btn", "btn-dark")
                attr("type", "button")
                if (state.produceLoading) disabled()
                onClick { onProduceMessage() }
            }) {
                Text(if (state.produceLoading) "Отправляю..." else "Отправить сообщение")
            }
        }

        state.produceResult?.takeIf { it.topicName == topicOverview.topic.name }?.let { result ->
            Div({ classes("kafka-produce-result") }) {
                Text(
                    "Сообщение отправлено: partition ${result.partition} · offset ${result.offset} · timestamp ${result.timestamp ?: "n/a"}",
                )
            }
        }
    }
}

@Composable
private fun KafkaRenderedBytesBlock(
    title: String,
    payload: KafkaRenderedBytesResponse,
) {
    Div({ classes("kafka-message-payload") }) {
        P({ classes("kafka-message-section-title") }) {
            Text(
                buildList {
                    add(title)
                    add("${payload.sizeBytes} B")
                    if (payload.truncated) {
                        add("truncated")
                    }
                }.joinToString(" · "),
            )
        }
        Pre({ classes("kafka-message-payload-body") }) {
            Text(payload.jsonPrettyText ?: payload.text ?: "")
        }
    }
}

@Composable
private fun KafkaOverviewMetric(
    title: String,
    value: String,
) {
    Div({ classes("kafka-topic-summary-item") }) {
        Div({ classes("kafka-topic-summary-label") }) { Text(title) }
        Div({ classes("kafka-topic-summary-value") }) { Text(value) }
    }
}

@Composable
private fun KafkaConsumerGroupsSection(
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
private fun KafkaConsumerGroupCard(
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

private fun formatRetention(
    retentionMs: Long?,
    retentionBytes: Long?,
): String {
    val timePart = retentionMs?.let { "${it} ms" }
    val sizePart = retentionBytes?.let { "${it} B" }
    return listOfNotNull(timePart, sizePart).joinToString(" · ").ifBlank { "default" }
}
