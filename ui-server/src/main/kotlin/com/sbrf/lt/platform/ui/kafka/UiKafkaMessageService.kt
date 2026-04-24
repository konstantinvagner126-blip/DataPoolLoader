package com.sbrf.lt.platform.ui.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaMessageOperations
import com.sbrf.lt.datapool.kafka.KafkaRenderedBytes
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageHeader
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadScope
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadMode
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadRequest
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageReadResult
import com.sbrf.lt.datapool.kafka.KafkaTopicMessageRecord
import com.sbrf.lt.datapool.kafka.KafkaTopicNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaTopicPartitionNotFoundException
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import java.nio.charset.StandardCharsets
import java.time.Duration

internal class ConfigBackedKafkaMessageService(
    private val kafkaConfig: UiKafkaConfig,
    private val consumerFacadeFactory: UiKafkaMessageConsumerFacadeFactory = DefaultUiKafkaMessageConsumerFacadeFactory(),
    private val objectMapper: ObjectMapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT),
) : KafkaMessageOperations {

    override fun readMessages(request: KafkaTopicMessageReadRequest): KafkaTopicMessageReadResult {
        require(request.scope == KafkaTopicMessageReadScope.ALL_PARTITIONS || request.partition != null) {
            "Для Kafka read scope SELECTED_PARTITION параметр partition обязателен."
        }
        request.partition?.let {
            require(it >= 0) {
                "Kafka partition должна быть >= 0."
            }
        }
        request.offset?.let {
            require(it >= 0L) { "Kafka offset должен быть >= 0." }
        }
        request.timestampMs?.let {
            require(it >= 0L) { "Kafka timestampMs должен быть >= 0." }
        }

        val cluster = requireCluster(request.clusterId)
        consumerFacadeFactory.open(cluster).use { consumer ->
            val partitions = consumer.loadPartitions(request.topicName)
            if (partitions.isEmpty()) {
                throw KafkaTopicNotFoundException(request.clusterId, request.topicName)
            }
            val availablePartitions = partitions.map { it.partition() }.sorted()
            if (request.scope == KafkaTopicMessageReadScope.SELECTED_PARTITION &&
                availablePartitions.none { it == request.partition }
            ) {
                throw KafkaTopicPartitionNotFoundException(
                    request.clusterId,
                    request.topicName,
                    request.partition ?: -1,
                )
            }
            val effectiveLimit = normalizeLimit(request.limit)
            return when (request.scope) {
                KafkaTopicMessageReadScope.SELECTED_PARTITION -> readSelectedPartition(
                    cluster = cluster,
                    consumer = consumer,
                    request = request,
                    effectiveLimit = effectiveLimit,
                )
                KafkaTopicMessageReadScope.ALL_PARTITIONS -> readAllPartitions(
                    cluster = cluster,
                    consumer = consumer,
                    request = request,
                    partitions = availablePartitions,
                    effectiveLimit = effectiveLimit,
                )
            }
        }
    }

    private fun renderBytes(bytes: ByteArray): KafkaRenderedBytes {
        val truncated = bytes.size > kafkaConfig.maxPayloadBytes
        val visibleBytes = if (truncated) {
            bytes.copyOf(kafkaConfig.maxPayloadBytes)
        } else {
            bytes
        }
        val text = visibleBytes.toString(StandardCharsets.UTF_8)
        return KafkaRenderedBytes(
            sizeBytes = bytes.size,
            truncated = truncated,
            text = text,
            jsonPrettyText = prettyJsonOrNull(text),
        )
    }

    private fun prettyJsonOrNull(text: String): String? {
        val candidate = text.trim()
        if (!(candidate.startsWith("{") || candidate.startsWith("["))) {
            return null
        }
        return runCatching {
            objectMapper.writeValueAsString(objectMapper.readTree(candidate))
        }.getOrNull()
    }

    private fun normalizeLimit(requestedLimit: Int?): Int {
        val fallback = minOf(50, kafkaConfig.maxRecordsPerRead)
        val resolved = requestedLimit ?: fallback
        require(resolved > 0) {
            "Kafka read limit должен быть > 0."
        }
        return resolved.coerceAtMost(kafkaConfig.maxRecordsPerRead)
    }

    private fun requireCluster(clusterId: String): UiKafkaClusterConfig =
        kafkaConfig.clusters.firstOrNull { it.id == clusterId }
            ?: throw KafkaClusterNotFoundException(clusterId)

    private fun readSelectedPartition(
        cluster: UiKafkaClusterConfig,
        consumer: UiKafkaMessageConsumerFacade,
        request: KafkaTopicMessageReadRequest,
        effectiveLimit: Int,
    ): KafkaTopicMessageReadResult {
        val partition = request.partition ?: error("partition is required for SELECTED_PARTITION")
        val topicPartition = TopicPartition(request.topicName, partition)
        val earliestOffset = consumer.beginningOffset(topicPartition) ?: 0L
        val latestOffset = consumer.endOffset(topicPartition) ?: earliestOffset
        val cursor = resolveReadCursor(
            request = request,
            earliestOffset = earliestOffset,
            latestOffset = latestOffset,
            timestampOffset = if (request.mode == KafkaTopicMessageReadMode.TIMESTAMP) {
                consumer.offsetForTimestamp(topicPartition, request.timestampMs ?: 0L)
            } else {
                null
            },
        )
        val records = if (cursor.startOffset >= latestOffset) {
            emptyList()
        } else {
            consumer.readRecords(
                topicPartition = topicPartition,
                startOffset = cursor.startOffset,
                limit = effectiveLimit,
                pollTimeout = Duration.ofMillis(kafkaConfig.pollTimeoutMs.toLong()),
            )
        }
        return KafkaTopicMessageReadResult(
            cluster = cluster.toCatalogEntry(),
            topicName = request.topicName,
            scope = request.scope,
            partition = partition,
            mode = request.mode,
            requestedLimit = request.limit ?: effectiveLimit,
            effectiveLimit = effectiveLimit,
            requestedOffset = request.offset,
            requestedTimestampMs = request.timestampMs,
            effectiveStartOffset = cursor.startOffset,
            note = cursor.note,
            records = records.map { record -> record.toViewRecord(partition) },
        )
    }

    private fun readAllPartitions(
        cluster: UiKafkaClusterConfig,
        consumer: UiKafkaMessageConsumerFacade,
        request: KafkaTopicMessageReadRequest,
        partitions: List<Int>,
        effectiveLimit: Int,
    ): KafkaTopicMessageReadResult {
        val topicPartitions = partitions.map { TopicPartition(request.topicName, it) }
        val partitionReads = topicPartitions.map { topicPartition ->
            val earliestOffset = consumer.beginningOffset(topicPartition) ?: 0L
            val latestOffset = consumer.endOffset(topicPartition) ?: earliestOffset
            val cursor = resolveReadCursor(
                request = request,
                earliestOffset = earliestOffset,
                latestOffset = latestOffset,
                timestampOffset = if (request.mode == KafkaTopicMessageReadMode.TIMESTAMP) {
                    consumer.offsetForTimestamp(topicPartition, request.timestampMs ?: 0L)
                } else {
                    null
                },
            )
            val records = if (cursor.startOffset >= latestOffset) {
                emptyList()
            } else {
                consumer.readRecords(
                    topicPartition = topicPartition,
                    startOffset = cursor.startOffset,
                    limit = effectiveLimit,
                    pollTimeout = Duration.ofMillis(kafkaConfig.pollTimeoutMs.toLong()),
                )
            }
            KafkaPartitionReadResult(
                partition = topicPartition.partition(),
                startOffset = cursor.startOffset,
                latestOffset = latestOffset,
                note = cursor.note,
                records = records.map { record -> record.toViewRecord(topicPartition.partition()) },
            )
        }
        val orderedRecords = orderAllPartitionsRecords(
            mode = request.mode,
            records = partitionReads.flatMap { it.records },
        ).take(effectiveLimit)
        return KafkaTopicMessageReadResult(
            cluster = cluster.toCatalogEntry(),
            topicName = request.topicName,
            scope = request.scope,
            partition = null,
            mode = request.mode,
            requestedLimit = request.limit ?: effectiveLimit,
            effectiveLimit = effectiveLimit,
            requestedOffset = request.offset,
            requestedTimestampMs = request.timestampMs,
            effectiveStartOffset = null,
            note = buildAllPartitionsNote(request.mode, partitionReads, orderedRecords.isEmpty()),
            records = orderedRecords,
        )
    }

    private fun resolveReadCursor(
        request: KafkaTopicMessageReadRequest,
        earliestOffset: Long,
        latestOffset: Long,
        timestampOffset: Long?,
    ): KafkaReadCursor =
        when (request.mode) {
            KafkaTopicMessageReadMode.LATEST -> {
                val startOffset = (latestOffset - normalizeLimit(request.limit).toLong()).coerceAtLeast(earliestOffset)
                KafkaReadCursor(
                    startOffset = startOffset,
                    note = if (latestOffset <= earliestOffset) {
                        "В выбранной partition пока нет сообщений."
                    } else {
                        null
                    },
                )
            }

            KafkaTopicMessageReadMode.OFFSET -> {
                val requestedOffset = request.offset
                    ?: throw IllegalArgumentException("Для Kafka read mode OFFSET параметр offset обязателен.")
                val startOffset = requestedOffset.coerceAtLeast(earliestOffset)
                KafkaReadCursor(
                    startOffset = startOffset,
                    note = if (requestedOffset < earliestOffset) {
                        "Запрошенный offset уже недоступен. Чтение начато с earliest offset $earliestOffset."
                    } else {
                        null
                    },
                )
            }

            KafkaTopicMessageReadMode.TIMESTAMP -> {
                val requestedTimestamp = request.timestampMs
                    ?: throw IllegalArgumentException("Для Kafka read mode TIMESTAMP параметр timestampMs обязателен.")
                val startOffset = timestampOffset ?: latestOffset
                KafkaReadCursor(
                    startOffset = startOffset,
                    note = if (timestampOffset == null) {
                        "Для указанного timestamp offsets не найдены. Чтение начато с latest offset."
                    } else {
                        null
                    },
                )
            }
        }

    private fun orderAllPartitionsRecords(
        mode: KafkaTopicMessageReadMode,
        records: List<KafkaTopicMessageRecord>,
    ): List<KafkaTopicMessageRecord> =
        when (mode) {
            KafkaTopicMessageReadMode.LATEST -> records.sortedWith(
                compareByDescending<KafkaTopicMessageRecord> { it.timestamp ?: Long.MIN_VALUE }
                    .thenByDescending { it.offset }
                    .thenByDescending { it.partition },
            )
            KafkaTopicMessageReadMode.OFFSET -> records.sortedWith(
                compareBy<KafkaTopicMessageRecord> { it.partition }
                    .thenBy { it.offset }
                    .thenBy { it.timestamp ?: Long.MIN_VALUE },
            )
            KafkaTopicMessageReadMode.TIMESTAMP -> records.sortedWith(
                compareBy<KafkaTopicMessageRecord> { it.partition }
                    .thenBy { it.offset }
                    .thenBy { it.timestamp ?: Long.MIN_VALUE },
            )
        }

    private fun buildAllPartitionsNote(
        mode: KafkaTopicMessageReadMode,
        partitionReads: List<KafkaPartitionReadResult>,
        noRecords: Boolean,
    ): String? {
        val hadCursorFallback = partitionReads.any { !it.note.isNullOrBlank() }
        return when {
            noRecords -> "Для выбранного topic scope сообщения не найдены."
            mode == KafkaTopicMessageReadMode.LATEST && hadCursorFallback ->
                "Результат собран по всем partition и отсортирован по timestamp. Для части partition был применен fallback cursor."
            mode == KafkaTopicMessageReadMode.LATEST ->
                "Результат собран по всем partition и отсортирован по timestamp."
            hadCursorFallback ->
                "Результат собран по всем partition. Для части partition был применен fallback cursor."
            else ->
                "Результат собран по всем partition. Записи сгруппированы по partition и offset."
        }
    }

    private fun ConsumerRecord<ByteArray, ByteArray>.toViewRecord(partition: Int): KafkaTopicMessageRecord =
        KafkaTopicMessageRecord(
            partition = partition,
            offset = offset(),
            timestamp = timestamp().takeIf { it >= 0L },
            key = key()?.let(::renderBytes),
            value = value()?.let(::renderBytes),
            headers = headers().map { header ->
                KafkaTopicMessageHeader(
                    name = header.key(),
                    value = header.value()?.let(::renderBytes),
                )
            },
        )
}

private data class KafkaReadCursor(
    val startOffset: Long,
    val note: String? = null,
)

private data class KafkaPartitionReadResult(
    val partition: Int,
    val startOffset: Long,
    val latestOffset: Long,
    val note: String? = null,
    val records: List<KafkaTopicMessageRecord> = emptyList(),
)
