package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException

internal class DatabaseModuleNotFoundException(
    moduleCode: String,
) : UiEntityNotFoundException("DB-модуль '$moduleCode' не найден.")
