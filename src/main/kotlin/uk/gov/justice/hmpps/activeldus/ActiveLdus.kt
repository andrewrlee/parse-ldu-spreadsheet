package uk.gov.justice.hmpps.activeldus

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.FileInputStream

object ActiveLdus {
    fun cellValue(row: Row, cellIndex: Int): String = row.getCell(cellIndex).stringCellValue.trim()

    fun readSheet(filepath: String, sheetName: String) {
        val inputStream = FileInputStream(filepath)
        val xlWb = WorkbookFactory.create(inputStream)

        val sheet = xlWb.getSheet(sheetName)

        sheet.rowIterator().asSequence()
                .drop(1)
                .takeWhile { it.getCell(0)?.stringCellValue?.isNotEmpty() ?: false }
                .map { Pair(cellValue(it, 1), cellValue(it, 4)) }
                .distinct()
                .map { (probationCode, lduCode) -> "{ldu_code: '$lduCode', probation_area_code: '$probationCode'}, " }
                .forEach { println(it) }
    }
}

fun main(args: Array<String>) {
    println("knex('active_local_delivery_units').insert([")
    ActiveLdus.readSheet("src/main/resources/confirmed nps crc mailboxes.xlsx",
            "Confirmed NE CRC FMBs")
    ActiveLdus.readSheet("src/main/resources/confirmed nps crc mailboxes.xlsx",
            "Confirmed NE NPS FMBs")
    println("])")
}
