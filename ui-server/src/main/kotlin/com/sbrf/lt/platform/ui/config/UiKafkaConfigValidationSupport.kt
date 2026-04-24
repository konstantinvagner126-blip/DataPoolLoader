package com.sbrf.lt.platform.ui.config

internal class UiKafkaConfigValidationSupport {

    fun validateForLoad(kafkaConfig: UiKafkaConfig) {
        require(kafkaConfig.maxRecordsPerRead > 0) {
            "ui.kafka.maxRecordsPerRead должен быть больше 0."
        }
        require(kafkaConfig.pollTimeoutMs > 0) {
            "ui.kafka.pollTimeoutMs должен быть больше 0."
        }
        require(kafkaConfig.adminTimeoutMs > 0) {
            "ui.kafka.adminTimeoutMs должен быть больше 0."
        }
        require(kafkaConfig.maxPayloadBytes > 0) {
            "ui.kafka.maxPayloadBytes должен быть больше 0."
        }

        val duplicateIds = kafkaConfig.clusters
            .map { it.id.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "ui.kafka.clusters содержит дублирующиеся id: ${duplicateIds.joinToString(", ")}."
        }

        kafkaConfig.clusters.forEach { cluster ->
            require(cluster.id.isNotBlank()) {
                "Kafka cluster id не должен быть пустым."
            }
            require(cluster.name.isNotBlank()) {
                "Kafka cluster '${cluster.id}' должен иметь непустое name."
            }
            require(cluster.bootstrapServers() != null) {
                "Kafka cluster '${cluster.id}' должен содержать bootstrap.servers."
            }
            require(cluster.properties.keys.none { it.isBlank() }) {
                "Kafka cluster '${cluster.id}' содержит пустой ключ в properties."
            }
        }
    }

    fun validateForRuntime(kafkaConfig: UiKafkaConfig) {
        kafkaConfig.clusters.forEach(::validateClusterRuntime)
    }

    private fun validateClusterRuntime(cluster: UiKafkaClusterConfig) {
        val protocol = cluster.securityProtocolOrDefault().uppercase()
        require(protocol in supportedSecurityProtocols) {
            "Kafka cluster '${cluster.id}' использует неподдерживаемый security.protocol '$protocol'. Поддерживаются только PLAINTEXT и SSL."
        }

        val sslKeysPresent = cluster.properties.anyNonBlankValueForPrefix("ssl.")
        if (protocol == "PLAINTEXT") {
            require(!sslKeysPresent) {
                "Kafka cluster '${cluster.id}' использует PLAINTEXT и не должен содержать ssl.* properties."
            }
            return
        }

        validateTrustMaterial(cluster)
        validateIdentityMaterial(cluster)
    }

    private fun validateTrustMaterial(cluster: UiKafkaClusterConfig) {
        val props = cluster.properties
        val type = props.normalizedValue("ssl.truststore.type")?.uppercase()
        val location = props.normalizedValue("ssl.truststore.location")
        val certificates = props.normalizedValue("ssl.truststore.certificates")

        if (type == null) {
            require(certificates == null) {
                "Kafka cluster '${cluster.id}' использует ssl.truststore.certificates без ssl.truststore.type=PEM."
            }
            return
        }

        require(type in supportedStoreTypes) {
            "Kafka cluster '${cluster.id}' использует неподдерживаемый ssl.truststore.type '$type'. Поддерживаются только JKS и PEM."
        }

        when (type) {
            "JKS" -> {
                require(location != null) {
                    "Kafka cluster '${cluster.id}' с ssl.truststore.type=JKS должен задавать ssl.truststore.location."
                }
                require(certificates == null) {
                    "Kafka cluster '${cluster.id}' с ssl.truststore.type=JKS не должен задавать ssl.truststore.certificates."
                }
            }

            "PEM" -> {
                require(!(location != null && certificates != null)) {
                    "Kafka cluster '${cluster.id}' с ssl.truststore.type=PEM не должен одновременно задавать ssl.truststore.location и ssl.truststore.certificates."
                }
                require(location != null || certificates != null) {
                    "Kafka cluster '${cluster.id}' с ssl.truststore.type=PEM должен задавать ssl.truststore.certificates или ssl.truststore.location."
                }
            }
        }
    }

    private fun validateIdentityMaterial(cluster: UiKafkaClusterConfig) {
        val props = cluster.properties
        val type = props.normalizedValue("ssl.keystore.type")?.uppercase()
        val location = props.normalizedValue("ssl.keystore.location")
        val certificateChain = props.normalizedValue("ssl.keystore.certificate.chain")
        val key = props.normalizedValue("ssl.keystore.key")

        if (type == null) {
            require(certificateChain == null && key == null) {
                "Kafka cluster '${cluster.id}' использует PEM identity fields без ssl.keystore.type=PEM."
            }
            return
        }

        require(type in supportedStoreTypes) {
            "Kafka cluster '${cluster.id}' использует неподдерживаемый ssl.keystore.type '$type'. Поддерживаются только JKS и PEM."
        }

        when (type) {
            "JKS" -> {
                require(location != null) {
                    "Kafka cluster '${cluster.id}' с ssl.keystore.type=JKS должен задавать ssl.keystore.location."
                }
                require(certificateChain == null && key == null) {
                    "Kafka cluster '${cluster.id}' с ssl.keystore.type=JKS не должен задавать PEM identity fields."
                }
            }

            "PEM" -> {
                val inlineIdentityProvided = certificateChain != null || key != null
                require(!(location != null && inlineIdentityProvided)) {
                    "Kafka cluster '${cluster.id}' с ssl.keystore.type=PEM не должен одновременно задавать ssl.keystore.location и PEM inline identity fields."
                }
                if (inlineIdentityProvided) {
                    require(certificateChain != null && key != null) {
                        "Kafka cluster '${cluster.id}' с ssl.keystore.type=PEM должен задавать и ssl.keystore.certificate.chain, и ssl.keystore.key."
                    }
                } else {
                    require(location != null) {
                        "Kafka cluster '${cluster.id}' с ssl.keystore.type=PEM должен задавать ssl.keystore.location или PEM inline identity fields."
                    }
                }
            }
        }
    }

    private fun Map<String, String>.normalizedValue(key: String): String? =
        get(key)?.trim()?.takeIf { it.isNotEmpty() }

    private fun Map<String, String>.anyNonBlankValueForPrefix(prefix: String): Boolean =
        entries.any { (key, value) -> key.startsWith(prefix) && value.isNotBlank() }

    private companion object {
        val supportedSecurityProtocols = setOf("PLAINTEXT", "SSL")
        val supportedStoreTypes = setOf("JKS", "PEM")
    }
}
