import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

apply(from = "repositories.gradle")

plugins {
    kotlin("jvm") version "2.2.0" apply false
}

group = "com.sbrf.lt.datapool"
version = "1.0.0"

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
        jvmToolchain(17)
    }

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    dependencies {
        add("testImplementation", kotlin("test"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter:5.13.4")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

abstract class CreateAppModuleTask : DefaultTask() {
    @get:Input
    val appName = project.objects.property(String::class.java)

    @TaskAction
    fun generate() {
        val rawName = appName.orNull?.trim().orEmpty()
        require(rawName.isNotEmpty()) {
            "Укажите имя модуля через -PappName=<module-name>."
        }
        require(Regex("[a-z0-9-]+").matches(rawName)) {
            "Имя модуля должно содержать только строчные буквы, цифры и дефис."
        }

        val targetDir = project.layout.projectDirectory.dir("apps/$rawName").asFile.toPath()
        require(Files.notExists(targetDir)) {
            "Модуль apps/$rawName уже существует."
        }

        val templateDir = project.layout.projectDirectory.dir("templates/app-module").asFile.toPath()
        require(Files.exists(templateDir)) {
            "Шаблон templates/app-module не найден."
        }

        Files.walk(templateDir).use { paths ->
            paths.forEach { source ->
                val relative = templateDir.relativize(source).toString()
                val target = targetDir.resolve(relative)
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    val content = Files.readString(source)
                        .replace("__APP_NAME__", rawName)
                    Files.writeString(target, content)
                }
            }
        }

        logger.lifecycle("Создан новый app-модуль: apps/{}", rawName)
        logger.lifecycle("Следующий шаг: перезагрузить Gradle-проект или выполнить ./gradlew projects")
    }
}

tasks.register<CreateAppModuleTask>("createAppModule") {
    group = "application"
    description = "Создает новый app-модуль в apps/<name> из шаблона templates/app-module"
    appName.convention(
        providers.gradleProperty("appName")
    )
}
