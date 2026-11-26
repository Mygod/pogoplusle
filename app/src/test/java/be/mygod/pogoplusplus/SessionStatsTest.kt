package be.mygod.pogoplusplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SessionStatsTest {
    private fun session(
        startTime: Long,
        endTime: Long = startTime,
        zone: ZoneId = ZoneOffset.UTC,
        deviceAddress: String = "",
        deviceName: String? = null,
        caught: Long = 0,
        fled: Long = 0,
        spins: Long = 0,
        items: Long = 0,
        itemFull: Long = 0,
    ) = SfidaSession(startTime, endTime, zone.id, deviceAddress, deviceName, caught, fled, spins, items, itemFull, 0, 0,
        0)

    @Test
    fun statsMatchNotificationFormat() {
        assertEquals("🔮 3　🤾 4　💫 5　🎒 12", formatStats(3, 4, 5, 12))
        assertEquals("🔮 3⁺　🤾 4　💫 5　🎒 12", formatStats(3, 4, 5, 12, -1))
        assertEquals("🔮 3　🤾 4⁺　💫 5　🎒 12", formatStats(3, 4, 5, 12, -2))
        assertEquals("🔮 3　🤾 4　💫 5　🎒 12⁺¹⁰", formatStats(3, 4, 5, 12, 10))
    }

    @Test
    fun totalsSumSessionsAndDeriveRates() {
        val totals = listOf(
            session(0, 1_800_000, caught = 9, fled = 1, spins = 4, items = 10, itemFull = 2),
            session(2_000_000, 3_800_000, caught = 3, fled = 3, spins = 6, items = 20),
        ).totals()
        assertEquals(2, totals.sessions)
        assertEquals(3_600_000, totals.duration)
        assertEquals("🔮 12　🤾 16　💫 10　🎒 30", totals.stats)
        assertEquals(0.75, totals.catchRate!!, 0.0)
        assertEquals(3.0, totals.itemsPerSpin!!, 0.0)
        assertEquals("🔮 12　🤾 16　💫 10　🎒 30", totals.hourlyStats)
        assertEquals(2, totals.itemFull)
    }

    @Test
    fun ratesNeedData() {
        val totals = listOf(session(0, 59_999, itemFull = 1)).totals()
        assertNull(totals.catchRate)
        assertNull(totals.itemsPerSpin)
        assertNull(totals.hourlyStats)
    }

    @Test
    fun clockGoingBackAddsNoTime() {
        assertEquals(60_000, listOf(session(0, 60_000), session(120_000, 90_000)).totals().duration)
    }

    @Test
    fun clearingKeepsTheOngoingSessionAndLaterOnes() {
        val pending = SfidaSessionManager.PendingClear(1_000, null, 500, null, emptyMap())
        assertEquals(listOf(true, false, false, false),
            listOf(400L, 500L, 1_000L, 1_500L).map { pending.removes(session(it)) })
        assertEquals(true, SfidaSessionManager.PendingClear(1_000, null, null, 500, emptyMap()).removes(session(500)))
    }

    @Test
    fun sessionsGroupByStartDateInTheirOwnZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val newYork = ZoneId.of("America/New_York")
        fun at(zone: ZoneId, day: Int, hour: Int, minute: Int) =
            ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
        val late = session(at(tokyo, 25, 23, 30), at(tokyo, 26, 0, 45), tokyo)
        val early = session(at(tokyo, 26, 0, 50), zone = tokyo)
        val morning = session(at(tokyo, 26, 9, 0), zone = tokyo)
        // after flying to New York, a later session lands on an earlier date
        val evening = session(at(newYork, 25, 22, 0), zone = newYork)
        val groups = listOf(evening, morning, early, late).groupByDay()
        assertEquals(listOf(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 25)), groups.keys.toList())
        assertEquals(listOf(morning, early), groups[LocalDate.of(2026, 11, 26)])
        assertEquals(listOf(evening, late), groups[LocalDate.of(2026, 11, 25)])
    }

    @Test
    fun unknownZoneFallsBackToCurrentZone() {
        assertEquals(ZoneId.systemDefault(), session(0).copy(zoneId = "Nowhere/Atlantis").zone)
    }

    @Test
    fun deviceLabelsUseLatestNameAndDisambiguate() {
        assertEquals(mapOf(
            "AA:BB:CC:DD:EE:01" to "Pokemon GO Plus + (EE:01)",
            "AA:BB:CC:DD:EE:02" to "Pokemon GO Plus + (EE:02)",
            "AA:BB:CC:DD:EE:03" to "Pokemon GO Plus (AA:BB:CC:DD:EE:03)",
            "11:22:33:44:EE:03" to "Pokemon GO Plus (11:22:33:44:EE:03)",
            "AA:BB:CC:DD:EE:04" to "Pokemon GO Plus (EE:04)",
            "" to "❓",
        ), listOf(
            session(5, deviceAddress = "AA:BB:CC:DD:EE:01", deviceName = null),
            session(4, deviceAddress = "AA:BB:CC:DD:EE:02", deviceName = "Pokemon GO Plus +"),
            session(3, deviceAddress = "AA:BB:CC:DD:EE:01", deviceName = "Pokemon GO Plus +"),
            session(2, deviceAddress = "AA:BB:CC:DD:EE:03", deviceName = "Pokemon GO Plus"),
            // same name and end of address
            session(2, deviceAddress = "11:22:33:44:EE:03", deviceName = "Pokemon GO Plus"),
            session(2, deviceAddress = "AA:BB:CC:DD:EE:04", deviceName = "Pokemon GO Plus"),
            session(1),
        ).deviceLabels())
    }

    @Test
    fun csvUsesIsoSecondsAndQuotesFields() {
        val csv = StringBuilder().apply {
            appendSessionsCsv(listOf(session(1_360, 61_420, ZoneId.of("Asia/Tokyo"), "AA:BB:CC:DD:EE:01", "Plus, \"2\"",
                caught = 1, spins = 2, items = 3)))
        }.toString()
        assertEquals("start,end,time_zone,duration_seconds,device_address,device_name,caught,fled,encounters,spins," +
                "items,bag_full,pokemon_storage_full,out_of_poke_balls,spin_failed\r\n" +
                "1970-01-01T09:00:01+09:00,1970-01-01T09:01:01+09:00,Asia/Tokyo,60,AA:BB:CC:DD:EE:01," +
                "\"Plus, \"\"2\"\"\",1,0,1,2,3,0,0,0,0\r\n",
            csv)
    }
}
