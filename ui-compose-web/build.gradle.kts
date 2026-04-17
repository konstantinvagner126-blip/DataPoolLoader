import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

version = rootProject.version

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "compose-ui-spike.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        add(project.projectDir.path)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.html.core)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
    }
}
val exportComposeSpike by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Собирает и подготавливает артефакты Compose Web spike"
    dependsOn(tasks.named("jsBrowserDistribution"))
    from(layout.buildDirectory.dir("dist/js/productionExecutable"))
    into(layout.buildDirectory.dir("exportedComposeSpike"))
}
