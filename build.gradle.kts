import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.xml.parsers.DocumentBuilderFactory

apply(from = "repositories.gradle")

plugins {
    kotlin("jvm") version "2.2.0" apply false
    kotlin("multiplatform") version "2.2.0" apply false
    kotlin("plugin.compose") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "com.sbrf.lt.datapool"
version = "1.0.0"

subprojects {
    if (path == ":ui-compose-web" || path == ":ui-compose-shared" || path == ":ui-compose-desktop") {
        return@subprojects
    }

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
        jvmToolchain(21)
    }

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }

    dependencies {
        add("testImplementation", kotlin("test"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter:5.13.4")
    }

    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("local-postgres")
        }
    }

    pluginManager.withPlugin("application") {
        tasks.withType<JavaExec>().configureEach {
            System.getProperty("credentials.file")
                ?.takeIf { it.isNotBlank() }
                ?.let { systemProperty("credentials.file", it) }
        }
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

abstract class VerifyKoverLineCoverageTask : DefaultTask() {
    @get:InputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    abstract val minimumLineCoveragePercent: Property<Double>

    @get:Input
    abstract val coverageScopeLabel: Property<String>

    @TaskAction
    fun verify() {
        val report = reportFile.get().asFile
        require(report.exists()) {
            "Kover report not found: ${report.absolutePath}"
        }

        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(report)
        val root = document.documentElement
        val lineCounter = (0 until root.childNodes.length)
            .asSequence()
            .map { root.childNodes.item(it) }
            .filter { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE }
            .map { it as org.w3c.dom.Element }
            .firstOrNull { it.tagName == "counter" && it.getAttribute("type") == "LINE" }
            ?: error("Failed to locate root LINE counter in ${report.absolutePath}")

        val missed = lineCounter.getAttribute("missed").toLong()
        val covered = lineCounter.getAttribute("covered").toLong()
        val total = missed + covered
        require(total > 0) {
            "Kover report for ${coverageScopeLabel.get()} has zero measured lines: ${report.absolutePath}"
        }

        val actualPercent = covered.toDouble() * 100.0 / total.toDouble()
        val minimumPercent = minimumLineCoveragePercent.get()

        logger.lifecycle(
            "Kover line coverage for {}: {}% (covered={}, total={}, floor={}%)",
            coverageScopeLabel.get(),
            String.format("%.2f", actualPercent),
            covered,
            total,
            String.format("%.2f", minimumPercent),
        )

        check(actualPercent + 1e-9 >= minimumPercent) {
            "Kover line coverage for ${coverageScopeLabel.get()} is ${"%.2f".format(actualPercent)}%, " +
                "below floor ${"%.2f".format(minimumPercent)}%."
        }
    }
}

val uiServerKoverXmlReport = project(":ui-server").tasks.named("koverXmlReport")
val uiServerKoverHtmlReport = project(":ui-server").tasks.named("koverHtmlReport")
val uiServerKoverReportFile = project(":ui-server").layout.buildDirectory.file("reports/kover/report.xml")

tasks.register("serverCoverageXmlReport") {
    group = "verification"
    description = "Строит bounded Kover XML report только для server-side scope (:ui-server)"
    dependsOn(uiServerKoverXmlReport)
}

tasks.register("serverCoverageHtmlReport") {
    group = "verification"
    description = "Строит bounded Kover HTML report только для server-side scope (:ui-server)"
    dependsOn(uiServerKoverHtmlReport)
}

tasks.register<VerifyKoverLineCoverageTask>("verifyServerCoverageFloor") {
    group = "verification"
    description = "Проверяет, что Kover line coverage для server-side scope (:ui-server) не ниже 80%"
    dependsOn(uiServerKoverXmlReport)
    reportFile.set(uiServerKoverReportFile)
    minimumLineCoveragePercent.set(80.0)
    coverageScopeLabel.set(":ui-server")
}
