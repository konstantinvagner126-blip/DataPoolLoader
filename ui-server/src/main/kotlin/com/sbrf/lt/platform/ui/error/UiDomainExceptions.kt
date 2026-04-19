package com.sbrf.lt.platform.ui.error

internal open class UiEntityNotFoundException(
    message: String,
) : RuntimeException(message)

internal open class UiStateConflictException(
    message: String,
) : RuntimeException(message)
