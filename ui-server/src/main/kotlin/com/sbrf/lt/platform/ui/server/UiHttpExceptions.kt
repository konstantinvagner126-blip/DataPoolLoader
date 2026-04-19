package com.sbrf.lt.platform.ui.server

import io.ktor.http.HttpStatusCode

internal sealed class UiHttpException(
    val statusCode: HttpStatusCode,
    override val message: String,
) : RuntimeException(message)

internal class UiBadRequestException(message: String) : UiHttpException(HttpStatusCode.BadRequest, message)

internal class UiConflictException(message: String) : UiHttpException(HttpStatusCode.Conflict, message)

internal class UiNotFoundException(message: String) : UiHttpException(HttpStatusCode.NotFound, message)

internal class UiServiceUnavailableException(message: String) : UiHttpException(HttpStatusCode.ServiceUnavailable, message)

internal fun badRequest(message: String): Nothing = throw UiBadRequestException(message)

internal fun conflict(message: String): Nothing = throw UiConflictException(message)

internal fun notFound(message: String): Nothing = throw UiNotFoundException(message)

internal fun serviceUnavailable(message: String): Nothing = throw UiServiceUnavailableException(message)
