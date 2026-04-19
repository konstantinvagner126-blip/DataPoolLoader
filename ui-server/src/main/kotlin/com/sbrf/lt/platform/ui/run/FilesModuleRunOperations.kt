package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import com.sbrf.lt.platform.ui.model.UiStateResponse
import kotlinx.coroutines.flow.SharedFlow

interface FilesModuleRunOperations : UiCredentialsProvider {
    fun updates(): SharedFlow<UiStateResponse>

    fun currentState(): UiStateResponse

    fun uploadCredentials(fileName: String, content: String): CredentialsStatusResponse

    fun startRun(request: StartRunRequest): UiRunSnapshot

    fun loadModuleDetails(moduleId: String): ModuleDetailsResponse
}
