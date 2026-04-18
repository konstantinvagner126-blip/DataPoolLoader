plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

version = rootProject.version

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ui-compose-shared"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.sbrf.lt.platform.composeui.desktop.MainKt"
    }
}
