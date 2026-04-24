package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaProduceOperations
import com.sbrf.lt.datapool.kafka.KafkaTopicProduceRequest
import com.sbrf.lt.datapool.kafka.KafkaTopicProduceResult
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.UiKafkaConfig
import org.apache.kafka.common.header.internals.RecordHeader
import java.nio.charset.StandardCharsets

internal class ConfigBackedKafkaProduceService(
    private val kafkaConfig: UiKafkaConfig,
    private val producerFacadeFactory: UiKafkaProducerFacadeFactory = DefaultUiKafkaProducerFacadeFactory(),
) : KafkaProduceOperations {

    override fun produce(request: KafkaTopicProduceRequest): KafkaTopicProduceResult {
        require(request.topicName.isNotBlank()) {
            "Kafka topic name не должен быть пустым."
        }
        request.partition?.let {
            require(it >= 0) { "Kafka partition должна быть >= 0." }
        }
        require(request.payloadText.isNotBlank()) {
            "Kafka payload не должен быть пустым."
        }
        val cluster = requireCluster(request.clusterId)
        val payloadBytes = request.payloadText.toByteArray(StandardCharsets.UTF_8)
        require(payloadBytes.size <= kafkaConfig.maxPayloadBytes) {
            "Kafka payload превышает лимит ${kafkaConfig.maxPayloadBytes} B."
        }
        request.headers.forEach { header ->
            require(header.name.isNotBlank()) {
                "Kafka header name не должен быть пустым."
            }
        }

        producerFacadeFactory.open(cluster).use { producer ->
            val metadata = producer.send(
                topicName = request.topicName,
                partition = request.partition,
                key = request.keyText?.toByteArray(StandardCharsets.UTF_8),
                value = payloadBytes,
                headers = request.headers.map { header ->
                    RecordHeader(
                        header.name,
                        header.valueText?.toByteArray(StandardCharsets.UTF_8),
                    )
                },
            )
            return KafkaTopicProduceResult(
                cluster = cluster.toCatalogEntry(),
                topicName = request.topicName,
                partition = metadata.partition(),
                offset = metadata.offset(),
                timestamp = metadata.timestamp().takeIf { it >= 0L },
            )
        }
    }

    private fun requireCluster(clusterId: String): UiKafkaClusterConfig =
        kafkaConfig.clusters.firstOrNull { it.id == clusterId }
            ?: throw KafkaClusterNotFoundException(clusterId)
}
