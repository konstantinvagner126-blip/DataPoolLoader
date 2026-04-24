package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaTopicCreateRequest
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UiKafkaTopicAdminServiceTest {

    @Test
    fun `creates topic with optional configs`() {
        val captures = mutableListOf<FakeTopicCreateCapture>()
        val service = ConfigBackedKafkaTopicAdminService(
            kafkaConfig = kafkaConfig(),
            adminFacadeFactory = FakeUiKafkaTopicAdminFacadeFactory(captures = captures),
        )

        val result = service.createTopic(
            KafkaTopicCreateRequest(
                clusterId = "local",
                topicName = "orders.events",
                partitionCount = 3,
                replicationFactor = 2,
                cleanupPolicy = "compact",
                retentionMs = 120_000,
                retentionBytes = 1_048_576,
            ),
        )

        assertEquals("orders.events", result.topicName)
        assertEquals(3, result.partitionCount)
        assertEquals(2, result.replicationFactor)
        assertEquals("compact", captures.single().configs["cleanup.policy"])
        assertEquals("120000", captures.single().configs["retention.ms"])
        assertEquals("1048576", captures.single().configs["retention.bytes"])
    }

    @Test
    fun `rejects create topic for read only cluster`() {
        val service = ConfigBackedKafkaTopicAdminService(
            kafkaConfig = kafkaConfig(readOnly = true),
            adminFacadeFactory = FakeUiKafkaTopicAdminFacadeFactory(),
        )

        assertFailsWith<IllegalArgumentException> {
            service.createTopic(
                KafkaTopicCreateRequest(
                    clusterId = "local",
                    topicName = "orders.events",
                    partitionCount = 1,
                    replicationFactor = 1,
                ),
            )
        }
    }

    @Test
    fun `fails for missing cluster`() {
        val service = ConfigBackedKafkaTopicAdminService(
            kafkaConfig = kafkaConfig(),
            adminFacadeFactory = FakeUiKafkaTopicAdminFacadeFactory(),
        )

        assertFailsWith<KafkaClusterNotFoundException> {
            service.createTopic(
                KafkaTopicCreateRequest(
                    clusterId = "missing",
                    topicName = "orders.events",
                    partitionCount = 1,
                    replicationFactor = 1,
                ),
            )
        }
    }
}

private fun kafkaConfig(readOnly: Boolean = false): UiKafkaConfig =
    UiKafkaConfig(
        clusters = listOf(
            UiKafkaClusterConfig(
                id = "local",
                name = "Local Kafka",
                readOnly = readOnly,
                properties = mapOf(
                    "bootstrap.servers" to "localhost:19092",
                    "security.protocol" to "PLAINTEXT",
                ),
            ),
        ),
    )

private data class FakeTopicCreateCapture(
    val topicName: String,
    val partitionCount: Int,
    val replicationFactor: Short,
    val configs: Map<String, String>,
)

private class FakeUiKafkaTopicAdminFacadeFactory(
    private val captures: MutableList<FakeTopicCreateCapture> = mutableListOf(),
) : UiKafkaAdminFacadeFactory {
    override fun open(cluster: UiKafkaClusterConfig): UiKafkaAdminFacade =
        object : UiKafkaAdminFacade {
            override fun loadBrokerNodeCount(): Int = 1

            override fun describeClusterBrokers(): UiKafkaClusterBrokers = UiKafkaClusterBrokers()

            override fun listTopics(): List<UiKafkaTopicListing> = emptyList()

            override fun describeTopics(topicNames: List<String>): List<UiKafkaTopicDetails> = emptyList()

            override fun describeTopicConfigs(topicNames: List<String>): Map<String, Map<String, String>> = emptyMap()

            override fun createTopic(
                topicName: String,
                partitionCount: Int,
                replicationFactor: Short,
                configs: Map<String, String>,
            ) {
                this@FakeUiKafkaTopicAdminFacadeFactory.captures += FakeTopicCreateCapture(
                    topicName,
                    partitionCount,
                    replicationFactor,
                    configs,
                )
            }

            override fun listConsumerGroups(): List<UiKafkaConsumerGroupListing> = emptyList()

            override fun describeConsumerGroups(groupIds: List<String>): Map<String, UiKafkaConsumerGroupDetails> = emptyMap()

            override fun loadConsumerGroupOffsets(groupId: String): List<UiKafkaCommittedOffset> = emptyList()

            override fun loadOffsets(topicName: String, partitions: List<Int>): Map<Int, UiKafkaPartitionOffsets> = emptyMap()

            override fun close() = Unit
        }
}
