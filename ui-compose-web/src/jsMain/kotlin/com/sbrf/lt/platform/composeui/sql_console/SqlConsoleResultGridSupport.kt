package com.sbrf.lt.platform.composeui.sql_console

internal const val SQL_RESULT_GRID_MIN_WIDTH_PX = 140
internal const val SQL_RESULT_GRID_MAX_WIDTH_PX = 420
private const val SQL_RESULT_GRID_CHAR_WIDTH_PX = 8
private const val SQL_RESULT_GRID_BASE_WIDTH_PX = 44
private const val SQL_RESULT_GRID_SAMPLE_SIZE = 40

internal data class SqlResultGridSelection(
    val absoluteRowIndex: Int,
    val columnName: String,
)

internal fun buildSqlResultGridInitialColumnWidths(
    columns: List<String>,
    rows: List<Map<String, String?>>,
): Map<String, Int> =
    columns.associateWith { columnName ->
        calculateSqlResultGridColumnWidthPx(
            header = columnName,
            values = rows.asSequence().map { row -> row[columnName].orEmpty() },
        )
    }

internal fun calculateSqlResultGridColumnWidthPx(
    header: String,
    values: Sequence<String>,
): Int {
    val maxVisibleLength = sequenceOf(header)
        .plus(values.take(SQL_RESULT_GRID_SAMPLE_SIZE).map(::normalizeSqlResultGridValue))
        .map { it.length }
        .maxOrNull()
        ?: 0
    val estimatedWidth = SQL_RESULT_GRID_BASE_WIDTH_PX + maxVisibleLength * SQL_RESULT_GRID_CHAR_WIDTH_PX
    return estimatedWidth.coerceIn(SQL_RESULT_GRID_MIN_WIDTH_PX, SQL_RESULT_GRID_MAX_WIDTH_PX)
}

internal fun growSqlResultGridColumnWidth(currentWidth: Int): Int =
    (currentWidth + 32).coerceAtMost(SQL_RESULT_GRID_MAX_WIDTH_PX)

internal fun shrinkSqlResultGridColumnWidth(currentWidth: Int): Int =
    (currentWidth - 32).coerceAtLeast(SQL_RESULT_GRID_MIN_WIDTH_PX)

internal fun buildSqlResultGridCellCopyValue(value: String?): String =
    value.orEmpty()

internal fun buildSqlResultGridRowCopyValue(
    columns: List<String>,
    row: Map<String, String?>,
): String =
    columns.joinToString("\t") { columnName ->
        normalizeSqlResultGridClipboardValue(row[columnName])
    }

internal fun buildSqlResultGridColumnCopyValue(
    columnName: String,
    rows: List<Map<String, String?>>,
): String =
    buildList {
        add(columnName)
        rows.forEach { row ->
            add(normalizeSqlResultGridClipboardValue(row[columnName]))
        }
    }.joinToString("\n")

private fun normalizeSqlResultGridClipboardValue(value: String?): String =
    normalizeSqlResultGridValue(value.orEmpty()).replace("\t", "    ")

private fun normalizeSqlResultGridValue(value: String): String =
    value.replace("\r\n", "\n").replace('\r', '\n')
