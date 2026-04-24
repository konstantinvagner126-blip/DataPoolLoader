plugins {
    application
}

val packagedAppName = "LoadTestingDataPlatform"

version = rootProject.version

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated/composeWebResources"))
    }
}

val syncComposeWebAssets by tasks.registering(Sync::class) {
    group = "build"
    description = "Копирует собранный Compose Web UI в ресурсы UI"
    dependsOn(":ui-compose-web:exportComposeWebAssets")
    from(project(":ui-compose-web").layout.buildDirectory.dir("exportedComposeWeb"))
    into(layout.buildDirectory.dir("generated/composeWebResources/static/compose-app"))
}

tasks.named("processResources") {
    dependsOn(syncComposeWebAssets)
}

dependencies {
    implementation(project(":core"))
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.apache.kafka:kafka-clients:3.9.0")
    implementation("io.ktor:ktor-server-core-jvm:3.2.3")
    implementation("io.ktor:ktor-server-netty-jvm:3.2.3")
    implementation("io.ktor:ktor-server-websockets-jvm:3.2.3")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.2.3")
    implementation("io.ktor:ktor-serialization-jackson-jvm:3.2.3")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.2.3")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.2.3")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.2.3")
}

application {
    mainClass = "com.sbrf.lt.platform.ui.app.MainKt"
}

fun currentOsName(): String = System.getProperty("os.name").lowercase()

fun currentInstallerType(): String = when {
    currentOsName().contains("mac") -> "dmg"
    currentOsName().contains("win") -> "msi"
    currentOsName().contains("linux") -> "deb"
    else -> error("Неподдерживаемая ОС для jpackage: ${System.getProperty("os.name")}")
}

fun jpackageExecutable(): String {
    val executableName = if (currentOsName().contains("win")) "jpackage.exe" else "jpackage"
    val localExecutable = file("${System.getProperty("java.home")}/bin/$executableName")
    require(localExecutable.exists()) {
        "Не найден jpackage в Gradle JVM: ${localExecutable.absolutePath}. Укажи JDK с jpackage в настройке Gradle JVM."
    }
    return localExecutable.absolutePath
}

fun optionalJpackageIcon(): String? {
    val baseDir = project.layout.projectDirectory.dir("src/jpackage").asFile
    val iconName = when {
        currentOsName().contains("mac") -> "icon.icns"
        currentOsName().contains("win") -> "icon.ico"
        currentOsName().contains("linux") -> "icon.png"
        else -> null
    } ?: return null
    val iconFile = baseDir.resolve(iconName)
    return iconFile.takeIf { it.exists() }?.absolutePath
}

tasks.register<Exec>("jpackageAppImage") {
    group = "distribution"
    description = "Собирает desktop app-image для UI через jpackage"
    dependsOn(tasks.named("installDist"))

    doFirst {
        val inputDir = layout.buildDirectory.dir("install/${project.name}/lib").get().asFile
        val destinationDir = layout.buildDirectory.dir("jpackage").get().asFile
        val mainJarName = tasks.named<Jar>("jar").get().archiveFileName.get()
        val existingAppImage = destinationDir.resolve(
            if (currentOsName().contains("mac")) "$packagedAppName.app" else packagedAppName,
        )

        if (existingAppImage.exists()) {
            existingAppImage.deleteRecursively()
        }

        val args = mutableListOf(
            jpackageExecutable(),
            "--type", "app-image",
            "--name", packagedAppName,
            "--app-version", project.version.toString(),
            "--vendor", "MLP",
            "--description", "Load Testing Data Platform UI",
            "--input", inputDir.absolutePath,
            "--main-jar", mainJarName,
            "--main-class", application.mainClass.get(),
            "--dest", destinationDir.absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8",
        )
        optionalJpackageIcon()?.let { iconPath ->
            args.addAll(listOf("--icon", iconPath))
        }
        commandLine(args)
    }

    doLast {
        val destinationDir = layout.buildDirectory.dir("jpackage").get().asFile
        val bundledConfig = destinationDir.resolve("ui-application.yml")
        val template = project.layout.projectDirectory.file("src/jpackage/ui-application.example.yml").asFile
        val appsRoot = rootProject.layout.projectDirectory.dir("apps").asFile.absolutePath
        bundledConfig.writeText(
            template.readText().replace("__APPS_ROOT__", appsRoot),
        )
    }
}

tasks.register<Exec>("jpackageInstaller") {
    group = "distribution"
    description = "Собирает desktop installer для UI через jpackage под текущую ОС"
    dependsOn(tasks.named("installDist"))

    doFirst {
        val inputDir = layout.buildDirectory.dir("install/${project.name}/lib").get().asFile
        val destinationDir = layout.buildDirectory.dir("jpackage").get().asFile
        val mainJarName = tasks.named<Jar>("jar").get().archiveFileName.get()
        val extension = currentInstallerType()
        val existingInstaller = destinationDir.resolve("$packagedAppName-${project.version}.$extension")

        if (existingInstaller.exists()) {
            existingInstaller.delete()
        }

        val args = mutableListOf(
            jpackageExecutable(),
            "--type", currentInstallerType(),
            "--name", packagedAppName,
            "--app-version", project.version.toString(),
            "--vendor", "MLP",
            "--description", "Load Testing Data Platform UI",
            "--input", inputDir.absolutePath,
            "--main-jar", mainJarName,
            "--main-class", application.mainClass.get(),
            "--dest", destinationDir.absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8",
        )
        optionalJpackageIcon()?.let { iconPath ->
            args.addAll(listOf("--icon", iconPath))
        }
        commandLine(args)
    }
}
