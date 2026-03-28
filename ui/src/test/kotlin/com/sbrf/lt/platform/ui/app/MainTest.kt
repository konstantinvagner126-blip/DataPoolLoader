package com.sbrf.lt.platform.ui.app

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {

    @Test
    fun `runUi starts server on configured port`() {
        var config: UiAppConfig? = null

        runUi(
            configLoader = object : UiConfigLoader() {
                override fun load(): UiAppConfig {
                    return UiAppConfig(
                        port = 9090,
                        sqlConsole = SqlConsoleConfig(),
                    )
                }
            },
            starter = { loadedConfig -> config = loadedConfig },
        )

        assertEquals(9090, config?.port)
    }
}
