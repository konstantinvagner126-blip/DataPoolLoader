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
        return current.copy(
            settingsLoading = false,
            settings = saved,
            settingsStatusMessage = "Настройки Kafka сохранены.",
            settingsConnectionTestClusterIndex = null,
            settingsConnectionResult = null,
        )
    }

    private fun KafkaEditableClusterResponse.toRequestPayload(): KafkaEditableClusterRequestPayload =
        KafkaEditableClusterRequestPayload(
            id = id,
            name = name,
            readOnly = readOnly,
            bootstrapServers = bootstrapServers,
            clientId = clientId,
            securityProtocol = securityProtocol,
            truststoreType = truststoreType,
            truststoreLocation = truststoreLocation,
            truststoreCertificates = truststoreCertificates,
            keystoreType = keystoreType,
            keystoreLocation = keystoreLocation,
            keystoreCertificateChain = keystoreCertificateChain,
            keystoreKey = keystoreKey,
            keyPassword = keyPassword,
            additionalProperties = additionalProperties,
        )
}
