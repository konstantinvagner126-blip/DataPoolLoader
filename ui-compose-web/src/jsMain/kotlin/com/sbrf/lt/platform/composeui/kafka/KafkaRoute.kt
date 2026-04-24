package com.sbrf.lt.platform.composeui.kafka

data class KafkaRouteState(
    val clusterId: String? = null,
    val topicName: String? = null,
)

fun parseKafkaRoute(params: Map<String, String>): KafkaRouteState =
    KafkaRouteState(
        clusterId = params["clusterId"]?.trim()?.ifBlank { null },
        topicName = params["topic"]?.trim()?.ifBlank { null },
    )

fun buildKafkaPageHref(
    clusterId: String? = null,
    topicName: String? = null,
): String {
    val queryParams = buildList {
        clusterId?.takeIf { it.isNotBlank() }?.let { add("clusterId=${urlEncode(it)}") }
        topicName?.takeIf { it.isNotBlank() }?.let { add("topic=${urlEncode(it)}") }
    }
    return if (queryParams.isEmpty()) {
        "/kafka"
    } else {
        "/kafka?${queryParams.joinToString("&")}"
    }
}

internal fun urlEncode(value: String): String = js("encodeURIComponent(value)") as String
