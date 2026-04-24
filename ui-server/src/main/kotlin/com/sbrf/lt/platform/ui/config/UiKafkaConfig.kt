package com.sbrf.lt.platform.ui.config

data class UiKafkaConfig(
    val maxRecordsPerRead: Int = DEFAULT_MAX_RECORDS_PER_READ,
    val pollTimeoutMs: Int = DEFAULT_POLL_TIMEOUT_MS,
    val adminTimeoutMs: Int = DEFAULT_ADMIN_TIMEOUT_MS,
    val maxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
    val clusters: List<UiKafkaClusterConfig> = emptyList(),
) {
    companion object {
        const val DEFAULT_MAX_RECORDS_PER_READ = 100
        const val DEFAULT_POLL_TIMEOUT_MS = 3_000
        const val DEFAULT_ADMIN_TIMEOUT_MS = 5_000
        const val DEFAULT_MAX_PAYLOAD_BYTES = 1_048_576
    }
}

data class UiKafkaClusterConfig(
    val id: String = "",
    val name: String = "",
    val readOnly: Boolean = true,
    val properties: Map<String, String> = linkedMapOf(),
)

internal fun UiKafkaClusterConfig.bootstrapServers(): String? =
    properties["bootstrap.servers"]?.trim()?.takeIf { it.isNotEmpty() }

internal fun UiKafkaClusterConfig.securityProtocolOrDefault(): String =
    properties["security.protocol"]?.trim()?.takeIf { it.isNotEmpty() } ?: "PLAINTEXT"
