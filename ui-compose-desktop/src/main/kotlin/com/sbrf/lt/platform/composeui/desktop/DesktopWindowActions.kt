package com.sbrf.lt.platform.composeui.desktop

import java.awt.Desktop
import java.net.URI

interface DesktopWindowActions {
    fun openWebUi()
    fun close()
}

class DefaultDesktopWindowActions(
    private val serverBaseUrl: String,
    private val onClose: () -> Unit,
) : DesktopWindowActions {
    override fun openWebUi() {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(serverBaseUrl))
            }
        }
    }

    override fun close() {
        onClose()
    }
}
