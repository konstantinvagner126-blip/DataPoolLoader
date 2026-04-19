package com.sbrf.lt.platform.ui.run

import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiCredentialsStateStoreTest {

    @Test
    fun `migrates uploaded credentials from legacy run state into dedicated credentials state`() {
        val storageDir = createTempDirectory("ui-credentials-state-store-")
        val legacyRunState = storageDir.resolve("run-state.json")
        legacyRunState.writeText(
            """
            {
              "uploadedCredentials": {
                "fileName": "uploaded.properties",
                "content": "DB1_USERNAME=uploaded"
              },
              "history": []
            }
            """.trimIndent(),
        )

        val store = UiCredentialsStateStore(storageDir)

        val loaded = store.load()

        assertEquals("uploaded.properties", loaded.uploadedCredentials?.fileName)
        assertEquals("DB1_USERNAME=uploaded", loaded.uploadedCredentials?.content)
        val dedicatedStateFile = storageDir.resolve("credentials-state.json")
        assertTrue(dedicatedStateFile.exists())
        assertTrue(dedicatedStateFile.readText().contains("uploaded.properties"))
        assertFalse(dedicatedStateFile.readText().contains("\"history\""))
    }
}
