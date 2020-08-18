package uk.gov.justice.hmpps.paupdate

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import uk.gov.justice.hmpps.paupdate.SpreadsheetReader.mapToNewLdus
import uk.gov.justice.hmpps.paupdate.SpreadsheetReader.parseJson
import uk.gov.justice.hmpps.paupdate.SpreadsheetReader.readSheets
import uk.gov.justice.hmpps.paupdate.SpreadsheetReader.toCommands
import uk.gov.justice.hmpps.paupdate.SpreadsheetReader.toLdus
import java.io.FileInputStream
import java.io.InputStream
import java.util.*


data class TeamSpec(
    val oldProbationAreaCode: String,
    val oldLduCode: String,
    val oldTeamCode: String,
    val newProbationAreaCode: String,
    val newLduCode: String,
    val newTeamCode: String)

data class Ldu(
    val probationAreaCode: String,
    val lduCode: String,
    val fmb: String?,
    val teams: List<Team>,
    val foundMatch: Boolean = true
)

data class Team(
    val teamCode: String,
    val fmb: String,
    val foundMatch: Boolean = true
)

val sheets = listOf(
    Pair("src/main/resources/N54 v1.2.xlsx", "N54 Team"),
    Pair("src/main/resources/N55 v1.2.xlsx", "N55 Team")
)

val jsonFilename = "src/main/resources/N02.json"

object SpreadsheetReader {
  fun readSheets(fileName: String, sheetName: String): Sequence<TeamSpec> =
      readSheetRows(FileInputStream(fileName), sheetName)


  private fun readSheetRows(input: InputStream, sheetName: String): Sequence<TeamSpec> = WorkbookFactory
      .create(input)
      .getSheet(sheetName)
      .rowIterator()
      .asSequence()
      .drop(1)
      .takeWhile { it.getCell(0)?.stringCellValue?.isNotEmpty() ?: false }
      .map(::toTeamSpec)

  private fun toTeamSpec(row: Row) = TeamSpec(
      row.text(0).trim().toUpperCase(),
      row.text(7).trim().toUpperCase(),
      row.text(14).trim().toUpperCase(),
      row.text(1).trim().toUpperCase(),
      row.text(8).trim().toUpperCase(),
      row.text(15).trim().toUpperCase()
  )

  private fun Row.text(index: Int) = this.getCell(index).stringCellValue.trim()

  fun parseJson(): JsonObject = Parser.default().parse(FileInputStream(jsonFilename)) as JsonObject

  fun toOptionalLduFmb(jsonObject: JsonObject?): Optional<Ldu> {
    if (jsonObject == null) return Optional.empty()
    val probationAreaCode = jsonObject.string("probationAreaCode")
    val lduCode = jsonObject.string("localDeliveryUnitCode")
    if (probationAreaCode == null || lduCode == null) return Optional.empty()

    val fmb = jsonObject.string("functionalMailbox")
    val probationTeams = toProbationTeams(jsonObject.obj("probationTeams"))

    return Optional.of(Ldu(probationAreaCode, lduCode, fmb, probationTeams))
  }

  private fun toProbationTeams(jsonObject: JsonObject?): List<Team> {
    if (jsonObject == null) return listOf()
    return jsonObject
        .entries
        .map { (key, value) ->
          val o = value as JsonObject
          val fmb = o.string("functionalMailbox")
          if (fmb == null)
            Optional.empty<Team>()
          else Optional.of(Team(key, fmb))
        }
        .filter { it.isPresent }
        .map { it.get() }
  }

  fun toLdus(jsonObject: JsonObject): List<Ldu> {
    val ldus = jsonObject.obj("localDeliveryUnits") ?: return listOf()
    return ldus
        .values
        .map { toOptionalLduFmb(it as JsonObject) }
        .filter { it.isPresent }
        .map { it.get() }
  }

  fun mapToNewLdus(ldus: List<Ldu>, teamSpecs: List<TeamSpec>): List<Ldu> =
      ldus
          .flatMap { ldu ->
            val specGroups = teamSpecs.filter {
              it.oldProbationAreaCode == ldu.probationAreaCode &&
                  it.oldLduCode == ldu.lduCode
            }.groupBy { it.newProbationAreaCode }
            specGroups
                .map { (_, teams) ->
                  val team = teams.first()
                  Ldu(
                      team.newProbationAreaCode,
                      team.newLduCode,
                      ldu.fmb, // could be null if this LDU contains Team FMBs
                      mapToNewTeams(ldu, teams)
                  )
                }.
                ifEmpty {
                  listOf(Ldu(ldu.probationAreaCode, ldu.lduCode, ldu.fmb, listOf(), false))
                }
          }


  private fun mapToNewTeams(ldu: Ldu, teamSpecs: List<TeamSpec>): List<Team> =
      ldu
          .teams
          .map { team ->
            val spec = Optional.ofNullable(teamSpecs.find {
              it.oldProbationAreaCode == ldu.probationAreaCode &&
                  it.oldLduCode == ldu.lduCode &&
                  it.oldTeamCode == team.teamCode
            })
            spec
                .map { Team(it.newTeamCode, team.fmb) }
                .orElse(Team(team.teamCode, team.fmb, false))
          }

  fun toCommands(newLdus: List<Ldu>, env: String): List<String> =
      newLdus
          .filter { it.fmb != null && it.foundMatch }
          .map {
            "./probation-teams.sh -ns ${env} -pa ${it.probationAreaCode} -ldu ${it.lduCode} -update ${it.fmb}"
          } + newLdus
          .flatMap { ldu ->
            ldu.teams
                .filter { it.foundMatch }
                .map {
                  "./probation-teams.sh -ns ${env} -pa ${ldu.probationAreaCode} -ldu ${ldu.lduCode} -team ${it.teamCode} -update ${it.fmb}"
                }
          }

}


fun main() {
  val teamSpecs = sheets.flatMap { readSheets(it.first, it.second) }
  val ldus = toLdus(parseJson())
  val newLdus = mapToNewLdus(ldus, teamSpecs)

  toCommands(newLdus, "dev").forEach(::println)

  println()
  println("Found ${ldus.filter { it.fmb != null }.size} LDU FMBs, mapped to ${newLdus.filter { it.fmb != null && it.foundMatch }.size}")
  println("Found ${ldus.flatMap { it.teams }.size} Team FMBs, mapped to ${newLdus.flatMap { it.teams.filter { it.foundMatch } }.size}")
  println()

  newLdus
      .filter { !it.foundMatch }
      .map { "No match for LDU ${it.probationAreaCode}/${it.lduCode} ${it.fmb}" }
      .forEach(::println)

  newLdus
      .flatMap { ldu ->
        ldu.teams
            .filter { !it.foundMatch }
            .map { "No match for Team code ${it.teamCode} in ${ldu.probationAreaCode}/${ldu.lduCode}  ${it.fmb}" }
      }
      .forEach(::println)
}


