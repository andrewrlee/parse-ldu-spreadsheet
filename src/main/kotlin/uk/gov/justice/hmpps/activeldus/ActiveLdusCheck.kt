package uk.gov.justice.hmpps.activeldus

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.FileInputStream

object ActiveLdusCheck {
    fun cellValue(row: Row, cellIndex: Int): String = row.getCell(cellIndex).stringCellValue.trim()

    fun readSheet(filepath: String, sheetName: String) {
        val inputStream = FileInputStream(filepath)
        val xlWb = WorkbookFactory.create(inputStream)

        val sheet = xlWb.getSheet(sheetName)

        val set = HashSet<String>()
        sheet.rowIterator().asSequence()
                .drop(1)
                .takeWhile { it.getCell(0)?.stringCellValue?.isNotEmpty() ?: false }
                .map { Pair(cellValue(it, 1), cellValue(it, 4)) }
                .distinct()
                .forEach { (probationCode, lduCode) -> run {
                    if (set.contains(lduCode)) {
                        println("$probationCode, $lduCode")
                    }
                    set.add(lduCode)
                } }
    }
}

fun main(args: Array<String>) {
    ActiveLdusCheck.readSheet("src/main/resources/confirmed nps crc mailboxes.xlsx",
            "Confirmed NE CRC FMBs")
    ActiveLdusCheck.readSheet("src/main/resources/confirmed nps crc mailboxes.xlsx",
            "Confirmed NE NPS FMBs")
}
