package com.example.datapoolloader.app

import java.nio.file.Path

fun main(args: Array<String>) {
    val configArg = args.firstOrNull { it.startsWith("--config=") }
    val credentialsArg = args.firstOrNull { it.startsWith("--credentials=") }
    val configPath = configArg?.substringAfter("=")?.let(Path::of)
        ?: Path.of("apps/dc-sms-offer/src/main/resources/application.yml")
    val credentialsPath = credentialsArg?.substringAfter("=")?.let(Path::of)

    ApplicationRunner().run(configPath, credentialsPath)
}
