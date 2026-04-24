package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfigValidationSupport
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.model.KafkaEditableClusterRequestPayload
import com.sbrf.lt.platform.ui.model.KafkaEditableClusterResponse
import com.sbrf.lt.platform.ui.model.KafkaSettingsConnectionTestRequestPayload
import com.sbrf.lt.platform.ui.model.KafkaSettingsConnectionTestResponse
import com.sbrf.lt.platform.ui.model.KafkaSettingsResponse
import com.sbrf.lt.platform.ui.model.KafkaSettingsUpdateRequestPayload
import java.nio.file.Files
import java.nio.file.Path

internal interface UiKafkaSettingsOperations {
    fun loadSettings(uiConfig: UiAppConfig): KafkaSettingsResponse

    fun saveSettings(
        request: KafkaSettingsUpdateRequestPayload,
        currentUiConfig: UiAppConfig,
    ): KafkaSettingsResponse

    fun testConnection(
        request: KafkaSettingsConnectionTestRequestPayload,
        currentUiConfig: UiAppConfig,
    ): KafkaSettingsConnectionTestResponse
}

internal open class UiKafkaSettingsService(
    private val uiConfigPersistenceService: UiConfigPersistenceService,
    private val runtimeConfigResolver: UiRuntimeConfigResolver,
    private val kafkaValidationSupport: UiKafkaConfigValidationSupport = UiKafkaConfigValidationSupport(),
    private val adminFacadeFactory: UiKafkaAdminFacadeFactory = DefaultUiKafkaAdminFacadeFactory(),
) : UiKafkaSettingsOperations {
    override fun loadSettings(uiConfig: UiAppConfig): KafkaSettingsResponse =
        KafkaSettingsResponse(
            editableConfigPath = uiConfigPersistenceService.resolveEditableConfigPath()?.toString(),
            clusters = uiConfig.kafka.clusters.map { it.toEditableResponse() },
        )

    override fun saveSettings(
        request: KafkaSettingsUpdateRequestPayload,
        currentUiConfig: UiAppConfig,
    ): KafkaSettingsResponse {
        val clusters = request.clusters.map { payload ->
            payload.toUiKafkaClusterConfig().also { cluster ->
                validateEditableCluster(cluster, currentUiConfig.configBaseDir?.let { Path.of(it) })
            }
        }
        val updated = uiConfigPersistenceService.updateKafkaClusterCatalog(clusters)
        return loadSettings(updated)
    }

    override fun testConnection(
        request: KafkaSettingsConnectionTestRequestPayload,
        currentUiConfig: UiAppConfig,
    ): KafkaSettingsConnectionTestResponse {
        val rawCluster = request.cluster.toUiKafkaClusterConfig()
        validateEditableCluster(rawCluster, currentUiConfig.configBaseDir?.let { Path.of(it) })
        val runtimeCluster = runtimeConfigResolver.resolve(
            currentUiConfig.copy(
                kafka = currentUiConfig.kafka.copy(clusters = listOf(rawCluster)),
            ),
        ).kafka.clusters.single()
        return runCatching {
            adminFacadeFactory.open(runtimeCluster).use { adminFacade ->
                val nodeCount = adminFacade.loadBrokerNodeCount()
                KafkaSettingsConnectionTestResponse(
                    success = true,
                    message = "Подключение успешно. Доступно broker nodes: $nodeCount.",
                    nodeCount = nodeCount,
                )
            }
        }.getOrElse { error ->
            KafkaSettingsConnectionTestResponse(
                success = false,
                message = describeKafkaSettingsConnectionFailure(error),
                nodeCount = null,
            )
        }
    }

    private fun validateEditableCluster(
        cluster: UiKafkaClusterConfig,
        configBaseDir: Path?,
    ) {
        kafkaValidationSupport.validateForLoad(UiKafkaConfig(clusters = listOf(cluster)))
        kafkaValidationSupport.validateForRuntime(UiKafkaConfig(clusters = listOf(cluster)))
        validateLocalPaths(cluster, configBaseDir)
    }

    private fun validateLocalPaths(
        cluster: UiKafkaClusterConfig,
        configBaseDir: Path?,
    ) {
        listOf(
            "ssl.truststore.location",
            "ssl.keystore.location",
            "ssl.truststore.certificates",
            "ssl.keystore.certificate.chain",
            "ssl.keystore.key",
        ).forEach { key ->
            val rawValue = cluster.properties[key]?.trim().orEmpty()
            if (rawValue.isBlank()) {
                return@forEach
            }
            resolveFileBackedPath(rawValue, configBaseDir)?.let { resolvedPath ->
                require(Files.exists(resolvedPath)) {
                    "Kafka cluster '${cluster.id}' с $key ссылается на несуществующий путь: $resolvedPath"
                }
            }
        }
    }

    private fun resolveFileBackedPath(
        rawValue: String,
        configBaseDir: Path?,
    ): Path? {
        val trimmed = rawValue.trim()
        val filePlaceholderPrefix = "\${file:"
        val rawPath = when {
            trimmed.startsWith(filePlaceholderPrefix) && trimmed.endsWith("}") ->
                trimmed.removePrefix(filePlaceholderPrefix).removeSuffix("}")
            trimmed.startsWith("\${") -> return null
            else -> trimmed
        }
        if (rawPath.isBlank()) {
            return null
        }
        val path = Path.of(rawPath)
        return if (path.isAbsolute) {
            path.normalize()
        } else {
            configBaseDir?.resolve(path)?.normalize() ?: path.normalize()
        }
    }
}

private fun describeKafkaSettingsConnectionFailure(error: Throwable): String {
    val detail = error.message?.trim()?.takeIf { it.isNotEmpty() }
    return when (error) {
        is org.apache.kafka.common.errors.AuthorizationException ->
            "Подключение отклонено по правам Kafka.${detail?.let { " $it" }.orEmpty()}"
        is org.apache.kafka.common.errors.TimeoutException ->
            "Kafka cluster не ответил вовремя.${detail?.let { " $it" }.orEmpty()}"
        else ->
            "Не удалось подключиться к Kafka cluster.${detail?.let { " $it" }.orEmpty()}"
    }
}

private fun UiKafkaClusterConfig.toEditableResponse(): KafkaEditableClusterResponse {
    val known = properties.filterKeys { it in knownKafkaSettingsKeys() }
    val additional = properties.filterKeys { it !in knownKafkaSettingsKeys() }.toSortedMap()
    return KafkaEditableClusterResponse(
        id = id,
        name = name,
        readOnly = readOnly,
        bootstrapServers = known["bootstrap.servers"].orEmpty(),
        clientId = known["client.id"].orEmpty(),
        securityProtocol = known["security.protocol"].orEmpty().ifBlank { "PLAINTEXT" },
        truststoreType = known["ssl.truststore.type"].orEmpty(),
        truststoreLocation = known["ssl.truststore.location"].orEmpty(),
        truststoreCertificates = known["ssl.truststore.certificates"].orEmpty(),
        keystoreType = known["ssl.keystore.type"].orEmpty(),
        keystoreLocation = known["ssl.keystore.location"].orEmpty(),
        keystoreCertificateChain = known["ssl.keystore.certificate.chain"].orEmpty(),
        keystoreKey = known["ssl.keystore.key"].orEmpty(),
        keyPassword = known["ssl.key.password"].orEmpty(),
        additionalProperties = additional,
    )
}

private fun KafkaEditableClusterRequestPayload.toUiKafkaClusterConfig(): UiKafkaClusterConfig =
    UiKafkaClusterConfig(
        id = id.trim(),
        name = name.trim(),
        readOnly = readOnly,
        properties = buildMap {
            putIfNotBlank("bootstrap.servers", bootstrapServers)
            putIfNotBlank("client.id", clientId)
            putIfNotBlank("security.protocol", securityProtocol)
            putIfNotBlank("ssl.truststore.type", truststoreType)
            putIfNotBlank("ssl.truststore.location", truststoreLocation)
            putIfNotBlank("ssl.truststore.certificates", truststoreCertificates)
            putIfNotBlank("ssl.keystore.type", keystoreType)
            putIfNotBlank("ssl.keystore.location", keystoreLocation)
            putIfNotBlank("ssl.keystore.certificate.chain", keystoreCertificateChain)
            putIfNotBlank("ssl.keystore.key", keystoreKey)
            putIfNotBlank("ssl.key.password", keyPassword)
            additionalProperties.forEach { (key, value) ->
                putIfNotBlank(key.trim(), value)
            }
        }.toSortedMap(),
    )

private fun MutableMap<String, String>.putIfNotBlank(
    key: String,
    value: String,
) {
    if (key.isNotBlank() && value.isNotBlank()) {
        put(key, value.trim())
    }
}

private fun knownKafkaSettingsKeys(): Set<String> = setOf(
    "bootstrap.servers",
    "client.id",
    "security.protocol",
    "ssl.truststore.type",
    "ssl.truststore.location",
    "ssl.truststore.certificates",
    "ssl.keystore.type",
    "ssl.keystore.location",
    "ssl.keystore.certificate.chain",
    "ssl.keystore.key",
    "ssl.key.password",
)
