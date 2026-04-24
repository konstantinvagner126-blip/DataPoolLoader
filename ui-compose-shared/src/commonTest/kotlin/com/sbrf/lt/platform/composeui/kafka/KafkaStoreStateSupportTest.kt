package com.sbrf.lt.platform.composeui.kafka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KafkaStoreStateSupportTest {
    private val support = KafkaStoreStateSupport()

    @Test
    fun `resolveClusterId returns preferred cluster or first configured one`() {
        val info = KafkaToolInfoResponse(
            configured = true,
            maxRecordsPerRead = 100,
            maxPayloadBytes = 1_048_576,
            clusters = listOf(
                KafkaClusterCatalogEntryResponse(
                    id = "alpha",
                    name = "Alpha",
                    readOnly = false,
                    bootstrapServers = "alpha:9092",
                    securityProtocol = "PLAINTEXT",
                ),
                KafkaClusterCatalogEntryResponse(
                    id = "beta",
                    name = "Beta",
                    readOnly = true,
                    bootstrapServers = "beta:9092",
                    securityProtocol = "SSL",
                ),
            ),
        )

        assertEquals("beta", support.resolveClusterId(info, "beta"))
        assertEquals("alpha", support.resolveClusterId(info, "missing"))
        assertNull(
            support.resolveClusterId(
                info.copy(clusters = emptyList()),
                preferredClusterId = "beta",
            ),
        )
    }

    @Test
    fun `resolveMessagePartition and normalize route values keep Kafka route contract stable`() {
        val overview = sampleKafkaOverview()
        val topics = sampleKafkaTopics()

        assertEquals(1, support.resolveMessagePartition(1, overview))
        assertEquals(0, support.resolveMessagePartition(99, overview))
        assertNull(support.resolveMessagePartition(1, null))

        assertEquals("topics", support.normalizeClusterSection("noise"))
        assertEquals("consumer-groups", support.normalizeClusterSection("consumer_groups"))
        assertEquals("brokers", support.normalizeClusterSection("BROKERS"))
        assertEquals("messages", support.normalizePane("MESSAGES"))
        assertEquals("consumers", support.normalizePane("CONSUMERS"))
        assertEquals("cluster-settings", support.normalizePane("cluster_settings"))
        assertEquals("overview", support.normalizePane("unexpected"))
        assertEquals("overview", support.resolveActivePane("messages", null))
        assertEquals("consumers", support.resolveActivePane("consumers", "datapool-test"))
        assertEquals("cluster-settings", support.resolveActivePane("cluster-settings", null))
        assertEquals("ALL_PARTITIONS", support.normalizeMessageScope("all_partitions"))
        assertEquals("SELECTED_PARTITION", support.normalizeMessageScope("noise"))
        assertEquals("TIMESTAMP", support.normalizeMessageMode("timestamp"))
        assertEquals("LATEST", support.normalizeMessageMode("noise"))

        assertNull(support.resolveTopicName(topics, null))
        assertEquals("datapool-test", support.resolveTopicName(topics, "datapool-test"))
        assertNull(support.resolveTopicName(topics, "missing"))
    }
}
