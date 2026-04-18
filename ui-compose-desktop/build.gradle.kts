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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

compose.desktop {
    application {
        mainClass = "com.sbrf.lt.platform.composeui.desktop.MainKt"
    }
}
