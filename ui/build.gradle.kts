plugins {
    application
}

dependencies {
    implementation(project(":core"))
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
    mainClass = "com.sbrf.lt.datapool.ui.MainKt"
}
