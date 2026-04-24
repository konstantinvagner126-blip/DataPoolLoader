package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UiKafkaClientPropertiesFactoryTest {

    private val propertiesFactory = UiKafkaClientPropertiesFactory()

    @Test
    fun `builds consumer properties with byte array defaults`() {
        val properties = propertiesFactory.consumer(
            UiKafkaClusterConfig(
                id = "local",
                name = "Local",
                properties = mapOf(
                    "bootstrap.servers" to "localhost:19092",
                    "client.id" to "datapool-loader",
                    "security.protocol" to "PLAINTEXT",
                ),
            ),
            clientIdSuffix = "messages",
        )

        assertEquals("localhost:19092", properties.getProperty("bootstrap.servers"))
        assertEquals("datapool-loader-messages", properties.getProperty(CommonClientConfigs.CLIENT_ID_CONFIG))
        assertEquals("false", properties.getProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))
        assertEquals("org.apache.kafka.common.serialization.ByteArrayDeserializer", properties.getProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG))
        assertEquals("org.apache.kafka.common.serialization.ByteArrayDeserializer", properties.getProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
    }

    @Test
    fun `builds producer properties with byte array defaults`() {
        val properties = propertiesFactory.producer(
            UiKafkaClusterConfig(
                id = "local",
                name = "Local",
                properties = mapOf(
                    "bootstrap.servers" to "localhost:19092",
                    "security.protocol" to "PLAINTEXT",
                ),
            ),
            clientIdSuffix = "produce",
        )

        assertEquals("produce", properties.getProperty(CommonClientConfigs.CLIENT_ID_CONFIG))
        assertEquals("org.apache.kafka.common.serialization.ByteArraySerializer", properties.getProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
        assertEquals("org.apache.kafka.common.serialization.ByteArraySerializer", properties.getProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
    }

    @Test
    fun `rejects producer creation for read only cluster`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DefaultUiKafkaClientFactory(propertiesFactory).createProducer(
                UiKafkaClusterConfig(
                    id = "ro",
                    name = "Read only",
                    readOnly = true,
                    properties = mapOf(
                        "bootstrap.servers" to "localhost:19092",
                        "security.protocol" to "PLAINTEXT",
                    ),
                ),
                clientIdSuffix = "produce",
            )
        }

        kotlin.test.assertTrue(error.message!!.contains("readOnly"))
    }
}
