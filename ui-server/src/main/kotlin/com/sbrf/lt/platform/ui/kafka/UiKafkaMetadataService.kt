package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.datapool.kafka.KafkaClusterCatalogEntry
import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaMetadataOperations
import com.sbrf.lt.datapool.kafka.KafkaToolInfo
import com.sbrf.lt.datapool.kafka.KafkaTopicNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaTopicOverview
import com.sbrf.lt.datapool.kafka.KafkaTopicPartitionSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicsCatalog
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig
import com.sbrf.lt.platform.ui.config.bootstrapServers
import com.sbrf.lt.platform.ui.config.securityProtocolOrDefault

internal class ConfigBackedKafkaMetadataService(
    private val kafkaConfig: UiKafkaConfig,
    private val adminFacadeFactory: UiKafkaAdminFacadeFactory = DefaultUiKafkaAdminFacadeFactory(),
) : KafkaMetadataOperations {

    override fun info(): KafkaToolInfo =
        KafkaToolInfo(
            configured = kafkaConfig.clusters.isNotEmpty(),
            maxRecordsPerRead = kafkaConfig.maxRecordsPerRead,
            maxPayloadBytes = kafkaConfig.maxPayloadBytes,
            clusters = kafkaConfig.clusters.map { cluster ->
                cluster.toCatalogEntry()
            },
        )

    override fun listTopics(
        clusterId: String,
        query: String,
    ): KafkaTopicsCatalog {
        val cluster = requireCluster(clusterId)
        val normalizedQuery = query.trim()
        adminFacadeFactory.open(cluster).use { admin ->
            val listings = admin.listTopics()
                .filter { listing ->
                    normalizedQuery.isBlank() || listing.name.contains(normalizedQuery, ignoreCase = true)
                }
                .sortedBy { it.name.lowercase() }
            if (listings.isEmpty()) {
                return KafkaTopicsCatalog(
                    clusterId = clusterId,
                    query = normalizedQuery,
                )
            }
            val topicNames = listings.map { it.name }
            val topicDetails = admin.describeTopics(topicNames).associateBy { it.name }
            val topicConfigs = admin.describeTopicConfigs(topicNames)
            return KafkaTopicsCatalog(
                clusterId = clusterId,
                query = normalizedQuery,
                topics = topicNames.mapNotNull { topicName ->
                    topicDetails[topicName]?.toTopicSummary(topicConfigs[topicName].orEmpty())
                },
            )
        }
    }

    override fun loadTopicOverview(
        clusterId: String,
        topicName: String,
    ): KafkaTopicOverview {
        val cluster = requireCluster(clusterId)
        adminFacadeFactory.open(cluster).use { admin ->
            val topicDetails = admin.describeTopics(listOf(topicName)).firstOrNull()
                ?: throw KafkaTopicNotFoundException(clusterId, topicName)
            val topicConfig = admin.describeTopicConfigs(listOf(topicName))[topicName].orEmpty()
            val partitionOffsets = admin.loadOffsets(
                topicName = topicName,
                partitions = topicDetails.partitions.map { it.partition },
            )
            return KafkaTopicOverview(
                cluster = cluster.toCatalogEntry(),
                topic = topicDetails.toTopicSummary(topicConfig),
                partitions = topicDetails.partitions.map { partition ->
                    val offsets = partitionOffsets[partition.partition]
                    KafkaTopicPartitionSummary(
                        partition = partition.partition,
                        leaderId = partition.leaderId,
                        replicaCount = partition.replicaCount,
                        inSyncReplicaCount = partition.inSyncReplicaCount,
                        earliestOffset = offsets?.earliestOffset,
                        latestOffset = offsets?.latestOffset,
                    )
                },
            )
        }
    }

    private fun requireCluster(clusterId: String): UiKafkaClusterConfig =
        kafkaConfig.clusters.firstOrNull { it.id == clusterId }
            ?: throw KafkaClusterNotFoundException(clusterId)
}

private fun UiKafkaClusterConfig.toCatalogEntry(): KafkaClusterCatalogEntry =
    KafkaClusterCatalogEntry(
        id = id,
        name = name,
        readOnly = readOnly,
        bootstrapServers = bootstrapServers().orEmpty(),
        securityProtocol = securityProtocolOrDefault(),
    )

private fun UiKafkaTopicDetails.toTopicSummary(config: Map<String, String>): KafkaTopicSummary =
    KafkaTopicSummary(
        name = name,
        internal = internal,
        partitionCount = partitions.size,
        replicationFactor = partitions.maxOfOrNull { it.replicaCount } ?: 0,
        cleanupPolicy = config["cleanup.policy"]?.trim()?.ifBlank { null },
        retentionMs = config["retention.ms"]?.toLongOrNull(),
        retentionBytes = config["retention.bytes"]?.toLongOrNull(),
    )
