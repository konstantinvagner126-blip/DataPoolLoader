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
}
