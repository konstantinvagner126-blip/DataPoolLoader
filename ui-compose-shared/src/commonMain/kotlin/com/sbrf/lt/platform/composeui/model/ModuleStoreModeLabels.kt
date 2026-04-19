package com.sbrf.lt.platform.composeui.model

val ModuleStoreMode.label: String
    get() = when (this) {
        ModuleStoreMode.FILES -> "Файлы"
        ModuleStoreMode.DATABASE -> "База данных"
    }
