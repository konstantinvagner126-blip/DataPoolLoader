package com.sbrf.lt.platform.composeui.run_history_cleanup

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.model.RuntimeContext

class RunHistoryCleanupApiClient(
    private val httpClient: ComposeHttpClient = ComposeHttpClient(),
) : RunHistoryCleanupApi {
    override suspend fun loadRuntimeContext(): RuntimeContext =
        httpClient.get("/api/ui/runtime-context", RuntimeContext.serializer())

    override suspend fun loadPreview(disableSafeguard: Boolean): RunHistoryCleanupPreviewResponse =
        httpClient.get(
            "/api/run-history/cleanup/preview?disableSafeguard=$disableSafeguard",
            RunHistoryCleanupPreviewResponse.serializer(),
        )

    override suspend fun cleanupRunHistory(disableSafeguard: Boolean): RunHistoryCleanupResultResponse =
        httpClient.postJson(
            path = "/api/run-history/cleanup",
            payload = RunHistoryCleanupRequestDto(disableSafeguard),
            serializer = RunHistoryCleanupRequestDto.serializer(),
            deserializer = RunHistoryCleanupResultResponse.serializer(),
        )

    override suspend fun loadOutputPreview(disableSafeguard: Boolean): OutputRetentionPreviewResponse =
        httpClient.get(
            "/api/output-retention/preview?disableSafeguard=$disableSafeguard",
            OutputRetentionPreviewResponse.serializer(),
        )

    override suspend fun cleanupOutputs(disableSafeguard: Boolean): OutputRetentionResultResponse =
        httpClient.postJson(
            path = "/api/output-retention",
            payload = OutputRetentionRequestDto(disableSafeguard),
            serializer = OutputRetentionRequestDto.serializer(),
            deserializer = OutputRetentionResultResponse.serializer(),
        )
}
