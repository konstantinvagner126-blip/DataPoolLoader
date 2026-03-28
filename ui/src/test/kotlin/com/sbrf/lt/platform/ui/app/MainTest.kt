package com.sbrf.lt.platform.ui.app

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `runUi uses default loader factory and starter`() {
        val previousFactory = configLoaderFactory
        val previousStarter = uiStarter
        var config: UiAppConfig? = null
        try {
            configLoaderFactory = {
                object : UiConfigLoader() {
                    override fun load(): UiAppConfig = UiAppConfig(
                        port = 9191,
                        sqlConsole = SqlConsoleConfig(),
                    )
                }
            }
            uiStarter = { loadedConfig -> config = loadedConfig }

            runUi()

            assertEquals(9191, config?.port)
        } finally {
            configLoaderFactory = previousFactory
            uiStarter = previousStarter
        }
    }

    @Test
    fun `main delegates to configured entry point`() {
        val previous = mainEntryPoint
        var invoked = false
        try {
            mainEntryPoint = { invoked = true }

            main()

            assertTrue(invoked)
        } finally {
            mainEntryPoint = previous
        }
    }

    @Test
    fun `main uses default entry point`() {
        val previousEntryPoint = mainEntryPoint
        val previousFactory = configLoaderFactory
        val previousStarter = uiStarter
        var loadedPort: Int? = null
        try {
            mainEntryPoint = ::runUi
            configLoaderFactory = {
                object : UiConfigLoader() {
                    override fun load(): UiAppConfig = UiAppConfig(
                        port = 9393,
                        sqlConsole = SqlConsoleConfig(),
                    )
                }
            }
            uiStarter = { config -> loadedPort = config.port }

            main()

            assertEquals(9393, loadedPort)
        } finally {
            mainEntryPoint = previousEntryPoint
            configLoaderFactory = previousFactory
            uiStarter = previousStarter
        }
    }
}
