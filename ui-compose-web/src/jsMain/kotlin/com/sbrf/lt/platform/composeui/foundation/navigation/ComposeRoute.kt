package com.sbrf.lt.platform.composeui.foundation.navigation

import kotlinx.browser.window

data class ComposeRoute(
    val screen: String,
    val params: Map<String, String>,
)

fun currentComposeRoute(): ComposeRoute {
    val map = parseQueryString(window.location.search)
    return ComposeRoute(
        screen = map["screen"] ?: "home",
        params = map,
    )
}

private fun parseQueryString(search: String): Map<String, String> {
    if (search.isBlank()) return emptyMap()
    return search
        .removePrefix("?")
        .split("&")
        .asSequence()
        .mapNotNull { chunk ->
            if (chunk.isBlank()) return@mapNotNull null
            val separatorIndex = chunk.indexOf("=")
            val rawKey = if (separatorIndex >= 0) chunk.substring(0, separatorIndex) else chunk
            if (rawKey.isBlank()) return@mapNotNull null
            val rawValue = if (separatorIndex >= 0) chunk.substring(separatorIndex + 1) else ""
            decodeURIComponent(rawKey) to decodeURIComponent(rawValue)
        }
        .toMap()
}

private external fun decodeURIComponent(encodedURI: String): String
