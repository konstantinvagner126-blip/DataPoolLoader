package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.kafka.KafkaClusterNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaTopicPartitionNotFoundException
import com.sbrf.lt.datapool.kafka.KafkaTopicNotFoundException
import com.sbrf.lt.platform.ui.model.KafkaTopicMessageReadRequestPayload
import com.sbrf.lt.platform.ui.model.KafkaSettingsConnectionTestRequestPayload
import com.sbrf.lt.platform.ui.model.KafkaSettingsUpdateRequestPayload
import com.sbrf.lt.platform.ui.model.KafkaTopicProduceRequestPayload
import com.sbrf.lt.platform.ui.model.toCoreRequest
import com.sbrf.lt.platform.ui.model.toResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.registerKafkaRoutes(context: UiServerContext) {
    get("/api/kafka/info") {
        call.respond(context.kafkaMetadataService.info().toResponse())
    }

    get("/api/kafka/settings") {
        call.respond(
            context.kafkaSettingsService.loadSettings(
                context.currentUiConfig(),
            ),
        )
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

    post("/api/kafka/messages/read") {
        val payload = runCatching { call.receive<KafkaTopicMessageReadRequestPayload>() }.getOrElse { error ->
            call.respond(HttpStatusCode.BadRequest, error.message.orEmpty())
            return@post
        }
        try {
            call.respond(
                context.kafkaMessageService.readMessages(
                    payload.toCoreRequest(),
                ).toResponse(),
            )
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, e.message.orEmpty())
        } catch (e: KafkaClusterNotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message.orEmpty())
        } catch (e: KafkaTopicNotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message.orEmpty())
        } catch (e: KafkaTopicPartitionNotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message.orEmpty())
        }
    }

    post("/api/kafka/messages/produce") {
        val payload = runCatching { call.receive<KafkaTopicProduceRequestPayload>() }.getOrElse { error ->
            call.respond(HttpStatusCode.BadRequest, error.message.orEmpty())
            return@post
        }
        try {
            call.respond(
                context.kafkaProduceService.produce(
                    payload.toCoreRequest(),
                ).toResponse(),
            )
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, e.message.orEmpty())
        } catch (e: KafkaClusterNotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message.orEmpty())
        } catch (e: KafkaTopicNotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message.orEmpty())
        } catch (e: KafkaTopicPartitionNotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message.orEmpty())
        }
    }

    post("/api/kafka/settings") {
        val payload = runCatching { call.receive<KafkaSettingsUpdateRequestPayload>() }.getOrElse { error ->
            call.respond(HttpStatusCode.BadRequest, error.message.orEmpty())
            return@post
        }
        try {
            call.respond(
                context.kafkaSettingsService.saveSettings(
                    request = payload,
                    currentUiConfig = context.currentUiConfig(),
                ),
            )
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, e.message.orEmpty())
        }
    }

    post("/api/kafka/settings/test-connection") {
        val payload = runCatching { call.receive<KafkaSettingsConnectionTestRequestPayload>() }.getOrElse { error ->
            call.respond(HttpStatusCode.BadRequest, error.message.orEmpty())
            return@post
        }
        try {
            call.respond(
                context.kafkaSettingsService.testConnection(
                    request = payload,
                    currentUiConfig = context.currentUiConfig(),
                ),
            )
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, e.message.orEmpty())
        }
    }
}
