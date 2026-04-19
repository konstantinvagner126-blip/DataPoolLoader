package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException

internal class ModuleNotFoundException(
    moduleId: String,
) : UiEntityNotFoundException("Модуль '$moduleId' не найден.")
