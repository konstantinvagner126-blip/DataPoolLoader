package com.sbrf.lt.platform.composeui.module_editor

internal fun updateSource(
    formState: ConfigFormStateDto,
    index: Int,
    transform: ConfigFormSourceStateDto.() -> ConfigFormSourceStateDto,
): ConfigFormStateDto =
    formState.copy(
        sources = formState.sources.mapIndexed { sourceIndex, source ->
            if (sourceIndex == index) {
                source.transform()
            } else {
                source
            }
        },
    )

internal fun updateQuota(
    formState: ConfigFormStateDto,
    index: Int,
    transform: ConfigFormQuotaStateDto.() -> ConfigFormQuotaStateDto,
): ConfigFormStateDto =
    formState.copy(
        quotas = formState.quotas.mapIndexed { quotaIndex, quota ->
            if (quotaIndex == index) {
                quota.transform()
            } else {
                quota
            }
        },
    )
