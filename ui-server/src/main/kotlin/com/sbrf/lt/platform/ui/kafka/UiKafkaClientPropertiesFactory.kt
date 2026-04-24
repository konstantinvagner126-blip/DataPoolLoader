package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.util.Properties

open class UiKafkaClientPropertiesFactory {

    open fun admin(cluster: UiKafkaClusterConfig): Properties =
        baseProperties(cluster)

    open fun consumer(
        cluster: UiKafkaClusterConfig,
        clientIdSuffix: String? = null,
    ): Properties = baseProperties(cluster).apply {
        setDefault(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
        setDefault(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
        setDefault(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        setClientIdSuffix(clientIdSuffix)
    }

    open fun producer(
        cluster: UiKafkaClusterConfig,
        clientIdSuffix: String? = null,
    ): Properties = baseProperties(cluster).apply {
        setDefault(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
        setDefault(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
        setClientIdSuffix(clientIdSuffix)
    }

    private fun baseProperties(cluster: UiKafkaClusterConfig): Properties =
        Properties().apply {
            cluster.properties.forEach { (key, value) ->
                put(key, value)
            }
        }

    private fun Properties.setDefault(key: String, value: String) {
        if (getProperty(key).isNullOrBlank()) {
            setProperty(key, value)
        }
    }

    private fun Properties.setClientIdSuffix(suffix: String?) {
        val resolvedSuffix = suffix?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val current = getProperty(CommonClientConfigs.CLIENT_ID_CONFIG)?.trim().orEmpty()
        val updated = if (current.isEmpty()) {
            resolvedSuffix
        } else {
            "$current-$resolvedSuffix"
        }
        setProperty(CommonClientConfigs.CLIENT_ID_CONFIG, updated)
    }
}
