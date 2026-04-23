pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "DataPoolLoader"

include(":core")
include(":ui-server")
include(":ui-compose-shared")
include(":ui-compose-web")
include(":ui-compose-desktop")

val appsDir = file("apps")
if (appsDir.exists()) {
    appsDir.listFiles()
        ?.filter { it.isDirectory && file("${it.path}/build.gradle.kts").exists() }
        ?.sortedBy { it.name }
        ?.forEach { include(":apps:${it.name}") }
}
