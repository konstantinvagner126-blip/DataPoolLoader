package com.sbrf.lt.platform.composeui.foundation.http

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLAnchorElement
import org.w3c.fetch.RequestInit
import org.w3c.xhr.FormData
import kotlin.js.json

private val jsonCodec = Json {
    ignoreUnknownKeys = true
}

class ComposeHttpClient(
    private val baseUrl: String = window.location.origin,
) {
    suspend fun <T> get(
        path: String,
        deserializer: KSerializer<T>,
    ): T {
        val response = window.fetch("$baseUrl$path").await()
        val text = response.text().await()
        if (!response.ok) {
            error(text.ifBlank { "HTTP ${response.status.toInt()} для $path" })
        }
        return jsonCodec.decodeFromString(deserializer, text)
    }

    suspend fun <T> getOrNull(
        path: String,
        deserializer: KSerializer<T>,
    ): T? {
        val response = window.fetch("$baseUrl$path").await()
        val text = response.text().await()
        if (!response.ok) {
            return null
        }
        return jsonCodec.decodeFromString(deserializer, text)
    }

    suspend fun <T, R> postJson(
        path: String,
        payload: R,
        serializer: KSerializer<R>,
        deserializer: KSerializer<T>,
    ): T {
        val response = window.fetch(
            input = "$baseUrl$path",
            init = RequestInit(
                method = "POST",
                headers = json("Content-Type" to "application/json"),
                body = jsonCodec.encodeToString(serializer, payload),
            ),
        ).await()
        val text = response.text().await()
        if (!response.ok) {
            error(text.ifBlank { "HTTP ${response.status.toInt()} для $path" })
        }
        return jsonCodec.decodeFromString(deserializer, text)
    }

    suspend fun <T> postFormData(
        path: String,
        formData: FormData,
        deserializer: KSerializer<T>,
    ): T {
        val response = window.fetch(
            input = "$baseUrl$path",
            init = RequestInit(
                method = "POST",
                body = formData,
            ),
        ).await()
        val text = response.text().await()
        if (!response.ok) {
            error(text.ifBlank { "HTTP ${response.status.toInt()} для $path" })
        }
        return jsonCodec.decodeFromString(deserializer, text)
    }

    suspend fun <T> delete(
        path: String,
        deserializer: KSerializer<T>,
    ): T {
        val response = window.fetch(
            input = "$baseUrl$path",
            init = RequestInit(
                method = "DELETE",
                headers = json("Content-Type" to "application/json"),
            ),
        ).await()
        val text = response.text().await()
        if (!response.ok) {
            error(text.ifBlank { "HTTP ${response.status.toInt()} для $path" })
        }
        return jsonCodec.decodeFromString(deserializer, text)
    }

    suspend fun <R> downloadPostJson(
        path: String,
        payload: R,
        serializer: KSerializer<R>,
        fallbackFileName: String,
    ) {
        val response = window.fetch(
            input = "$baseUrl$path",
            init = RequestInit(
                method = "POST",
                headers = json("Content-Type" to "application/json"),
                body = jsonCodec.encodeToString(serializer, payload),
            ),
        ).await()
        if (!response.ok) {
            val responseText = response.text().await()
            error(responseText.ifBlank { "HTTP ${response.status.toInt()} для $path" })
        }
        val blob = response.blob().await()
        val objectUrl = window.asDynamic().URL.createObjectURL(blob) as String
        val link = document.createElement("a") as HTMLAnchorElement
        link.href = objectUrl
        link.download = extractFileName(response.headers.get("content-disposition")) ?: fallbackFileName
        document.body?.appendChild(link)
        link.click()
        link.remove()
        window.asDynamic().URL.revokeObjectURL(objectUrl)
    }

    private fun extractFileName(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) {
            return null
        }
        val regex = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
        return regex.find(contentDisposition)?.groupValues?.getOrNull(1)
    }
}
