plugins {
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass = "com.example.datapoolloader.app.MainKt"
}
