package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.datapool.kafka.KafkaClusterCatalogEntry
import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaMetadataOperations
import com.sbrf.lt.datapool.kafka.KafkaToolInfo
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupLagStatus
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupPartitionLag
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupsStatus
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupsSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaTopicOverview
import com.sbrf.lt.datapool.kafka.KafkaTopicPartitionSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicsCatalog
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig
import com.sbrf.lt.platform.ui.config.bootstrapServers
import com.sbrf.lt.platform.ui.config.securityProtocolOrDefault
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.TimeoutException

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
                consumerGroups = loadTopicConsumerGroups(
                    admin = admin,
                    topicName = topicName,
                    partitions = topicDetails.partitions.map { it.partition }.sorted(),
                    latestOffsets = partitionOffsets.mapValues { (_, offsets) -> offsets.latestOffset },
                ),
            )
        }
    }

    private fun requireCluster(clusterId: String): UiKafkaClusterConfig =
        kafkaConfig.clusters.firstOrNull { it.id == clusterId }
            ?: throw KafkaClusterNotFoundException(clusterId)

    private fun loadTopicConsumerGroups(
        admin: UiKafkaAdminFacade,
        topicName: String,
        partitions: List<Int>,
        latestOffsets: Map<Int, Long?>,
    ): KafkaTopicConsumerGroupsSummary {
        val groupListings = try {
            admin.listConsumerGroups().sortedBy { it.groupId.lowercase() }
        } catch (e: Throwable) {
            return KafkaTopicConsumerGroupsSummary(
                status = KafkaTopicConsumerGroupsStatus.ERROR,
                message = describeConsumerGroupSectionFailure(e),
            )
        }
        if (groupListings.isEmpty()) {
            return KafkaTopicConsumerGroupsSummary(
                status = KafkaTopicConsumerGroupsStatus.EMPTY,
                message = "В кластере нет consumer groups.",
            )
        }

        val summaries = mutableListOf<KafkaTopicConsumerGroupSummary>()
        var unresolvedGroupCount = 0
        groupListings.forEach { listing ->
            when (val resolution = loadTopicConsumerGroupSummary(admin, listing.groupId, topicName, partitions, latestOffsets)) {
                KafkaTopicConsumerGroupResolution.UNRELATED -> Unit
                KafkaTopicConsumerGroupResolution.UNRESOLVED -> unresolvedGroupCount += 1
                is KafkaTopicConsumerGroupResolution.RELATED -> summaries += resolution.summary
            }
        }

        if (summaries.isEmpty()) {
            if (unresolvedGroupCount > 0) {
                return KafkaTopicConsumerGroupsSummary(
                    status = KafkaTopicConsumerGroupsStatus.ERROR,
                    message = "Не удалось определить consumer groups для топика: offsets недоступны для части или всех групп.",
                )
            }
            return KafkaTopicConsumerGroupsSummary(
                status = KafkaTopicConsumerGroupsStatus.EMPTY,
                message = "Для этого топика нет consumer groups с committed offsets.",
            )
        }

        val message = if (unresolvedGroupCount > 0) {
            "Для части consumer groups offsets недоступны. Список может быть неполным."
        } else {
            null
        }
        return KafkaTopicConsumerGroupsSummary(
            status = KafkaTopicConsumerGroupsStatus.AVAILABLE,
            message = message,
            groups = summaries.sortedBy { it.groupId.lowercase() },
        )
    }

    private fun loadTopicConsumerGroupSummary(
        admin: UiKafkaAdminFacade,
        groupId: String,
        topicName: String,
        partitions: List<Int>,
        latestOffsets: Map<Int, Long?>,
    ): KafkaTopicConsumerGroupResolution {
        val committedOffsets = try {
            admin.loadConsumerGroupOffsets(groupId)
                .filter { it.topicName == topicName }
                .associateBy { it.partition }
        } catch (_: Throwable) {
            return KafkaTopicConsumerGroupResolution.UNRESOLVED
        }
        if (committedOffsets.isEmpty()) {
            return KafkaTopicConsumerGroupResolution.UNRELATED
        }

        val partitionLag = partitions.map { partition ->
            val committedOffset = committedOffsets[partition]?.committedOffset
            val latestOffset = latestOffsets[partition]
            KafkaTopicConsumerGroupPartitionLag(
                partition = partition,
                committedOffset = committedOffset,
                latestOffset = latestOffset,
                lag = if (committedOffset != null && latestOffset != null) {
                    (latestOffset - committedOffset).coerceAtLeast(0L)
                } else {
                    null
                },
            )
        }
        val partitionsWithoutLag = partitionLag.count { it.committedOffset == null || it.latestOffset == null || it.lag == null }
        val lagStatus = if (partitionsWithoutLag > 0) {
            KafkaTopicConsumerGroupLagStatus.PARTIAL
        } else {
            KafkaTopicConsumerGroupLagStatus.OK
        }
        val totalLag = partitionLag.mapNotNull { it.lag }.sum().takeIf { partitionLag.any { lag -> lag.lag != null } }
        val lagNote = when {
            partitionsWithoutLag == 0 -> null
            committedOffsets.size < partitions.size -> "Committed offsets есть не для всех partitions топика."
            else -> "Часть lag metadata недоступна для partitions топика."
        }

        val description = try {
            admin.describeConsumerGroups(listOf(groupId))[groupId]
        } catch (e: Throwable) {
            return KafkaTopicConsumerGroupResolution.RELATED(
                KafkaTopicConsumerGroupSummary(
                groupId = groupId,
                metadataAvailable = false,
                totalLag = totalLag,
                lagStatus = lagStatus,
                note = mergeGroupNotes(
                    lagNote,
                    describeConsumerGroupMetadataFailure(e),
                ),
                partitions = partitionLag,
                ),
            )
        }

        return KafkaTopicConsumerGroupResolution.RELATED(
            KafkaTopicConsumerGroupSummary(
                groupId = groupId,
                state = description?.state,
                memberCount = description?.memberCount,
                metadataAvailable = description != null,
                totalLag = totalLag,
                lagStatus = lagStatus,
                note = mergeGroupNotes(
                    lagNote,
                    if (description == null) "Metadata группы недоступна: Kafka не вернула group description." else null,
                ),
                partitions = partitionLag,
            ),
        )
    }
}

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

private sealed interface KafkaTopicConsumerGroupResolution {
    data class RELATED(
        val summary: KafkaTopicConsumerGroupSummary,
    ) : KafkaTopicConsumerGroupResolution

    object UNRELATED : KafkaTopicConsumerGroupResolution

    object UNRESOLVED : KafkaTopicConsumerGroupResolution
}

private fun describeConsumerGroupSectionFailure(error: Throwable): String =
    when (classifyKafkaOperationFailure(error)) {
        KafkaOperationFailure.AUTHORIZATION ->
            "Consumer groups недоступны: не хватает прав на list/offset metadata."
        KafkaOperationFailure.TIMEOUT ->
            "Consumer groups недоступны: Kafka admin timeout при чтении group metadata."
        KafkaOperationFailure.ERROR ->
            "Consumer groups недоступны: ${error.rootCause().message ?: error.rootCause().javaClass.simpleName}"
    }

private fun describeConsumerGroupMetadataFailure(error: Throwable): String =
    when (classifyKafkaOperationFailure(error)) {
        KafkaOperationFailure.AUTHORIZATION ->
            "Metadata группы недоступна: не хватает прав."
        KafkaOperationFailure.TIMEOUT ->
            "Metadata группы недоступна: Kafka admin timeout."
        KafkaOperationFailure.ERROR ->
            "Metadata группы недоступна: ${error.rootCause().message ?: error.rootCause().javaClass.simpleName}"
    }

private fun mergeGroupNotes(
    lagNote: String?,
    metadataNote: String?,
): String? =
    listOfNotNull(lagNote, metadataNote).joinToString(" ").ifBlank { null }

private fun classifyKafkaOperationFailure(error: Throwable): KafkaOperationFailure =
    when (val rootCause = error.rootCause()) {
        is AuthorizationException -> KafkaOperationFailure.AUTHORIZATION
        is TimeoutException -> KafkaOperationFailure.TIMEOUT
        else -> KafkaOperationFailure.ERROR
    }

private fun Throwable.rootCause(): Throwable {
    var current = this
    while (current.cause != null && current.cause !== current) {
        current = current.cause!!
    }
    return current
}

private enum class KafkaOperationFailure {
    AUTHORIZATION,
    TIMEOUT,
    ERROR,
}
