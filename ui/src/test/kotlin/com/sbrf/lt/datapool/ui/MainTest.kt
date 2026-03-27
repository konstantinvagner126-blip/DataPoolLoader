package com.sbrf.lt.datapool.ui

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {

    @Test
    fun `runUi starts server on configured port`() {
        var port: Int? = null

        runUi(
            configLoader = object : UiConfigLoader() {
                override fun load(): UiAppConfig {
                    return UiAppConfig(
                        port = 9090,
                        sqlConsole = SqlConsoleConfig(),
                    )
                }
            },
            starter = { startedPort -> port = startedPort },
        )

        assertEquals(9090, port)
    }
}
