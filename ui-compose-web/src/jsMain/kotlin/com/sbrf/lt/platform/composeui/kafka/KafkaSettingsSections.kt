package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

internal data class KafkaSettingsSelectOption(
    val value: String,
    val label: String,
)

@Composable
internal fun KafkaSettingsSection(
    state: KafkaPageState,
    onReloadSettings: () -> Unit,
    onAddSettingsCluster: () -> Unit,
    onRemoveSettingsCluster: (Int) -> Unit,
    onSettingsClusterChange: (Int, KafkaEditableClusterResponse) -> Unit,
    onPickSettingsFile: (Int, String) -> Unit,
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
                filePickTargetProperty = if (state.settingsFilePickClusterIndex == index) {
                    state.settingsFilePickTargetProperty
                } else {
                    null
                },
                onClusterChange = { updated -> onSettingsClusterChange(index, updated) },
                onPickFile = { property -> onPickSettingsFile(index, property) },
                onRemove = { onRemoveSettingsCluster(index) },
                onTestConnection = { onTestSettingsConnection(index) },
            )
        }
    }
}

@Composable
internal fun KafkaSettingsClusterCard(
    cluster: KafkaEditableClusterResponse,
    busy: Boolean,
    connectionResult: KafkaSettingsConnectionTestResponse?,
    filePickTargetProperty: String?,
    onClusterChange: (KafkaEditableClusterResponse) -> Unit,
    onPickFile: (String) -> Unit,
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
                options = listOf(
                    KafkaSettingsSelectOption("PLAINTEXT", "PLAINTEXT (без TLS)"),
                    KafkaSettingsSelectOption("SSL", "SSL / TLS"),
                ),
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
                    options = listOf(
                        KafkaSettingsSelectOption("", "Не задано"),
                        KafkaSettingsSelectOption("JKS", "JKS / PKCS12"),
                        KafkaSettingsSelectOption("PEM", "PEM certificate files"),
                    ),
                    disabled = busy,
                    helperText = "Не задано = type property не будет записано в config.",
                ) { onClusterChange(cluster.copy(truststoreType = it)) }
                KafkaSettingsPathField(
                    label = "ssl.truststore.location",
                    value = cluster.truststoreLocation,
                    placeholderText = "path to truststore",
                    disabled = busy,
                    helperText = "Truststore file: .jks / .p12 / .pfx",
                    browseLabel = "Выбрать truststore",
                    browseEnabled = cluster.truststoreType == "JKS",
                    browseBusy = filePickTargetProperty == "ssl.truststore.location",
                    onBrowse = { onPickFile("ssl.truststore.location") },
                ) {
                    onClusterChange(cluster.copy(truststoreLocation = it))
                }
                KafkaSettingsPathField(
                    "ssl.truststore.certificates",
                    value = cluster.truststoreCertificates,
                    placeholderText = "\${file:/path/to/ca.crt}",
                    disabled = busy,
                    helperText = "CA certificates: .crt / .cer / .pem",
                    browseLabel = "Выбрать CA сертификат",
                    browseEnabled = cluster.truststoreType == "PEM",
                    browseBusy = filePickTargetProperty == "ssl.truststore.certificates",
                    onBrowse = { onPickFile("ssl.truststore.certificates") },
                ) {
                    onClusterChange(cluster.copy(truststoreCertificates = it))
                }

                KafkaSettingsSelectField(
                    label = "ssl.keystore.type",
                    value = cluster.keystoreType,
                    options = listOf(
                        KafkaSettingsSelectOption("", "Не задано"),
                        KafkaSettingsSelectOption("JKS", "JKS / PKCS12"),
                        KafkaSettingsSelectOption("PEM", "PEM certificate files"),
                    ),
                    disabled = busy,
                    helperText = "Не задано = type property не будет записано в config.",
                ) { onClusterChange(cluster.copy(keystoreType = it)) }
                KafkaSettingsPathField(
                    label = "ssl.keystore.location",
                    value = cluster.keystoreLocation,
                    placeholderText = "path to keystore",
                    disabled = busy,
                    helperText = "Keystore file: .jks / .p12 / .pfx",
                    browseLabel = "Выбрать keystore",
                    browseEnabled = cluster.keystoreType == "JKS",
                    browseBusy = filePickTargetProperty == "ssl.keystore.location",
                    onBrowse = { onPickFile("ssl.keystore.location") },
                ) {
                    onClusterChange(cluster.copy(keystoreLocation = it))
                }
                KafkaSettingsPathField(
                    "ssl.keystore.certificate.chain",
                    value = cluster.keystoreCertificateChain,
                    placeholderText = "\${file:/path/to/client.crt}",
                    disabled = busy,
                    helperText = "Client certificate chain: .crt / .cer / .pem",
                    browseLabel = "Выбрать client certificate",
                    browseEnabled = cluster.keystoreType == "PEM",
                    browseBusy = filePickTargetProperty == "ssl.keystore.certificate.chain",
                    onBrowse = { onPickFile("ssl.keystore.certificate.chain") },
                ) {
                    onClusterChange(cluster.copy(keystoreCertificateChain = it))
                }
                KafkaSettingsPathField(
                    "ssl.keystore.key",
                    value = cluster.keystoreKey,
                    placeholderText = "\${file:/path/to/client.key}",
                    disabled = busy,
                    helperText = "Private key: .key / .pem",
                    browseLabel = "Выбрать private key",
                    browseEnabled = cluster.keystoreType == "PEM",
                    browseBusy = filePickTargetProperty == "ssl.keystore.key",
                    onBrowse = { onPickFile("ssl.keystore.key") },
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
internal fun KafkaSettingsTextField(
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
internal fun KafkaSettingsPathField(
    label: String,
    value: String,
    placeholderText: String,
    disabled: Boolean,
    helperText: String = "",
    browseLabel: String,
    browseEnabled: Boolean,
    browseBusy: Boolean,
    onBrowse: () -> Unit,
    onChange: (String) -> Unit,
) {
    Div({ classes("kafka-settings-field") }) {
        P({ classes("kafka-message-control-label") }) { Text(label) }
        Div({ classes("kafka-settings-path-field") }) {
            Input(type = InputType.Text, attrs = {
                classes("form-control")
                value(value)
                if (placeholderText.isNotBlank()) {
                    placeholder(placeholderText)
                }
                if (disabled) disabled()
                onInput { onChange(it.value) }
            })
            if (browseEnabled) {
                Button(attrs = {
                    classes("btn", "btn-outline-secondary", "btn-sm", "kafka-settings-path-button")
                    attr("type", "button")
                    if (disabled || browseBusy) disabled()
                    onClick { onBrowse() }
                }) {
                    Text(if (browseBusy) "Выбираю..." else browseLabel)
                }
            }
        }
        if (helperText.isNotBlank()) {
            P({ classes("text-secondary", "small", "mb-0") }) { Text(helperText) }
        }
    }
}

@Composable
internal fun KafkaSettingsSelectField(
    label: String,
    value: String,
    options: List<KafkaSettingsSelectOption>,
    disabled: Boolean,
    helperText: String = "",
    onChange: (String) -> Unit,
) {
    Div({ classes("kafka-settings-field") }) {
        P({ classes("kafka-message-control-label") }) { Text(label) }
        Select(attrs = {
            classes("form-select")
            if (disabled) disabled()
            onChange { onChange(it.value.orEmpty()) }
        }) {
            options.forEach { option ->
                Option(
                    value = option.value,
                    attrs = { if (option.value == value) selected() },
                ) {
                    Text(option.label)
                }
            }
        }
        if (helperText.isNotBlank()) {
            P({ classes("text-secondary", "small", "mb-0") }) { Text(helperText) }
        }
    }
}

@Composable
internal fun KafkaSettingsBooleanField(
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
