package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

internal fun createUiServerObjectMapper(): ObjectMapper = ObjectMapper().applyUiServerDefaults()

internal fun ObjectMapper.applyUiServerDefaults(): ObjectMapper = apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}
