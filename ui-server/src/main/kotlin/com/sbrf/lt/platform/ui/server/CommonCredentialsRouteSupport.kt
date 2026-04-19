package com.sbrf.lt.platform.ui.server

import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining

internal data class UploadedCredentialsPayload(
    val fileName: String,
    val content: String,
)

internal suspend fun ApplicationCall.readUploadedCredentialsPayload(): UploadedCredentialsPayload {
    val multipart = receiveMultipart()
    var fileName = "credential.properties"
    var content: String? = null
    while (true) {
        val part = multipart.readPart() ?: break
        when (part) {
            is PartData.FileItem -> {
                fileName = part.originalFileName ?: fileName
                content = part.provider().readRemaining().readText()
            }
            else -> Unit
        }
        part.dispose.invoke()
    }
    return UploadedCredentialsPayload(
        fileName = fileName,
        content = content?.takeIf { it.isNotBlank() }
            ?: badRequest("Не удалось прочитать содержимое credential.properties."),
    )
}
