package com.sbrf.lt.platform.composeui.kafka

data class KafkaRouteState(
    val clusterId: String? = null,
    val clusterSection: String = "topics",
    val topicName: String? = null,
    val topicQuery: String = "",
    val activePane: String = "overview",
    val messageReadScope: String = "SELECTED_PARTITION",
    val messageReadMode: String = "LATEST",
    val selectedMessagePartition: Int? = null,
)

fun parseKafkaRoute(params: Map<String, String>): KafkaRouteState =
    KafkaRouteState(
        clusterId = params["clusterId"]?.trim()?.ifBlank { null },
        clusterSection = params["section"]?.trim()?.ifBlank { null } ?: "topics",
        topicName = params["topic"]?.trim()?.ifBlank { null },
        topicQuery = params["query"]?.trim().orEmpty(),
        activePane = params["pane"]?.trim()?.ifBlank { null } ?: "overview",
        messageReadScope = params["scope"]?.trim()?.ifBlank { null } ?: "SELECTED_PARTITION",
        messageReadMode = params["mode"]?.trim()?.ifBlank { null } ?: "LATEST",
        selectedMessagePartition = params["partition"]?.trim()?.toIntOrNull(),
    )

fun buildKafkaPageHref(
    clusterId: String? = null,
    clusterSection: String = "topics",
    topicName: String? = null,
    topicQuery: String = "",
    activePane: String = "overview",
    messageReadScope: String = "SELECTED_PARTITION",
    messageReadMode: String = "LATEST",
    selectedMessagePartition: Int? = null,
): String {
    val queryParams = buildList {
        clusterId?.takeIf { it.isNotBlank() }?.let { add("clusterId=${urlEncode(it)}") }
        clusterSection.takeIf { it.isNotBlank() && it != "topics" }?.let { add("section=${urlEncode(it)}") }
        topicName?.takeIf { it.isNotBlank() }?.let { add("topic=${urlEncode(it)}") }
        topicQuery.takeIf { it.isNotBlank() }?.let { add("query=${urlEncode(it)}") }
        activePane.takeIf { it.isNotBlank() && it != "overview" }?.let { add("pane=${urlEncode(it)}") }
        messageReadScope.takeIf { it.isNotBlank() && it != "SELECTED_PARTITION" }?.let { add("scope=${urlEncode(it)}") }
        messageReadMode.takeIf { it.isNotBlank() && it != "LATEST" }?.let { add("mode=${urlEncode(it)}") }
        selectedMessagePartition?.let { add("partition=$it") }
    }
    return if (queryParams.isEmpty()) {
        "/kafka"
    } else {
        "/kafka?${queryParams.joinToString("&")}"
    }
}

internal fun urlEncode(value: String): String = js("encodeURIComponent(value)") as String
