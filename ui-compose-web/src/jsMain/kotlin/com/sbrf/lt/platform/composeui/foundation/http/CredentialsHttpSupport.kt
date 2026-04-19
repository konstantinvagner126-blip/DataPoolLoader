package com.sbrf.lt.platform.composeui.foundation.http

import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import org.w3c.files.File
import org.w3c.xhr.FormData

internal suspend fun loadCredentialsStatus(
    httpClient: ComposeHttpClient,
): CredentialsStatusResponse? =
    runCatching {
        httpClient.get("/api/credentials", CredentialsStatusResponse.serializer())
    }.getOrNull()

internal suspend fun uploadCredentialsFile(
    httpClient: ComposeHttpClient,
    file: File,
): CredentialsStatusResponse {
    val formData = FormData()
    formData.append("file", file, file.name)
    return httpClient.postFormData(
        path = "/api/credentials/upload",
        formData = formData,
        deserializer = CredentialsStatusResponse.serializer(),
    )
}
