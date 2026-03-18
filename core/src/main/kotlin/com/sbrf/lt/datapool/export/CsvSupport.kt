package com.sbrf.lt.datapool.export

import org.apache.commons.csv.CSVFormat

object CsvSupport {
    val format: CSVFormat = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(false)
        .get()

    val formatWithoutAutoHeader: CSVFormat = CSVFormat.DEFAULT.builder()
        .setSkipHeaderRecord(false)
        .get()
}
