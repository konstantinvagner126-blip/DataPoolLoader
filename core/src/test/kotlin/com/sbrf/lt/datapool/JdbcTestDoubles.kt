package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.db.TargetColumn
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement

data class FakeConnectionState(
    var autoCommit: Boolean = true,
    var committed: Int = 0,
    var rolledBack: Int = 0,
    val executedSql: MutableList<String> = mutableListOf(),
)

fun exportConnection(
    columns: List<String>,
    rows: List<List<Any?>>,
    state: FakeConnectionState = FakeConnectionState(),
): Connection {
    val resultSet = exportResultSet(columns, rows)
    val preparedStatement = Proxy.newProxyInstance(
        PreparedStatement::class.java.classLoader,
        arrayOf(PreparedStatement::class.java),
    ) { _, method, args ->
        when (method.name) {
            "executeQuery" -> resultSet
            "setFetchSize", "close" -> null
            "getFetchSize" -> 0
            "isClosed" -> false
            "unwrap" -> null
            "isWrapperFor" -> false
            "toString" -> "FakePreparedStatement"
            else -> defaultValue(method.returnType)
        }
    } as PreparedStatement

    return Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "prepareStatement" -> preparedStatement
            "setAutoCommit" -> {
                state.autoCommit = args?.get(0) as Boolean
                null
            }
            "getAutoCommit" -> state.autoCommit
            "commit" -> {
                state.committed++
                null
            }
            "rollback" -> {
                state.rolledBack++
                null
            }
            "close" -> null
            "isClosed" -> false
            "unwrap" -> null
            "isWrapperFor" -> false
            "toString" -> "FakeExportConnection"
            else -> defaultValue(method.returnType)
        }
    } as Connection
}

fun validatorConnection(
    columns: List<TargetColumn>,
    state: FakeConnectionState = FakeConnectionState(),
): Connection {
    val rows = columns.map {
        mapOf(
            "column_name" to it.name,
            "is_nullable" to if (it.nullable) "YES" else "NO",
            "column_default" to if (it.hasDefault) "default" else null,
        )
    }
    val resultSet = mapResultSet(rows)
    val preparedStatement = Proxy.newProxyInstance(
        PreparedStatement::class.java.classLoader,
        arrayOf(PreparedStatement::class.java),
    ) { _, method, args ->
        when (method.name) {
            "setString", "close" -> null
            "executeQuery" -> resultSet
            "isClosed" -> false
            "unwrap" -> null
            "isWrapperFor" -> false
            "toString" -> "FakeValidatorPreparedStatement"
            else -> defaultValue(method.returnType)
        }
    } as PreparedStatement

    return Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "prepareStatement" -> preparedStatement
            "setAutoCommit" -> {
                state.autoCommit = args?.get(0) as Boolean
                null
            }
            "getAutoCommit" -> state.autoCommit
            "close" -> null
            "isClosed" -> false
            "unwrap" -> null
            "isWrapperFor" -> false
            "toString" -> "FakeValidatorConnection"
            else -> defaultValue(method.returnType)
        }
    } as Connection
}

fun importerConnection(state: FakeConnectionState = FakeConnectionState()): Connection {
    val statement = Proxy.newProxyInstance(
        Statement::class.java.classLoader,
        arrayOf(Statement::class.java),
    ) { _, method, args ->
        when (method.name) {
            "execute" -> {
                state.executedSql += args?.get(0) as String
                true
            }
            "close" -> null
            "isClosed" -> false
            "unwrap" -> null
            "isWrapperFor" -> false
            "toString" -> "FakeStatement"
            else -> defaultValue(method.returnType)
        }
    } as Statement

    return Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "createStatement" -> statement
            "setAutoCommit" -> {
                state.autoCommit = args?.get(0) as Boolean
                null
            }
            "getAutoCommit" -> state.autoCommit
            "commit" -> {
                state.committed++
                null
            }
            "rollback" -> {
                state.rolledBack++
                null
            }
            "close" -> null
            "isClosed" -> false
            "unwrap" -> null
            "isWrapperFor" -> false
            "toString" -> "FakeImporterConnection"
            else -> defaultValue(method.returnType)
        }
    } as Connection
}

private fun exportResultSet(columns: List<String>, rows: List<List<Any?>>): ResultSet {
    var cursor = -1
    val metaData = Proxy.newProxyInstance(
        ResultSetMetaData::class.java.classLoader,
        arrayOf(ResultSetMetaData::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getColumnCount" -> columns.size
            "getColumnLabel" -> columns[(args?.get(0) as Int) - 1]
            else -> defaultValue(method.returnType)
        }
    } as ResultSetMetaData

    return Proxy.newProxyInstance(
        ResultSet::class.java.classLoader,
        arrayOf(ResultSet::class.java),
    ) { _, method, args ->
        when (method.name) {
            "next" -> {
                cursor++
                cursor < rows.size
            }
            "getObject" -> rows[cursor][(args?.get(0) as Int) - 1]
            "getMetaData" -> metaData
            "close" -> null
            "isClosed" -> false
            "unwrap" -> null
            "isWrapperFor" -> false
            "toString" -> "FakeExportResultSet"
            else -> defaultValue(method.returnType)
        }
    } as ResultSet
}

private fun mapResultSet(rows: List<Map<String, Any?>>): ResultSet {
    var cursor = -1
    return Proxy.newProxyInstance(
        ResultSet::class.java.classLoader,
        arrayOf(ResultSet::class.java),
    ) { _, method, args ->
        when (method.name) {
            "next" -> {
                cursor++
                cursor < rows.size
            }
            "getString" -> {
                val key = args?.get(0) as String
                rows[cursor][key] as String?
            }
            "close" -> null
            "isClosed" -> false
            "unwrap" -> null
            "isWrapperFor" -> false
            "toString" -> "FakeMapResultSet"
            else -> defaultValue(method.returnType)
        }
    } as ResultSet
}

private fun defaultValue(type: Class<*>): Any? = when {
    type == java.lang.Boolean.TYPE -> false
    type == java.lang.Integer.TYPE -> 0
    type == java.lang.Long.TYPE -> 0L
    type == java.lang.Double.TYPE -> 0.0
    type == java.lang.Float.TYPE -> 0f
    type == java.lang.Short.TYPE -> 0.toShort()
    type == java.lang.Byte.TYPE -> 0.toByte()
    type == java.lang.Character.TYPE -> '\u0000'
    else -> null
}
