plugins {
    `java-library`
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib")
    api("org.postgresql:postgresql:42.7.7")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.0")
    api("org.slf4j:slf4j-simple:2.0.17")
    api("org.apache.commons:commons-csv:1.14.1")
}
