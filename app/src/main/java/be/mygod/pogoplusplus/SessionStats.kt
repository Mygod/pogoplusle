package be.mygod.pogoplusplus

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Formats the numbers the connection notification shows. [delta] marks what changed: -1 for a capture, -2 for an
 * escape, or the item count of a spin.
 */
internal fun formatStats(caught: Long, encounters: Long, spins: Long, items: Long, delta: Long = 0) =
    StringBuilder("🔮 ").apply {
        append(caught)
        if (delta == -1L) append('⁺')
        append("　🤾 ")
        append(encounters)
        if (delta == -2L) append('⁺')
        append("　💫 ")
        append(spins)
        append("　🎒 ")
        append(items)
        if (delta > 0) {
            append('⁺')
            for (char in delta.toString()) append("⁰¹²³⁴⁵⁶⁷⁸⁹"[char.digitToInt()])
        }
    }.toString()

internal data class SessionTotals(
    val sessions: Int,
    val duration: Long,
    val caught: Long,
    val fled: Long,
    val spins: Long,
    val items: Long,
    val itemFull: Long,
    val pokemonFull: Long,
    val noBall: Long,
    val spinFail: Long,
) {
    val encounters get() = caught + fled
    val stats get() = formatStats(caught, encounters, spins, items)
    val catchRate get() = if (encounters > 0) caught.toDouble() / encounters else null
    val itemsPerSpin get() = if (spins > 0) items.toDouble() / spins else null
    /** Hourly numbers in the format of [stats], or null when too little time passed for a meaningful rate. */
    val hourlyStats get() = if (duration < 60_000) null else {
        fun perHour(count: Long) = Math.round(count * 3_600_000.0 / duration)
        formatStats(perHour(caught), perHour(encounters), perHour(spins), perHour(items))
    }
}

internal fun Iterable<SfidaSession>.totals(): SessionTotals {
    var sessions = 0
    var duration = 0L
    var caught = 0L
    var fled = 0L
    var spins = 0L
    var items = 0L
    var itemFull = 0L
    var pokemonFull = 0L
    var noBall = 0L
    var spinFail = 0L
    for (session in this) {
        ++sessions
        duration += session.duration
        caught += session.caught
        fled += session.fled
        spins += session.spins
        items += session.items
        itemFull += session.itemFull
        pokemonFull += session.pokemonFull
        noBall += session.noBall
        spinFail += session.spinFail
    }
    return SessionTotals(sessions, duration, caught, fled, spins, items, itemFull, pokemonFull, noBall, spinFail)
}

/**
 * Groups sessions by the date they started on in their own time zone, newest date first, keeping the order of [this]
 * within each date. Dates need sorting since travel can put a later session on an earlier date.
 */
internal fun List<SfidaSession>.groupByDay(): Map<LocalDate, List<SfidaSession>> =
    groupBy { Instant.ofEpochMilli(it.startTime).atZone(it.zone).toLocalDate() }.toSortedMap(reverseOrder())

/**
 * Labels accessories by their latest name, adding the end of the address when names collide, ordered by their latest
 * session when [this] is newest first.
 */
internal fun List<SfidaSession>.deviceLabels(): Map<String, String> {
    val names = groupBy { it.deviceAddress }.mapValues { (_, sessions) ->
        sessions.firstNotNullOfOrNull { it.deviceName } ?: "❓"
    }
    fun Map<String, String>.disambiguate(suffix: (String) -> String): Map<String, String> {
        val counts = values.groupingBy { it }.eachCount()
        return mapValues { (address, label) ->
            if (counts.getValue(label) > 1 && address.isNotEmpty()) "${names.getValue(address)} (${suffix(address)})"
            else label
        }
    }
    // the whole address only when its end is not enough
    return names.disambiguate { it.takeLast(5) }.disambiguate { it }
}

private val csvColumns = listOf(
    "start", "end", "time_zone", "duration_seconds", "device_address", "device_name", "caught", "fled", "encounters",
    "spins", "items", "bag_full", "pokemon_storage_full", "out_of_poke_balls", "spin_failed",
)

private fun Appendable.appendCsvRow(fields: List<Any?>) {
    for ((i, field) in fields.withIndex()) {
        if (i > 0) append(',')
        val text = field?.toString() ?: ""
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            append('"').append(text.replace("\"", "\"\"")).append('"')
        } else append(text)
    }
    append("\r\n")
}

/**
 * Writes RFC 4180 CSV with ISO 8601 times in the time zone of each session, in whole seconds for spreadsheets to
 * parse.
 */
internal fun Appendable.appendSessionsCsv(sessions: Iterable<SfidaSession>) {
    fun SfidaSession.format(time: Long) = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        Instant.ofEpochMilli(time).truncatedTo(ChronoUnit.SECONDS).atZone(zone))
    appendCsvRow(csvColumns)
    for (session in sessions) appendCsvRow(listOf(
        session.format(session.startTime), session.format(session.endTime), session.zone.id, session.duration / 1000,
        session.deviceAddress,
        session.deviceName, session.caught, session.fled, session.encounters, session.spins, session.items,
        session.itemFull, session.pokemonFull, session.noBall, session.spinFail,
    ))
}
