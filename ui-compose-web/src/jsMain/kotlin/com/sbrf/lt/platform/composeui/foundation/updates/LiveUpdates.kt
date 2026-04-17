package com.sbrf.lt.platform.composeui.foundation.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event

/**
 * Периодически вызывает suspend-блок, пока эффект включен.
 */
@Composable
fun PollingEffect(
    enabled: Boolean,
    intervalMs: Int,
    onTick: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestOnTick = rememberUpdatedState(onTick)

    DisposableEffect(enabled, intervalMs) {
        if (!enabled) {
            onDispose {}
        } else {
            val handle = window.setInterval(
                handler = {
                    scope.launch {
                        latestOnTick.value()
                    }
                },
                timeout = intervalMs,
            )
            onDispose {
                window.clearInterval(handle)
            }
        }
    }
}

/**
 * Подписывается на browser WebSocket и вызывает обработчики сообщений и ошибок.
 */
@Composable
fun WebSocketEffect(
    enabled: Boolean,
    url: String,
    onMessage: suspend (String?) -> Unit,
    onError: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val latestOnMessage = rememberUpdatedState(onMessage)
    val latestOnError = rememberUpdatedState(onError)

    DisposableEffect(enabled, url) {
        if (!enabled) {
            onDispose {}
        } else {
            val socket = WebSocket(url)
            socket.onmessage = { event: MessageEvent ->
                val payload = event.data?.toString()
                scope.launch {
                    latestOnMessage.value(payload)
                }
            }
            socket.onerror = { _: Event ->
                latestOnError.value?.invoke("Не удалось получить live-обновления по WebSocket.")
            }
            onDispose {
                socket.onmessage = null
                socket.onerror = null
                socket.close()
            }
        }
    }
}
