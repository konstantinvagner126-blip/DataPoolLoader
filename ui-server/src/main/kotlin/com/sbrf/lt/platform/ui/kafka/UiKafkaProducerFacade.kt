package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.header.Header

internal interface UiKafkaProducerFacadeFactory {
    fun open(cluster: UiKafkaClusterConfig): UiKafkaProducerFacade
}

internal interface UiKafkaProducerFacade : AutoCloseable {
    fun send(
        topicName: String,
        partition: Int? = null,
        key: ByteArray? = null,
        value: ByteArray,
        headers: List<Header> = emptyList(),
    ): RecordMetadata
}

internal class DefaultUiKafkaProducerFacadeFactory(
    private val clientFactory: UiKafkaClientFactory = DefaultUiKafkaClientFactory(),
) : UiKafkaProducerFacadeFactory {
    override fun open(cluster: UiKafkaClusterConfig): UiKafkaProducerFacade =
        KafkaProducerUiKafkaProducerFacade(
            clientFactory.createProducer(cluster, "single-produce"),
        )
}

private class KafkaProducerUiKafkaProducerFacade(
    private val producer: org.apache.kafka.clients.producer.KafkaProducer<ByteArray, ByteArray>,
) : UiKafkaProducerFacade {
    override fun send(
        topicName: String,
        partition: Int?,
        key: ByteArray?,
        value: ByteArray,
        headers: List<Header>,
    ): RecordMetadata =
        producer.send(
            org.apache.kafka.clients.producer.ProducerRecord(
                topicName,
                partition,
                key,
                value,
                headers,
            ),
        ).get()

    override fun close() {
        producer.close()
    }
}
