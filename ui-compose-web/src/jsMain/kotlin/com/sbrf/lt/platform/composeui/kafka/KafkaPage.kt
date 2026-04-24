package com.sbrf.lt.platform.composeui.kafka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeKafkaPage(
    route: KafkaRouteState,
    api: KafkaApi = remember { KafkaApiClient() },
) {
    val store = remember(api) { KafkaStore(api) }
    val scope = rememberCoroutineScope()
    var state by remember(route.clusterId, route.topicName) { mutableStateOf(KafkaPageState()) }

    LaunchedEffect(route.clusterId, route.topicName) {
        state = store.startLoading(state)
        state = runCatching {
            store.load(
                preferredClusterId = route.clusterId,
                preferredTopicName = route.topicName,
            )
        }.getOrElse { error ->
            KafkaPageState(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить Kafka metadata.",
            )
        }
    }

    PageScaffold(
        eyebrow = "Источники и брокеры",
        title = "Kafka cluster explorer",
        subtitle = "Локальный обзор кластеров, топиков и partition metadata по Kafka-каталогу из UI-конфига.",
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                A(attrs = {
                    classes("btn", "btn-outline-secondary")
                    href("/")
                }) { Text("На главную") }
                Button(attrs = {
                    classes("btn", "btn-dark")
                    attr("type", "button")
                    disabled()
                }) { Text("Kafka explorer") }
            }
        },
        content = {
            KafkaPageContent(
                state = state,
                onTopicQueryChange = { query ->
                    state = store.updateTopicQuery(state, query)
                },
                onApplyTopicQuery = {
                    scope.launch {
                        state = store.startTopicsReload(state)
                        state = runCatching {
                            store.reloadTopics(state)
                        }.getOrElse { error ->
                            state.copy(
                                topicsLoading = false,
                                topicOverviewLoading = false,
                                errorMessage = error.message ?: "Не удалось обновить список Kafka-топиков.",
                            )
                        }
                    }
                },
                onReloadTopicOverview = { topicName ->
                    scope.launch {
                        state = store.startTopicOverviewReload(state)
                        state = runCatching {
                            store.loadTopicOverview(state, topicName)
                        }.getOrElse { error ->
                            state.copy(
                                topicOverviewLoading = false,
                                errorMessage = error.message ?: "Не удалось загрузить topic overview.",
                            )
                        }
                    }
                },
            )
        },
    )
}
