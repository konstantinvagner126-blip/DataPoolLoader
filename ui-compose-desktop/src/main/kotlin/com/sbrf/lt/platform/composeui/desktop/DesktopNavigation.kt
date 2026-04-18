package com.sbrf.lt.platform.composeui.desktop

enum class DesktopScreen(
    val title: String,
) {
    HOME("Главная"),
    RUNS("История запусков"),
}

data class DesktopNavigationState(
    val screen: DesktopScreen = DesktopScreen.HOME,
    val runsStorage: String = "database",
    val runsModuleId: String = "local-manual-test",
    val runsHistoryLimit: Int = 20,
)
