package uk.gov.justice.hmpps.activeldus

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.FileInputStream

data class Team(val ldu: Ldu, val teamCode: String, val functionalMailbox: String)
data class TeamMailbox(val mailbox: String, val freq: Int, val teams: List<Team>)
data class Ldu(val probationCode: String, val lduCode: String)

object FunctionalMailboxes {

    fun cellValue(row: Row, cellIndex: Int): String = row.getCell(cellIndex).stringCellValue.trim()
    fun toTeam(row: Row): Team = Team(Ldu(cellValue(row, 1), cellValue(row, 4)), cellValue(row, 8), cellValue(row, 10))

    fun toEmailFreqs(ldu: Ldu, functionalMailboxToTeams: Map<String, List<Team>>): Pair<Ldu, List<TeamMailbox>> {
        return Pair(ldu, functionalMailboxToTeams
                .mapValues { it.value }
                .map { TeamMailbox(it.key, it.value.size, it.value) })
    }

    fun readSheet(filepath: String, sheetName: String) {
        val inputStream = FileInputStream(filepath)
        val xlWb = WorkbookFactory.create(inputStream)

        val sheet = xlWb.getSheet(sheetName)

        sheet.rowIterator().asSequence()
                .drop(1)
                .takeWhile { it.getCell(0)?.stringCellValue?.isNotEmpty() ?: false }
                .map(::toTeam)
                .groupBy { it.ldu }
                .map{ (ldu, teams) -> Pair(ldu, teams.groupBy { it.functionalMailbox.toLowerCase() })}
                .map { (ldu, functionalMailBoxes) -> toEmailFreqs(ldu, functionalMailBoxes)}
                .forEach { ( ldu, teamMailboxes) ->
                    println(ldu.probationCode + ": " + ldu.lduCode)
                    teamMailboxes.forEach{
                        val mailbox = if (it.mailbox.isBlank()) "MISSING" else it.mailbox
                        println("\t'" + mailbox + "': " + it.freq)
                    }
                    println()
                }
    }
}


fun main(args: Array<String>) {
    FunctionalMailboxes.readSheet("src/main/resources/confirmed nps crc mailboxes.xlsx",
            "Confirmed NE CRC FMBs")
    FunctionalMailboxes.readSheet("src/main/resources/confirmed nps crc mailboxes.xlsx",
            "Confirmed NE NPS FMBs")
}