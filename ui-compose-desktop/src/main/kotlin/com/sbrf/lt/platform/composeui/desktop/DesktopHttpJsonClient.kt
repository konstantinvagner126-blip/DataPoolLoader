package com.sbrf.lt.platform.composeui.desktop

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DesktopHttpJsonClient(
    private val serverBaseUrl: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun <T> get(path: String, deserializer: KSerializer<T>): T {
        val request = HttpRequest.newBuilder()
            .uri(resolve(path))
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()} for $path: ${response.body().ifBlank { "<empty>" }}")
        }
        return json.decodeFromString(deserializer, response.body())
    }

    fun <T> getOrNull(path: String, deserializer: KSerializer<T>): T? =
        runCatching { get(path, deserializer) }.getOrNull()

    fun <I, O> postJson(
        path: String,
        payload: I,
        serializer: KSerializer<I>,
        responseSerializer: KSerializer<O>,
    ): O {
        val request = HttpRequest.newBuilder()
            .uri(resolve(path))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(serializer, payload)))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()} for $path: ${response.body().ifBlank { "<empty>" }}")
        }
        return json.decodeFromString(responseSerializer, response.body())
    }

    private fun resolve(path: String): URI =
        URI.create(serverBaseUrl.trimEnd('/') + path)
}
