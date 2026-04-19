package com.sbrf.lt.platform.composeui.foundation.runtime

import com.sbrf.lt.platform.composeui.model.RuntimeContext
import com.sbrf.lt.platform.composeui.model.label

fun RuntimeContext.hasModeFallback(): Boolean =
    requestedMode != effectiveMode

fun buildRuntimeModeFallbackMessage(
    runtimeContext: RuntimeContext,
    suffix: String? = null,
): String {
    val reason = runtimeContext.fallbackReason
        ?.takeIf { it.isNotBlank() }
        ?.let { " $it" }
        .orEmpty()
    val extra = suffix
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { " $it" }
        .orEmpty()
    return "Запрошен режим «${runtimeContext.requestedMode.label}», но сейчас активен «${runtimeContext.effectiveMode.label}».$reason$extra".trim()
}

fun buildDatabaseModeUnavailableMessage(
    fallbackReason: String?,
    defaultMessage: String,
): String =
    fallbackReason?.takeIf { it.isNotBlank() } ?: defaultMessage
