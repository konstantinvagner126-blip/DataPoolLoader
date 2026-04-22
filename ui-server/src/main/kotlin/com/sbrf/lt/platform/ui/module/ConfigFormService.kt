package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.platform.ui.model.ConfigFormStateResponse
import com.sbrf.lt.platform.ui.model.ConfigFormUpdateResponse

class ConfigFormService(
    configLoader: ConfigLoader = ConfigLoader(),
) {
    private val mapper = configLoader.objectMapper()
    private val parsingSupport = ConfigFormParsingSupport(mapper, com.sbrf.lt.datapool.model.AppConfig())
    private val updateSupport = ConfigFormUpdateSupport(mapper)

    fun parse(configText: String): ConfigFormStateResponse =
        parsingSupport.parse(configText)

    fun apply(configText: String, formState: ConfigFormStateResponse): ConfigFormUpdateResponse {
        return updateSupport.apply(
            configText = configText,
            formState = formState,
            parseUpdated = ::parse,
        )
    }
}
