package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig
import org.apache.kafka.common.errors.AuthorizationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiKafkaMetadataServiceTest {

    @Test
    fun `lists filtered topics from configured cluster`() {
        val service = ConfigBackedKafkaMetadataService(
            kafkaConfig = testKafkaConfig(),
            adminFacadeFactory = FakeUiKafkaAdminFacadeFactory(
                topics = listOf(
                    UiKafkaTopicListing("datapool-test", false),
                    UiKafkaTopicListing("__consumer_offsets", true),
                ),
                topicDetails = listOf(
                    UiKafkaTopicDetails(
                        name = "datapool-test",
                        internal = false,
                        partitions = listOf(
                            UiKafkaPartitionDetails(partition = 0, leaderId = 1, replicaCount = 3, inSyncReplicaCount = 3),
                            UiKafkaPartitionDetails(partition = 1, leaderId = 2, replicaCount = 3, inSyncReplicaCount = 2),
                        ),
                    ),
                    UiKafkaTopicDetails(
                        name = "__consumer_offsets",
                        internal = true,
                        partitions = listOf(
                            UiKafkaPartitionDetails(partition = 0, leaderId = 1, replicaCount = 1, inSyncReplicaCount = 1),
                        ),
                    ),
                ),
                topicConfigs = mapOf(
                    "datapool-test" to mapOf("cleanup.policy" to "delete", "retention.ms" to "86400000"),
                    "__consumer_offsets" to mapOf("cleanup.policy" to "compact"),
                ),
            ),
        )

        val catalog = service.listTopics(clusterId = "local", query = "data")

        assertEquals("local", catalog.clusterId)
        assertEquals("data", catalog.query)
        assertEquals(listOf("datapool-test"), catalog.topics.map { it.name })
        assertEquals(2, catalog.topics.single().partitionCount)
        assertEquals(3, catalog.topics.single().replicationFactor)
        assertEquals("delete", catalog.topics.single().cleanupPolicy)
        assertEquals(86_400_000L, catalog.topics.single().retentionMs)
    }

    @Test
    fun `loads topic overview with partition offsets`() {
        val service = ConfigBackedKafkaMetadataService(
            kafkaConfig = testKafkaConfig(),
            adminFacadeFactory = FakeUiKafkaAdminFacadeFactory(
                topics = listOf(UiKafkaTopicListing("datapool-test", false)),
                topicDetails = listOf(
                    UiKafkaTopicDetails(
                        name = "datapool-test",
                        internal = false,
                        partitions = listOf(
                            UiKafkaPartitionDetails(partition = 0, leaderId = 1, replicaCount = 2, inSyncReplicaCount = 2),
                            UiKafkaPartitionDetails(partition = 1, leaderId = 2, replicaCount = 2, inSyncReplicaCount = 1),
                        ),
                    ),
                ),
                topicConfigs = mapOf(
                    "datapool-test" to mapOf("cleanup.policy" to "delete", "retention.bytes" to "1048576"),
                ),
                partitionOffsets = mapOf(
                    "datapool-test" to mapOf(
                        0 to UiKafkaPartitionOffsets(earliestOffset = 0, latestOffset = 12),
                        1 to UiKafkaPartitionOffsets(earliestOffset = 4, latestOffset = 27),
                    ),
                ),
                consumerGroups = listOf(
                    UiKafkaConsumerGroupListing("datapool-test-group"),
                    UiKafkaConsumerGroupListing("other-group"),
                ),
                consumerGroupDetails = mapOf(
                    "datapool-test-group" to UiKafkaConsumerGroupDetails(
                        groupId = "datapool-test-group",
                        state = "STABLE",
                        memberCount = 2,
                    ),
                ),
                consumerGroupOffsets = mapOf(
                    "datapool-test-group" to listOf(
                        UiKafkaCommittedOffset(topicName = "datapool-test", partition = 0, committedOffset = 10),
                        UiKafkaCommittedOffset(topicName = "datapool-test", partition = 1, committedOffset = 20),
                    ),
                    "other-group" to listOf(
                        UiKafkaCommittedOffset(topicName = "another-topic", partition = 0, committedOffset = 3),
                    ),
                ),
            ),
        )

        val overview = service.loadTopicOverview(clusterId = "local", topicName = "datapool-test")

        assertEquals("local", overview.cluster.id)
        assertEquals("datapool-test", overview.topic.name)
        assertEquals(2, overview.partitions.size)
        assertEquals(27L, overview.partitions.last().latestOffset)
        assertEquals(1_048_576L, overview.topic.retentionBytes)
        assertEquals("AVAILABLE", overview.consumerGroups.status.name)
        assertEquals(1, overview.consumerGroups.groups.size)
        assertEquals("datapool-test-group", overview.consumerGroups.groups.single().groupId)
        assertEquals(2, overview.consumerGroups.groups.single().memberCount)
        assertEquals(9L, overview.consumerGroups.groups.single().totalLag)
    }

    @Test
    fun `fails for unknown cluster`() {
        val service = ConfigBackedKafkaMetadataService(
            kafkaConfig = testKafkaConfig(),
            adminFacadeFactory = FakeUiKafkaAdminFacadeFactory(),
        )

        assertFailsWith<KafkaClusterNotFoundException> {
            service.listTopics(clusterId = "missing")
        }
    }

    @Test
    fun `keeps topic overview when consumer group metadata is unavailable`() {
        val service = ConfigBackedKafkaMetadataService(
            kafkaConfig = testKafkaConfig(),
            adminFacadeFactory = FakeUiKafkaAdminFacadeFactory(
                topicDetails = listOf(
                    UiKafkaTopicDetails(
                        name = "datapool-test",
                        internal = false,
                        partitions = listOf(
                            UiKafkaPartitionDetails(partition = 0, leaderId = 1, replicaCount = 1, inSyncReplicaCount = 1),
                        ),
                    ),
                ),
                partitionOffsets = mapOf(
                    "datapool-test" to mapOf(
                        0 to UiKafkaPartitionOffsets(earliestOffset = 0, latestOffset = 15),
                    ),
                ),
                consumerGroups = listOf(
                    UiKafkaConsumerGroupListing("datapool-test-group"),
                ),
                consumerGroupOffsets = mapOf(
                    "datapool-test-group" to listOf(
                        UiKafkaCommittedOffset(topicName = "datapool-test", partition = 0, committedOffset = 11),
                    ),
                ),
                consumerGroupDescribeFailures = mapOf(
                    "datapool-test-group" to AuthorizationException("denied"),
                ),
            ),
        )

        val overview = service.loadTopicOverview(clusterId = "local", topicName = "datapool-test")

        assertEquals("AVAILABLE", overview.consumerGroups.status.name)
        assertEquals(1, overview.consumerGroups.groups.size)
        val group = overview.consumerGroups.groups.single()
        assertFalse(group.metadataAvailable)
        assertEquals(4L, group.totalLag)
        assertTrue(group.note.orEmpty().contains("не хватает прав"))
    }

    @Test
    fun `keeps topic overview when consumer groups are unauthorized`() {
        val service = ConfigBackedKafkaMetadataService(
            kafkaConfig = testKafkaConfig(),
            adminFacadeFactory = FakeUiKafkaAdminFacadeFactory(
                topicDetails = listOf(
                    UiKafkaTopicDetails(
                        name = "datapool-test",
                        internal = false,
                        partitions = listOf(
                            UiKafkaPartitionDetails(partition = 0, leaderId = 1, replicaCount = 1, inSyncReplicaCount = 1),
                        ),
                    ),
                ),
                partitionOffsets = mapOf(
                    "datapool-test" to mapOf(
                        0 to UiKafkaPartitionOffsets(earliestOffset = 0, latestOffset = 15),
                    ),
                ),
                consumerGroupListFailure = AuthorizationException("denied"),
            ),
        )

        val overview = service.loadTopicOverview(clusterId = "local", topicName = "datapool-test")

        assertEquals("ERROR", overview.consumerGroups.status.name)
        assertTrue(overview.consumerGroups.message.orEmpty().contains("не хватает прав"))
        assertEquals(15L, overview.partitions.single().latestOffset)
    }
}

private fun testKafkaConfig(): UiKafkaConfig =
    UiKafkaConfig(
        clusters = listOf(
            UiKafkaClusterConfig(
                id = "local",
                name = "Local Kafka",
                readOnly = false,
                properties = mapOf(
                    "bootstrap.servers" to "localhost:19092",
                    "security.protocol" to "PLAINTEXT",
                ),
            ),
        ),
    )

private class FakeUiKafkaAdminFacadeFactory(
    private val topics: List<UiKafkaTopicListing> = emptyList(),
    private val topicDetails: List<UiKafkaTopicDetails> = emptyList(),
    private val topicConfigs: Map<String, Map<String, String>> = emptyMap(),
    private val partitionOffsets: Map<String, Map<Int, UiKafkaPartitionOffsets>> = emptyMap(),
    private val consumerGroups: List<UiKafkaConsumerGroupListing> = emptyList(),
    private val consumerGroupDetails: Map<String, UiKafkaConsumerGroupDetails> = emptyMap(),
    private val consumerGroupOffsets: Map<String, List<UiKafkaCommittedOffset>> = emptyMap(),
    private val consumerGroupListFailure: Throwable? = null,
    private val consumerGroupDescribeFailures: Map<String, Throwable> = emptyMap(),
    private val consumerGroupOffsetsFailures: Map<String, Throwable> = emptyMap(),
) : UiKafkaAdminFacadeFactory {
    override fun open(cluster: UiKafkaClusterConfig): UiKafkaAdminFacade =
        object : UiKafkaAdminFacade {
            override fun loadBrokerNodeCount(): Int = 1

            override fun listTopics(): List<UiKafkaTopicListing> = topics

            override fun describeTopics(topicNames: List<String>): List<UiKafkaTopicDetails> =
                topicNames.mapNotNull { topicName -> topicDetails.firstOrNull { it.name == topicName } }

            override fun describeTopicConfigs(topicNames: List<String>): Map<String, Map<String, String>> =
                topicNames.associateWith { topicName -> topicConfigs[topicName].orEmpty() }

            override fun listConsumerGroups(): List<UiKafkaConsumerGroupListing> {
                consumerGroupListFailure?.let { throw it }
                return consumerGroups
            }

            override fun describeConsumerGroups(groupIds: List<String>): Map<String, UiKafkaConsumerGroupDetails> {
                groupIds.firstOrNull { consumerGroupDescribeFailures.containsKey(it) }?.let { failingGroupId ->
                    throw consumerGroupDescribeFailures.getValue(failingGroupId)
                }
                return groupIds.mapNotNull { groupId ->
                    consumerGroupDetails[groupId]?.let { groupId to it }
                }.toMap()
            }

            override fun loadConsumerGroupOffsets(groupId: String): List<UiKafkaCommittedOffset> {
                consumerGroupOffsetsFailures[groupId]?.let { throw it }
                return consumerGroupOffsets[groupId].orEmpty()
            }

            override fun loadOffsets(
                topicName: String,
                partitions: List<Int>,
            ): Map<Int, UiKafkaPartitionOffsets> =
                partitions.associateWith { partition ->
                    partitionOffsets[topicName]?.get(partition) ?: UiKafkaPartitionOffsets()
                }

            override fun close() = Unit
        }
}
