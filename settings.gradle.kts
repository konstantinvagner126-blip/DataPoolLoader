pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "DataPoolLoader"

include(":core")
include(":ui")

val appsDir = file("apps")
if (appsDir.exists()) {
    appsDir.listFiles()
        ?.filter { it.isDirectory && file("${it.path}/build.gradle.kts").exists() }
        ?.sortedBy { it.name }
        ?.forEach { include(":apps:${it.name}") }
}
