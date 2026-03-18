plugins {
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass = "com.sbrf.lt.datapool.app.MainKt"
}
