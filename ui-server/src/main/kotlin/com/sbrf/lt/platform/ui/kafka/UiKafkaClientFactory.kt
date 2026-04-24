package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer

interface UiKafkaClientFactory {
    fun createAdminClient(cluster: UiKafkaClusterConfig): AdminClient
    fun createConsumer(cluster: UiKafkaClusterConfig, clientIdSuffix: String? = null): KafkaConsumer<ByteArray, ByteArray>
    fun createProducer(cluster: UiKafkaClusterConfig, clientIdSuffix: String? = null): KafkaProducer<ByteArray, ByteArray>
}

open class DefaultUiKafkaClientFactory(
    private val propertiesFactory: UiKafkaClientPropertiesFactory = UiKafkaClientPropertiesFactory(),
) : UiKafkaClientFactory {

    override fun createAdminClient(cluster: UiKafkaClusterConfig): AdminClient =
        AdminClient.create(propertiesFactory.admin(cluster))

    override fun createConsumer(
        cluster: UiKafkaClusterConfig,
        clientIdSuffix: String?,
    ): KafkaConsumer<ByteArray, ByteArray> =
        KafkaConsumer(propertiesFactory.consumer(cluster, clientIdSuffix))

    override fun createProducer(
        cluster: UiKafkaClusterConfig,
        clientIdSuffix: String?,
    ): KafkaProducer<ByteArray, ByteArray> {
        require(!cluster.readOnly) {
            "Kafka cluster '${cluster.id}' помечен как readOnly. Produce path для него запрещен."
        }
        return KafkaProducer(propertiesFactory.producer(cluster, clientIdSuffix))
    }
}
