plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

version = rootProject.version

kotlin {
    jvm()

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
