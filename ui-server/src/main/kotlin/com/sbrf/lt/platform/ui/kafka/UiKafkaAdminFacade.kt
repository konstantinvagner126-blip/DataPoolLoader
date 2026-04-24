package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.ConfigResource

internal interface UiKafkaAdminFacadeFactory {
    fun open(cluster: UiKafkaClusterConfig): UiKafkaAdminFacade
}

internal interface UiKafkaAdminFacade : AutoCloseable {
    fun loadBrokerNodeCount(): Int

    fun describeClusterBrokers(): UiKafkaClusterBrokers

    fun listTopics(): List<UiKafkaTopicListing>

    fun describeTopics(topicNames: List<String>): List<UiKafkaTopicDetails>

    fun describeTopicConfigs(topicNames: List<String>): Map<String, Map<String, String>>

    fun listConsumerGroups(): List<UiKafkaConsumerGroupListing>

    fun describeConsumerGroups(groupIds: List<String>): Map<String, UiKafkaConsumerGroupDetails>

    fun loadConsumerGroupOffsets(groupId: String): List<UiKafkaCommittedOffset>

    fun loadOffsets(
        topicName: String,
        partitions: List<Int>,
    ): Map<Int, UiKafkaPartitionOffsets>
}

internal data class UiKafkaTopicListing(
    val name: String,
    val internal: Boolean,
)

internal data class UiKafkaTopicDetails(
    val name: String,
    val internal: Boolean,
    val partitions: List<UiKafkaPartitionDetails> = emptyList(),
)

internal data class UiKafkaPartitionDetails(
    val partition: Int,
    val leaderId: Int? = null,
    val replicaCount: Int,
    val inSyncReplicaCount: Int,
)

internal data class UiKafkaPartitionOffsets(
    val earliestOffset: Long? = null,
    val latestOffset: Long? = null,
)

internal data class UiKafkaClusterBrokers(
    val controllerBrokerId: Int? = null,
    val brokers: List<UiKafkaBrokerNode> = emptyList(),
)

internal data class UiKafkaBrokerNode(
    val brokerId: Int,
    val host: String,
    val port: Int,
    val rack: String? = null,
)

internal data class UiKafkaConsumerGroupListing(
    val groupId: String,
)

internal data class UiKafkaConsumerGroupDetails(
    val groupId: String,
    val state: String? = null,
    val memberCount: Int,
)

internal data class UiKafkaCommittedOffset(
    val topicName: String,
    val partition: Int,
    val committedOffset: Long,
)

internal class DefaultUiKafkaAdminFacadeFactory(
    private val clientFactory: UiKafkaClientFactory = DefaultUiKafkaClientFactory(),
) : UiKafkaAdminFacadeFactory {
    override fun open(cluster: UiKafkaClusterConfig): UiKafkaAdminFacade =
        AdminClientUiKafkaAdminFacade(
            clientFactory.createAdminClient(cluster),
        )
}

private class AdminClientUiKafkaAdminFacade(
    private val adminClient: org.apache.kafka.clients.admin.AdminClient,
) : UiKafkaAdminFacade {

    override fun loadBrokerNodeCount(): Int =
        adminClient.describeCluster().nodes().get().size

    override fun describeClusterBrokers(): UiKafkaClusterBrokers {
        val description = adminClient.describeCluster()
        val brokers = description.nodes().get().map { node ->
            UiKafkaBrokerNode(
                brokerId = node.id(),
                host = node.host(),
                port = node.port(),
                rack = node.rack(),
            )
        }.sortedBy { it.brokerId }
        val controllerBrokerId = runCatching {
            description.controller().get()?.id()?.takeIf { it >= 0 }
        }.getOrNull()
        return UiKafkaClusterBrokers(
            controllerBrokerId = controllerBrokerId,
            brokers = brokers,
        )
    }

    override fun listTopics(): List<UiKafkaTopicListing> =
        adminClient.listTopics().listings().get().map { listing ->
            UiKafkaTopicListing(
                name = listing.name(),
                internal = listing.isInternal,
            )
        }

    override fun describeTopics(topicNames: List<String>): List<UiKafkaTopicDetails> {
        if (topicNames.isEmpty()) {
            return emptyList()
        }
        val descriptions = adminClient.describeTopics(topicNames).allTopicNames().get()
        return topicNames.mapNotNull { topicName ->
            descriptions[topicName]?.let { description ->
                UiKafkaTopicDetails(
                    name = description.name(),
                    internal = description.isInternal,
                    partitions = description.partitions().map { partitionInfo ->
                        UiKafkaPartitionDetails(
                            partition = partitionInfo.partition(),
                            leaderId = partitionInfo.leader()?.id()?.takeIf { it >= 0 },
                            replicaCount = partitionInfo.replicas().size,
                            inSyncReplicaCount = partitionInfo.isr().size,
                        )
                    }.sortedBy { it.partition },
                )
            }
        }
    }

    override fun describeTopicConfigs(topicNames: List<String>): Map<String, Map<String, String>> {
        if (topicNames.isEmpty()) {
            return emptyMap()
        }
        val resources = topicNames.map { ConfigResource(ConfigResource.Type.TOPIC, it) }
        val configs = adminClient.describeConfigs(resources).all().get()
        return topicNames.associateWith { topicName ->
            configs[ConfigResource(ConfigResource.Type.TOPIC, topicName)]
                ?.entries()
                ?.associate { it.name() to it.value() }
                .orEmpty()
        }
    }

    override fun listConsumerGroups(): List<UiKafkaConsumerGroupListing> =
        adminClient.listConsumerGroups().all().get().map { listing ->
            UiKafkaConsumerGroupListing(
                groupId = listing.groupId(),
            )
        }

    override fun describeConsumerGroups(groupIds: List<String>): Map<String, UiKafkaConsumerGroupDetails> {
        if (groupIds.isEmpty()) {
            return emptyMap()
        }
        val descriptions = adminClient.describeConsumerGroups(groupIds).all().get()
        return descriptions.mapValues { (_, description) ->
            UiKafkaConsumerGroupDetails(
                groupId = description.groupId(),
                state = description.state()?.toString(),
                memberCount = description.members().size,
            )
        }
    }

    override fun loadConsumerGroupOffsets(groupId: String): List<UiKafkaCommittedOffset> =
        adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get().map { (topicPartition, offsetAndMetadata) ->
            UiKafkaCommittedOffset(
                topicName = topicPartition.topic(),
                partition = topicPartition.partition(),
                committedOffset = offsetAndMetadata.offset(),
            )
        }

    override fun loadOffsets(
        topicName: String,
        partitions: List<Int>,
    ): Map<Int, UiKafkaPartitionOffsets> {
        if (partitions.isEmpty()) {
            return emptyMap()
        }
        val earliestOffsets = listOffsets(topicName, partitions, OffsetSpec.earliest())
        val latestOffsets = listOffsets(topicName, partitions, OffsetSpec.latest())
        return partitions.associateWith { partition ->
            UiKafkaPartitionOffsets(
                earliestOffset = earliestOffsets[partition],
                latestOffset = latestOffsets[partition],
            )
        }
    }

    private fun listOffsets(
        topicName: String,
        partitions: List<Int>,
        offsetSpec: OffsetSpec,
    ): Map<Int, Long?> {
        val requests = partitions.associate { partition ->
            TopicPartition(topicName, partition) to offsetSpec
        }
        return adminClient.listOffsets(requests).all().get().entries.associate { (topicPartition, resultInfo) ->
            topicPartition.partition() to resultInfo.offset()
        }
    }

    override fun close() {
        adminClient.close()
    }
}
