package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.datapool.kafka.KafkaClusterCatalogEntry
import com.sbrf.lt.datapool.kafka.KafkaToolInfo
import com.sbrf.lt.datapool.kafka.KafkaTopicOverview
import com.sbrf.lt.datapool.kafka.KafkaTopicPartitionSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicsCatalog

internal fun KafkaToolInfo.toResponse(): KafkaToolInfoResponse =
    KafkaToolInfoResponse(
        configured = configured,
        maxRecordsPerRead = maxRecordsPerRead,
        maxPayloadBytes = maxPayloadBytes,
        clusters = clusters.map { it.toResponse() },
    )

internal fun KafkaTopicsCatalog.toResponse(): KafkaTopicsCatalogResponse =
    KafkaTopicsCatalogResponse(
        clusterId = clusterId,
        query = query,
        topics = topics.map { it.toResponse() },
    )

internal fun KafkaTopicOverview.toResponse(): KafkaTopicOverviewResponse =
    KafkaTopicOverviewResponse(
        cluster = cluster.toResponse(),
        topic = topic.toResponse(),
        partitions = partitions.map { it.toResponse() },
    )

private fun KafkaClusterCatalogEntry.toResponse(): KafkaClusterCatalogEntryResponse =
    KafkaClusterCatalogEntryResponse(
        id = id,
        name = name,
        readOnly = readOnly,
        bootstrapServers = bootstrapServers,
        securityProtocol = securityProtocol,
    )

private fun KafkaTopicSummary.toResponse(): KafkaTopicSummaryResponse =
    KafkaTopicSummaryResponse(
        name = name,
        internal = internal,
        partitionCount = partitionCount,
        replicationFactor = replicationFactor,
        cleanupPolicy = cleanupPolicy,
        retentionMs = retentionMs,
        retentionBytes = retentionBytes,
    )

private fun KafkaTopicPartitionSummary.toResponse(): KafkaTopicPartitionSummaryResponse =
    KafkaTopicPartitionSummaryResponse(
        partition = partition,
        leaderId = leaderId,
        replicaCount = replicaCount,
        inSyncReplicaCount = inSyncReplicaCount,
        earliestOffset = earliestOffset,
        latestOffset = latestOffset,
    )
