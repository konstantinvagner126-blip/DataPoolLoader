package com.sbrf.lt.platform.composeui.kafka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KafkaStoreSettingsSupportTest {
    private val support = KafkaStoreSettingsSupport(api = StubKafkaApi())

    @Test
    fun `addSettingsCluster creates editable draft and clears stale status state`() {
        val current = KafkaPageState(
            settings = KafkaSettingsResponse(),
            settingsError = "stale error",
            settingsStatusMessage = "saved",
            settingsConnectionTestClusterIndex = 0,
            settingsConnectionResult = KafkaSettingsConnectionTestResponse(
                success = true,
                message = "ok",
                nodeCount = 1,
            ),
        )

        val updated = support.addSettingsCluster(current)

        assertEquals(1, updated.settings?.clusters?.size)
        assertEquals("", updated.settings?.clusters?.single()?.id)
        assertEquals(true, updated.settings?.clusters?.single()?.readOnly)
        assertNull(updated.settingsError)
        assertNull(updated.settingsStatusMessage)
        assertNull(updated.settingsConnectionTestClusterIndex)
        assertNull(updated.settingsConnectionResult)
    }

    @Test
    fun `pickSettingsFile applies config ready value to target cluster field`() {
        val support = KafkaStoreSettingsSupport(
            api = StubKafkaApi(
                pickSettingsFileHandler = {
                    KafkaSettingsFilePickResponse(
                        targetProperty = "ssl.keystore.key",
                        cancelled = false,
                        selectedPath = "/tmp/client.key",
                        configValue = "\${file:/tmp/client.key}",
                    )
                },
            ),
        )
        val current = KafkaPageState(
            settings = KafkaSettingsResponse(
                clusters = listOf(
                    KafkaEditableClusterResponse(
                        id = "local",
                        name = "Local Kafka",
                        readOnly = false,
                        securityProtocol = "SSL",
                        keystoreType = "PEM",
                    ),
                ),
            ),
        )

        val prepared = support.startSettingsFilePick(current, 0, "ssl.keystore.key")
        val updated = runKafkaSuspend {
            support.pickSettingsFile(prepared, 0, "ssl.keystore.key")
        }

        assertEquals("\${file:/tmp/client.key}", updated.settings?.clusters?.single()?.keystoreKey)
        assertNull(updated.settingsFilePickClusterIndex)
        assertNull(updated.settingsFilePickTargetProperty)
    }

    @Test
    fun `saveSettings refreshes shell info and selects first configured cluster when adding first cluster`() {
        val support = KafkaStoreSettingsSupport(
            api = StubKafkaApi(
                saveSettingsHandler = { request ->
                    KafkaSettingsResponse(
                        editableConfigPath = "/tmp/ui-application.yml",
                        clusters = request.clusters.map {
                            KafkaEditableClusterResponse(
                                id = it.id,
                                name = it.name,
                                readOnly = it.readOnly,
                                bootstrapServers = it.bootstrapServers,
                                clientId = it.clientId,
                                securityProtocol = it.securityProtocol,
                                truststoreType = it.truststoreType,
                                truststoreLocation = it.truststoreLocation,
                                truststoreCertificates = it.truststoreCertificates,
                                keystoreType = it.keystoreType,
                                keystoreLocation = it.keystoreLocation,
                                keystoreCertificateChain = it.keystoreCertificateChain,
                                keystoreKey = it.keystoreKey,
                                keyPassword = it.keyPassword,
                                additionalProperties = it.additionalProperties,
                            )
                        },
                    )
                },
                infoHandler = {
                    KafkaToolInfoResponse(
                        configured = true,
                        maxRecordsPerRead = 100,
                        maxPayloadBytes = 1_048_576,
                        clusters = listOf(
                            KafkaClusterCatalogEntryResponse(
                                id = "stage",
                                name = "Stage Kafka",
                                readOnly = true,
                                bootstrapServers = "stage-1:9092,stage-2:9092",
                                securityProtocol = "SSL",
                            ),
                        ),
                    )
                },
            ),
        )
        val current = KafkaPageState(
            info = KafkaToolInfoResponse(
                configured = false,
                maxRecordsPerRead = 100,
                maxPayloadBytes = 1_048_576,
                clusters = emptyList(),
            ),
            settings = KafkaSettingsResponse(
                clusters = listOf(
                    KafkaEditableClusterResponse(
                        id = "stage",
                        name = "Stage Kafka",
                        readOnly = true,
                        bootstrapServers = "stage-1:9092,stage-2:9092",
                        securityProtocol = "SSL",
                    ),
                ),
            ),
        )

        val updated = runKafkaSuspend {
            support.saveSettings(support.startSettingsSave(current))
        }

        assertEquals("stage", updated.selectedClusterId)
        assertEquals(listOf("stage"), updated.info?.clusters?.map { it.id })
        assertEquals("Настройки Kafka сохранены.", updated.settingsStatusMessage)
        assertNull(updated.selectedTopicName)
        assertNull(updated.topicOverview)
        assertNull(updated.messages)
    }
}
