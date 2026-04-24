package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.config.ConfigLoader
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
        val legacyRunStateTree = ConfigLoader().objectMapper().readTree(legacyRunState.toFile())
        assertFalse(legacyRunStateTree.has("uploadedCredentials"))
        assertTrue(legacyRunStateTree.has("history"))
    }

    @Test
    fun `existing dedicated credentials state clears stale legacy uploaded credentials copy`() {
        val storageDir = createTempDirectory("ui-credentials-state-store-")
        val legacyRunState = storageDir.resolve("run-state.json")
        val dedicatedStateFile = storageDir.resolve("credentials-state.json")
        legacyRunState.writeText(
            """
            {
              "uploadedCredentials": {
                "fileName": "legacy.properties",
                "content": "DB1_USERNAME=legacy"
              },
              "history": [
                {
                  "id": "run-1"
                }
              ]
            }
            """.trimIndent(),
        )
        dedicatedStateFile.writeText(
            """
            {
              "uploadedCredentials": {
                "fileName": "dedicated.properties",
                "content": "DB1_USERNAME=dedicated"
              }
            }
            """.trimIndent(),
        )

        val store = UiCredentialsStateStore(storageDir)

        val loaded = store.load()

        assertEquals("dedicated.properties", loaded.uploadedCredentials?.fileName)
        assertEquals("DB1_USERNAME=dedicated", loaded.uploadedCredentials?.content)
        assertTrue(dedicatedStateFile.readText().contains("dedicated.properties"))
        assertFalse(dedicatedStateFile.readText().contains("legacy.properties"))
        val legacyRunStateTree = ConfigLoader().objectMapper().readTree(legacyRunState.toFile())
        assertFalse(legacyRunStateTree.has("uploadedCredentials"))
        assertTrue(legacyRunStateTree.has("history"))
    }
}
