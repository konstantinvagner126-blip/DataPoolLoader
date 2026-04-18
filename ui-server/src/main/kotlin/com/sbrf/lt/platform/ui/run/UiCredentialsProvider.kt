package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import java.nio.file.Path

/**
 * Общий контракт доступа к credential.properties для разных сценариев запуска UI.
 */
interface UiCredentialsProvider {
    fun currentCredentialsStatus(): CredentialsStatusResponse
    fun materializeCredentialsFile(tempDir: Path): Path?
    fun currentProperties(): Map<String, String> = emptyMap()
}
