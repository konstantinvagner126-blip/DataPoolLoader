package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaTopicNotFoundException
import com.sbrf.lt.platform.ui.model.toResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.registerKafkaRoutes(context: UiServerContext) {
    get("/api/kafka/info") {
        call.respond(context.kafkaMetadataService.info().toResponse())
    }

    get("/api/kafka/topics") {
        val clusterId = call.request.queryParameters["clusterId"]?.trim().orEmpty()
        if (clusterId.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Параметр clusterId обязателен для /api/kafka/topics.")
            return@get
        }
        try {
            call.respond(
                context.kafkaMetadataService.listTopics(
                    clusterId = clusterId,
                    query = call.request.queryParameters["query"].orEmpty(),
                ).toResponse(),
            )
        } catch (e: KafkaClusterNotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message.orEmpty())
        }
    }

    get("/api/kafka/topic-overview") {
        val clusterId = call.request.queryParameters["clusterId"]?.trim().orEmpty()
        val topicName = call.request.queryParameters["topic"]?.trim().orEmpty()
        if (clusterId.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Параметр clusterId обязателен для /api/kafka/topic-overview.")
            return@get
        }
        if (topicName.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Параметр topic обязателен для /api/kafka/topic-overview.")
            return@get
        }
        try {
            call.respond(
                context.kafkaMetadataService.loadTopicOverview(
                    clusterId = clusterId,
                    topicName = topicName,
                ).toResponse(),
            )
        } catch (e: KafkaClusterNotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message.orEmpty())
        } catch (e: KafkaTopicNotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message.orEmpty())
        }
    }
}
