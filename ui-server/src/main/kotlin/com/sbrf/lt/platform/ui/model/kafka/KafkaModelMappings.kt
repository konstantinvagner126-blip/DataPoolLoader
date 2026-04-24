package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.datapool.kafka.KafkaClusterCatalogEntry
import com.sbrf.lt.datapool.kafka.KafkaClusterBrokersCatalog
import com.sbrf.lt.datapool.kafka.KafkaClusterConsumerGroupSummary
import com.sbrf.lt.datapool.kafka.KafkaClusterConsumerGroupTopicSummary
import com.sbrf.lt.datapool.kafka.KafkaClusterConsumerGroupsCatalog
import com.sbrf.lt.datapool.kafka.KafkaBrokerSummary
import com.sbrf.lt.datapool.kafka.KafkaProduceOperations
import com.sbrf.lt.datapool.kafka.KafkaRenderedBytes
import com.sbrf.lt.datapool.kafka.KafkaToolInfo
import com.sbrf.lt.datapool.kafka.KafkaTopicAdminOperations
import com.sbrf.lt.datapool.kafka.KafkaTopicCreateRequest
import com.sbrf.lt.datapool.kafka.KafkaTopicCreateResult
import com.sbrf.lt.datapool.kafka.KafkaTopicProduceHeader
import com.sbrf.lt.datapool.kafka.KafkaTopicProduceRequest
import com.sbrf.lt.datapool.kafka.KafkaTopicProduceResult
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageHeader
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadScope
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadMode
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadRequest
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadResult
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageRecord
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupPartitionLag
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupSummary
import com.sbrf.lt.datapool.kafka.KafkaTopicConsumerGroupsSummary
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

internal fun KafkaClusterConsumerGroupsCatalog.toResponse(): KafkaClusterConsumerGroupsCatalogResponse =
    KafkaClusterConsumerGroupsCatalogResponse(
        cluster = cluster.toResponse(),
        status = status.name,
        message = message,
        groups = groups.map { it.toResponse() },
    )

internal fun KafkaClusterBrokersCatalog.toResponse(): KafkaClusterBrokersCatalogResponse =
    KafkaClusterBrokersCatalogResponse(
        cluster = cluster.toResponse(),
        controllerBrokerId = controllerBrokerId,
        brokers = brokers.map { it.toResponse() },
    )

internal fun KafkaTopicOverview.toResponse(): KafkaTopicOverviewResponse =
    KafkaTopicOverviewResponse(
        cluster = cluster.toResponse(),
        topic = topic.toResponse(),
        partitions = partitions.map { it.toResponse() },
        consumerGroups = consumerGroups.toResponse(),
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

private fun KafkaTopicConsumerGroupsSummary.toResponse(): KafkaTopicConsumerGroupsSummaryResponse =
    KafkaTopicConsumerGroupsSummaryResponse(
        status = status.name,
        message = message,
        groups = groups.map { it.toResponse() },
    )

private fun KafkaTopicConsumerGroupSummary.toResponse(): KafkaTopicConsumerGroupSummaryResponse =
    KafkaTopicConsumerGroupSummaryResponse(
        groupId = groupId,
        state = state,
        memberCount = memberCount,
        metadataAvailable = metadataAvailable,
        totalLag = totalLag,
        lagStatus = lagStatus.name,
        note = note,
        partitions = partitions.map { it.toResponse() },
    )

private fun KafkaTopicConsumerGroupPartitionLag.toResponse(): KafkaTopicConsumerGroupPartitionLagResponse =
    KafkaTopicConsumerGroupPartitionLagResponse(
        partition = partition,
        committedOffset = committedOffset,
        latestOffset = latestOffset,
        lag = lag,
    )

private fun KafkaClusterConsumerGroupSummary.toResponse(): KafkaClusterConsumerGroupSummaryResponse =
    KafkaClusterConsumerGroupSummaryResponse(
        groupId = groupId,
        state = state,
        memberCount = memberCount,
        metadataAvailable = metadataAvailable,
        totalLag = totalLag,
        lagStatus = lagStatus.name,
        note = note,
        topics = topics.map { it.toResponse() },
    )

private fun KafkaClusterConsumerGroupTopicSummary.toResponse(): KafkaClusterConsumerGroupTopicSummaryResponse =
    KafkaClusterConsumerGroupTopicSummaryResponse(
        topicName = topicName,
        partitionCount = partitionCount,
        totalLag = totalLag,
        partitions = partitions.map { it.toResponse() },
    )

private fun KafkaBrokerSummary.toResponse(): KafkaBrokerSummaryResponse =
    KafkaBrokerSummaryResponse(
        brokerId = brokerId,
        host = host,
        port = port,
        rack = rack,
        controller = controller,
    )

internal fun KafkaTopicMessageReadRequestPayload.toCoreRequest(): KafkaTopicMessageReadRequest =
    KafkaTopicMessageReadRequest(
        clusterId = clusterId.trim(),
        topicName = topicName.trim(),
        scope = runCatching { KafkaTopicMessageReadScope.valueOf(scope.trim().uppercase()) }
            .getOrElse { throw IllegalArgumentException("Kafka read scope '$scope' не поддерживается.") },
        partition = partition,
        mode = runCatching { KafkaTopicMessageReadMode.valueOf(mode.trim().uppercase()) }
            .getOrElse { throw IllegalArgumentException("Kafka read mode '$mode' не поддерживается.") },
        limit = limit,
        offset = offset,
        timestampMs = timestampMs,
    )

internal fun KafkaTopicMessageReadResult.toResponse(): KafkaTopicMessageReadResponse =
    KafkaTopicMessageReadResponse(
        cluster = cluster.toResponse(),
        topicName = topicName,
        scope = scope.name,
        partition = partition,
        mode = mode.name,
        requestedLimit = requestedLimit,
        effectiveLimit = effectiveLimit,
        requestedOffset = requestedOffset,
        requestedTimestampMs = requestedTimestampMs,
        effectiveStartOffset = effectiveStartOffset,
        note = note,
        records = records.map { it.toResponse() },
    )

private fun KafkaTopicMessageRecord.toResponse(): KafkaTopicMessageRecordResponse =
    KafkaTopicMessageRecordResponse(
        partition = partition,
        offset = offset,
        timestamp = timestamp,
        key = key?.toResponse(),
        value = value?.toResponse(),
        headers = headers.map { it.toResponse() },
    )

private fun KafkaTopicMessageHeader.toResponse(): KafkaTopicMessageHeaderResponse =
    KafkaTopicMessageHeaderResponse(
        name = name,
        value = value?.toResponse(),
    )

private fun KafkaRenderedBytes.toResponse(): KafkaRenderedBytesResponse =
    KafkaRenderedBytesResponse(
        sizeBytes = sizeBytes,
        truncated = truncated,
        text = text,
        jsonPrettyText = jsonPrettyText,
    )

internal fun KafkaTopicProduceRequestPayload.toCoreRequest(): KafkaTopicProduceRequest =
    KafkaTopicProduceRequest(
        clusterId = clusterId.trim(),
        topicName = topicName.trim(),
        partition = partition,
        keyText = keyText,
        payloadText = payloadText,
        headers = headers.map { it.toCoreHeader() },
    )

private fun KafkaTopicProduceHeaderRequestPayload.toCoreHeader(): KafkaTopicProduceHeader =
    KafkaTopicProduceHeader(
        name = name.trim(),
        valueText = valueText,
    )

internal fun KafkaTopicCreateRequestPayload.toCoreRequest(): KafkaTopicCreateRequest =
    KafkaTopicCreateRequest(
        clusterId = clusterId.trim(),
        topicName = topicName.trim(),
        partitionCount = partitionCount,
        replicationFactor = replicationFactor,
        cleanupPolicy = cleanupPolicy?.trim()?.takeIf { it.isNotEmpty() },
        retentionMs = retentionMs,
        retentionBytes = retentionBytes,
    )

internal fun KafkaTopicCreateResult.toResponse(): KafkaTopicCreateResponse =
    KafkaTopicCreateResponse(
        cluster = cluster.toResponse(),
        topicName = topicName,
        partitionCount = partitionCount,
        replicationFactor = replicationFactor,
        cleanupPolicy = cleanupPolicy,
        retentionMs = retentionMs,
        retentionBytes = retentionBytes,
    )

internal fun KafkaTopicProduceResult.toResponse(): KafkaTopicProduceResponse =
    KafkaTopicProduceResponse(
        cluster = cluster.toResponse(),
        topicName = topicName,
        partition = partition,
        offset = offset,
        timestamp = timestamp,
    )
