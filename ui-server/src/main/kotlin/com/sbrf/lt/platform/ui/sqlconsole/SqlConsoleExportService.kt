package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.platform.ui.model.SqlConsoleQueryResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleShardResultResponse
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SqlConsoleExportService {

    fun exportShardCsv(
        result: SqlConsoleQueryResponse,
        shardName: String,
    ): ByteArray {
        require(result.statementType == "RESULT_SET") {
            "CSV-экспорт доступен только для SELECT-результатов."
        }
        val shard = successfulShards(result).firstOrNull { it.shardName == shardName }
            ?: throw SqlConsoleShardResultNotFoundException(shardName)
        return buildCsv(shard).toByteArray(StandardCharsets.UTF_8)
    }

    fun exportAllZip(result: SqlConsoleQueryResponse): ByteArray {
        require(result.statementType == "RESULT_SET") {
            "ZIP-экспорт доступен только для SELECT-результатов."
        }
        val shards = successfulShards(result)
        require(shards.isNotEmpty()) {
            "Нет успешных source-результатов для экспорта."
        }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            shards.forEach { shard ->
                val entryName = "${sanitizeFileName(shard.shardName)}.csv"
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(buildCsv(shard).toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun successfulShards(result: SqlConsoleQueryResponse): List<SqlConsoleShardResultResponse> =
        result.shardResults.filter { it.status == "SUCCESS" }

    private fun buildCsv(shard: SqlConsoleShardResultResponse): String {
        if (shard.columns.isEmpty()) {
            return ""
        }
        val builder = StringBuilder()
        builder.append(shard.columns.joinToString(",") { escapeCsv(it) }).append('\n')
        shard.rows.forEach { row ->
            builder.append(
                shard.columns.joinToString(",") { column ->
                    escapeCsv(row[column].orEmpty())
                }
            ).append('\n')
        }
        return builder.toString()
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("""[^\w.-]+"""), "_").ifBlank { "source" }
}
