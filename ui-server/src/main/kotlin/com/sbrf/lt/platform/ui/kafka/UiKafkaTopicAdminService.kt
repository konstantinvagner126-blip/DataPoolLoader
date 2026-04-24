package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaTopicAdminOperations
import com.sbrf.lt.datapool.kafka.KafkaTopicCreateRequest
import com.sbrf.lt.datapool.kafka.KafkaTopicCreateResult
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig

internal class ConfigBackedKafkaTopicAdminService(
    private val kafkaConfig: UiKafkaConfig,
    private val adminFacadeFactory: UiKafkaAdminFacadeFactory = DefaultUiKafkaAdminFacadeFactory(),
) : KafkaTopicAdminOperations {

    override fun createTopic(request: KafkaTopicCreateRequest): KafkaTopicCreateResult {
        val topicName = request.topicName.trim()
        require(topicName.isNotBlank()) {
            "Kafka topic name не должен быть пустым."
        }
        require(request.partitionCount > 0) {
            "Kafka partitions должны быть > 0."
        }
        require(request.replicationFactor > 0) {
            "Kafka replication factor должен быть > 0."
        }
        val cleanupPolicy = request.cleanupPolicy?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it in setOf("delete", "compact", "compact,delete", "delete,compact")) {
                "Kafka cleanup policy '$it' не поддерживается."
            }
        }
        request.retentionMs?.let {
            require(it > 0L) { "Kafka retention.ms должен быть > 0." }
        }
        request.retentionBytes?.let {
            require(it > 0L) { "Kafka retention.bytes должен быть > 0." }
        }

        val cluster = requireCluster(request.clusterId)
        require(!cluster.readOnly) {
            "Kafka cluster '${cluster.id}' помечен как readOnly. Topic create path для него запрещен."
        }
        val configs = buildMap<String, String> {
            cleanupPolicy?.let { put("cleanup.policy", it) }
            request.retentionMs?.let { put("retention.ms", it.toString()) }
            request.retentionBytes?.let { put("retention.bytes", it.toString()) }
        }

        adminFacadeFactory.open(cluster).use { admin ->
            admin.createTopic(
                topicName = topicName,
                partitionCount = request.partitionCount,
                replicationFactor = request.replicationFactor.toShort(),
                configs = configs,
            )
        }

        return KafkaTopicCreateResult(
            cluster = cluster.toCatalogEntry(),
            topicName = topicName,
            partitionCount = request.partitionCount,
            replicationFactor = request.replicationFactor,
            cleanupPolicy = cleanupPolicy,
            retentionMs = request.retentionMs,
            retentionBytes = request.retentionBytes,
        )
    }

    private fun requireCluster(clusterId: String): UiKafkaClusterConfig =
        kafkaConfig.clusters.firstOrNull { it.id == clusterId }
            ?: throw KafkaClusterNotFoundException(clusterId)
}
