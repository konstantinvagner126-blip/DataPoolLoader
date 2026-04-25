package com.sbrf.lt.platform.composeui.kafka

internal class KafkaStoreSettingsSupport(
    private val api: KafkaApi,
) {
    fun startSettingsReload(current: KafkaPageState): KafkaPageState =
        current.copy(
            settingsLoading = true,
            settingsError = null,
            settingsStatusMessage = null,
        )

    suspend fun loadSettings(current: KafkaPageState): KafkaPageState {
        val settings = api.loadSettings()
        return current.copy(
            settingsLoading = false,
            settingsError = null,
            settings = settings,
            settingsStatusMessage = null,
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
            settingsFilePickClusterIndex = null,
            settingsFilePickTargetProperty = null,
        )
    }

    fun addSettingsCluster(current: KafkaPageState): KafkaPageState {
        val settings = current.settings ?: KafkaSettingsResponse()
        return current.copy(
            settings = settings.copy(
                clusters = settings.clusters + KafkaEditableClusterResponse(
                    id = "",
                    name = "",
                    readOnly = true,
                ),
            ),
            settingsError = null,
            settingsStatusMessage = null,
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
            settingsFilePickClusterIndex = null,
            settingsFilePickTargetProperty = null,
        )
    }

    fun removeSettingsCluster(
        current: KafkaPageState,
        clusterIndex: Int,
    ): KafkaPageState {
        val settings = current.settings ?: return current
        if (clusterIndex !in settings.clusters.indices) {
            return current
        }
        return current.copy(
            settings = settings.copy(
                clusters = settings.clusters.filterIndexed { index, _ -> index != clusterIndex },
            ),
            settingsError = null,
            settingsStatusMessage = null,
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
            settingsFilePickClusterIndex = null,
            settingsFilePickTargetProperty = null,
        )
    }

    fun updateSettingsCluster(
        current: KafkaPageState,
        clusterIndex: Int,
        transform: (KafkaEditableClusterResponse) -> KafkaEditableClusterResponse,
    ): KafkaPageState {
        val settings = current.settings ?: return current
        if (clusterIndex !in settings.clusters.indices) {
            return current
        }
        val updatedClusters = settings.clusters.mapIndexed { index, cluster ->
            if (index == clusterIndex) transform(cluster) else cluster
        }
        return current.copy(
            settings = settings.copy(clusters = updatedClusters),
            settingsError = null,
            settingsStatusMessage = null,
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
            settingsFilePickClusterIndex = null,
            settingsFilePickTargetProperty = null,
        )
    }

    fun startSettingsConnectionTest(current: KafkaPageState): KafkaPageState =
        current.copy(
            settingsLoading = true,
            settingsError = null,
            settingsStatusMessage = null,
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
        )

    suspend fun testSettingsConnection(
        current: KafkaPageState,
        clusterIndex: Int,
    ): KafkaPageState {
        val settings = current.settings ?: return current.copy(settingsLoading = false)
        val cluster = settings.clusters.getOrNull(clusterIndex) ?: return current.copy(settingsLoading = false)
        val result = api.testSettingsConnection(
            KafkaSettingsConnectionTestRequestPayload(
                cluster = cluster.toRequestPayload(),
            ),
        )
        return current.copy(
            settingsLoading = false,
            settingsConnectionTestClusterIndex = clusterIndex,
            settingsConnectionResult = result,
            settingsStatusMessage = null,
        )
    }

    fun startSettingsFilePick(
        current: KafkaPageState,
        clusterIndex: Int,
        targetProperty: String,
    ): KafkaPageState =
        current.copy(
            settingsError = null,
            settingsStatusMessage = null,
            settingsFilePickClusterIndex = clusterIndex,
            settingsFilePickTargetProperty = targetProperty,
        )

    suspend fun pickSettingsFile(
        current: KafkaPageState,
        clusterIndex: Int,
        targetProperty: String,
    ): KafkaPageState {
        val settings = current.settings ?: return current.copy(
            settingsFilePickClusterIndex = null,
            settingsFilePickTargetProperty = null,
        )
        val cluster = settings.clusters.getOrNull(clusterIndex) ?: return current.copy(
            settingsFilePickClusterIndex = null,
            settingsFilePickTargetProperty = null,
        )
        val result = api.pickSettingsFile(
            KafkaSettingsFilePickRequestPayload(
                targetProperty = targetProperty,
                currentValue = cluster.propertyValue(targetProperty),
            ),
        )
        if (result.cancelled || result.configValue.isNullOrBlank()) {
            return current.copy(
                settingsFilePickClusterIndex = null,
                settingsFilePickTargetProperty = null,
            )
        }
        return updateSettingsCluster(current, clusterIndex) {
            it.withUpdatedPropertyValue(targetProperty, result.configValue!!)
        }.copy(
            settingsFilePickClusterIndex = null,
            settingsFilePickTargetProperty = null,
        )
    }

    fun startSettingsSave(current: KafkaPageState): KafkaPageState =
        current.copy(
            settingsLoading = true,
            settingsError = null,
            settingsStatusMessage = null,
        )

    suspend fun saveSettings(current: KafkaPageState): KafkaPageState {
        val settings = current.settings ?: return current.copy(settingsLoading = false)
        val saved = api.saveSettings(
            KafkaSettingsUpdateRequestPayload(
                clusters = settings.clusters.map { it.toRequestPayload() },
            ),
        )
        val refreshedInfo = api.loadInfo()
        val refreshedSelectedClusterId = resolveSettingsSelectedClusterId(
            currentSelectedClusterId = current.selectedClusterId,
            refreshedInfo = refreshedInfo,
        )
        val clusterSelectionChanged = refreshedSelectedClusterId != current.selectedClusterId
        return current.copy(
            settingsLoading = false,
            info = refreshedInfo,
            selectedClusterId = refreshedSelectedClusterId,
            topics = if (clusterSelectionChanged) null else current.topics,
            consumerGroups = if (clusterSelectionChanged) null else current.consumerGroups,
            brokers = if (clusterSelectionChanged) null else current.brokers,
            selectedTopicName = if (clusterSelectionChanged) null else current.selectedTopicName,
            topicOverview = if (clusterSelectionChanged) null else current.topicOverview,
            messages = if (clusterSelectionChanged) null else current.messages,
            produceResult = if (clusterSelectionChanged) null else current.produceResult,
            settings = saved,
            settingsStatusMessage = "Настройки Kafka сохранены.",
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
            settingsFilePickClusterIndex = null,
            settingsFilePickTargetProperty = null,
        )
    }

    private fun resolveSettingsSelectedClusterId(
        currentSelectedClusterId: String?,
        refreshedInfo: KafkaToolInfoResponse,
    ): String? =
        refreshedInfo.clusters.firstOrNull { it.id == currentSelectedClusterId }?.id
            ?: refreshedInfo.clusters.firstOrNull()?.id

    private fun KafkaEditableClusterResponse.propertyValue(targetProperty: String): String =
        when (targetProperty) {
            "ssl.truststore.location" -> truststoreLocation
            "ssl.truststore.certificates" -> truststoreCertificates
            "ssl.keystore.location" -> keystoreLocation
            "ssl.keystore.certificate.chain" -> keystoreCertificateChain
            "ssl.keystore.key" -> keystoreKey
            else -> ""
        }

    private fun KafkaEditableClusterResponse.withUpdatedPropertyValue(
        targetProperty: String,
        value: String,
    ): KafkaEditableClusterResponse =
        when (targetProperty) {
            "ssl.truststore.location" -> copy(truststoreLocation = value)
            "ssl.truststore.certificates" -> copy(truststoreCertificates = value)
            "ssl.keystore.location" -> copy(keystoreLocation = value)
            "ssl.keystore.certificate.chain" -> copy(keystoreCertificateChain = value)
            "ssl.keystore.key" -> copy(keystoreKey = value)
            else -> this
        }

    private fun KafkaEditableClusterResponse.toRequestPayload(): KafkaEditableClusterRequestPayload =
        normalizedForSave().let { normalized ->
            KafkaEditableClusterRequestPayload(
                id = normalized.id,
                name = normalized.name,
                readOnly = normalized.readOnly,
                bootstrapServers = normalized.bootstrapServers,
                clientId = normalized.clientId,
                securityProtocol = normalized.securityProtocol,
                truststoreType = normalized.truststoreType,
                truststoreLocation = normalized.truststoreLocation,
                truststoreCertificates = normalized.truststoreCertificates,
                keystoreType = normalized.keystoreType,
                keystoreLocation = normalized.keystoreLocation,
                keystoreCertificateChain = normalized.keystoreCertificateChain,
                keystoreKey = normalized.keystoreKey,
                keyPassword = normalized.keyPassword,
                additionalProperties = normalized.additionalProperties,
            )
        }

    private fun KafkaEditableClusterResponse.normalizedForSave(): KafkaEditableClusterResponse {
        val normalizedSecurityProtocol = securityProtocol.trim().uppercase().ifBlank { "PLAINTEXT" }
        if (normalizedSecurityProtocol == "PLAINTEXT") {
            return copy(
                securityProtocol = "PLAINTEXT",
                truststoreType = "",
                truststoreLocation = "",
                truststoreCertificates = "",
                keystoreType = "",
                keystoreLocation = "",
                keystoreCertificateChain = "",
                keystoreKey = "",
                keyPassword = "",
            )
        }

        return copy(securityProtocol = "SSL")
            .normalizeTruststoreForSave()
            .normalizeKeystoreForSave()
    }

    private fun KafkaEditableClusterResponse.normalizeTruststoreForSave(): KafkaEditableClusterResponse =
        when (truststoreType.trim().uppercase()) {
            "JKS" -> copy(
                truststoreType = "JKS",
                truststoreCertificates = "",
            )

            "PEM" -> copy(
                truststoreType = "PEM",
                truststoreLocation = "",
            )

            else -> copy(
                truststoreType = "",
                truststoreLocation = "",
                truststoreCertificates = "",
            )
        }

    private fun KafkaEditableClusterResponse.normalizeKeystoreForSave(): KafkaEditableClusterResponse =
        when (keystoreType.trim().uppercase()) {
            "JKS" -> copy(
                keystoreType = "JKS",
                keystoreCertificateChain = "",
                keystoreKey = "",
                keyPassword = "",
            )

            "PEM" -> copy(
                keystoreType = "PEM",
                keystoreLocation = "",
            )

            else -> copy(
                keystoreType = "",
                keystoreLocation = "",
                keystoreCertificateChain = "",
                keystoreKey = "",
                keyPassword = "",
            )
        }
}
