package com.sbrf.lt.datapool.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UiConfigTest {

    @Test
    fun `loads ui config from classpath`() {
        val config = UiConfigLoader().load()

        assertEquals(8080, config.port)
    }

    @Test
    fun `configured credentials file has priority in default path resolution`() {
        val path = UiAppConfig(defaultCredentialsFile = "/tmp/custom-credentials.properties").defaultCredentialsPath()

        assertNotNull(path)
        assertEquals("/tmp/custom-credentials.properties", path.toString())
    }
}
