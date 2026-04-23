package com.sbrf.lt.platform.composeui.run_history_cleanup

import com.sbrf.lt.platform.composeui.model.DatabaseConnectionStatus
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeActorState
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal fun sampleRunHistoryCleanupRuntimeContext(): RuntimeContext =
    RuntimeContext(
        requestedMode = ModuleStoreMode.FILES,
        effectiveMode = ModuleStoreMode.FILES,
        actor = RuntimeActorState(
            resolved = true,
            message = "ok",
        ),
        database = DatabaseConnectionStatus(
            configured = true,
            available = true,
            schema = "public",
            message = "ok",
        ),
    )

internal fun sampleRunHistoryCleanupPreview(): RunHistoryCleanupPreviewResponse =
    RunHistoryCleanupPreviewResponse(
        storageMode = "files",
        safeguardEnabled = true,
        retentionDays = 14,
        keepMinRunsPerModule = 3,
        cutoffTimestamp = "2026-04-23T10:00:00Z",
    )

internal fun sampleOutputRetentionPreview(): OutputRetentionPreviewResponse =
    OutputRetentionPreviewResponse(
        storageMode = "files",
        safeguardEnabled = true,
        retentionDays = 14,
        keepMinRunsPerModule = 3,
        cutoffTimestamp = "2026-04-23T10:00:00Z",
    )

internal fun sampleRunHistoryCleanupResult(
    totalRunsDeleted: Int = 0,
    totalOrphanExecutionSnapshotsDeleted: Int = 0,
): RunHistoryCleanupResultResponse =
    RunHistoryCleanupResultResponse(
        storageMode = "files",
        safeguardEnabled = true,
        retentionDays = 14,
        keepMinRunsPerModule = 3,
        cutoffTimestamp = "2026-04-23T10:00:00Z",
        finishedAt = "2026-04-23T10:10:00Z",
        totalRunsDeleted = totalRunsDeleted,
        totalOrphanExecutionSnapshotsDeleted = totalOrphanExecutionSnapshotsDeleted,
    )

internal fun sampleOutputRetentionResult(
    totalOutputDirsDeleted: Int = 0,
    totalMissingOutputDirs: Int = 0,
): OutputRetentionResultResponse =
    OutputRetentionResultResponse(
        storageMode = "files",
        safeguardEnabled = true,
        retentionDays = 14,
        keepMinRunsPerModule = 3,
        cutoffTimestamp = "2026-04-23T10:00:00Z",
        finishedAt = "2026-04-23T10:10:00Z",
        totalOutputDirsDeleted = totalOutputDirsDeleted,
        totalMissingOutputDirs = totalMissingOutputDirs,
    )

internal class StubRunHistoryCleanupApi(
    private val runtimeContextHandler: suspend () -> RuntimeContext = { sampleRunHistoryCleanupRuntimeContext() },
    private val previewHandler: suspend (Boolean) -> RunHistoryCleanupPreviewResponse = { sampleRunHistoryCleanupPreview() },
    private val cleanupRunHistoryHandler: suspend (Boolean) -> RunHistoryCleanupResultResponse = {
        sampleRunHistoryCleanupResult()
    },
    private val outputPreviewHandler: suspend (Boolean) -> OutputRetentionPreviewResponse = { sampleOutputRetentionPreview() },
    private val cleanupOutputsHandler: suspend (Boolean) -> OutputRetentionResultResponse = {
        sampleOutputRetentionResult()
    },
) : RunHistoryCleanupApi {
    override suspend fun loadRuntimeContext(): RuntimeContext = runtimeContextHandler()

    override suspend fun loadPreview(disableSafeguard: Boolean): RunHistoryCleanupPreviewResponse =
        previewHandler(disableSafeguard)

    override suspend fun cleanupRunHistory(disableSafeguard: Boolean): RunHistoryCleanupResultResponse =
        cleanupRunHistoryHandler(disableSafeguard)

    override suspend fun loadOutputPreview(disableSafeguard: Boolean): OutputRetentionPreviewResponse =
        outputPreviewHandler(disableSafeguard)

    override suspend fun cleanupOutputs(disableSafeguard: Boolean): OutputRetentionResultResponse =
        cleanupOutputsHandler(disableSafeguard)
}

internal fun <T> runRunHistoryCleanupSuspend(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                completed = result
            }
        },
    )
    return completed!!.getOrThrow()
}
