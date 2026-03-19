package com.sbrf.lt.datapool

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.UUID

object LocalPostgresTestSupport {
    private val fileProperties: Properties by lazy {
        val props = Properties()
        val path = Path.of("gradle", "local-postgres-test.properties")
        if (Files.exists(path)) {
            Files.newBufferedReader(path).use(props::load)
        }
        props
    }

    val host: String get() = read("datapool.test.pg.host", "host", "127.0.0.1")
    val port: Int get() = read("datapool.test.pg.port", "port", "5432").toInt()
    val database: String get() = read("datapool.test.pg.database", "database", "postgres")
    val username: String get() = read("datapool.test.pg.username", "username", System.getProperty("user.name"))
    val password: String get() = read("datapool.test.pg.password", "password", "dummy")
    val jdbcUrl: String get() = "jdbc:postgresql://$host:$port/$database"

    fun connection(): Connection = DriverManager.getConnection(jdbcUrl, username, password)

    fun createSchema(prefix: String = "datapool_test"): String {
        val schema = "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""create schema "$schema"""")
            }
        }
        return schema
    }

    fun dropSchema(schema: String) {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""drop schema if exists "$schema" cascade""")
            }
        }
    }

    fun quote(identifier: String): String = "\"${identifier.replace("\"", "\"\"")}\""

    private fun read(systemKey: String, fileKey: String, default: String): String =
        System.getProperty(systemKey)
            ?.takeIf { it.isNotBlank() }
            ?: fileProperties.getProperty(fileKey)
                ?.takeIf { it.isNotBlank() || fileKey == "password" }
            ?: default
}
