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
}
