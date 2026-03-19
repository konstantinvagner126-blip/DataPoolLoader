package com.sbrf.lt.datapool.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

fun main(args: Array<String>) {
    val configArg = args.firstOrNull { it.startsWith("--config=") }
    val configPath = configArg?.substringAfter("=")?.let(Path::of)
        ?: extractDefaultConfig()
    val credentialsPath = System.getProperty("credentials.file")?.let(Path::of)

    if (credentialsPath != null) {
        ApplicationRunner().run(configPath, credentialsPath)
    } else {
        ApplicationRunner().run(configPath)
    }
}

private fun extractDefaultConfig(): Path {
    val resourceStream = object {}.javaClass.classLoader.getResourceAsStream("application.yml")
        ?: error("Ресурс application.yml не найден в classpath.")
    val tempFile = Files.createTempFile("local-manual-test-", ".yml")
    resourceStream.use { input ->
        Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
    }
    tempFile.toFile().deleteOnExit()
    return tempFile
}
