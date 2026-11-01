package com.kurtz.homerecord.core.log

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

// R09d: the Log tab's "today" moves on at midnight while it is open (AC-F30)
@OptIn(ExperimentalCoroutinesApi::class)
class MidnightTest {
    private val ny = ZoneId.of("America/New_York")

    @Test fun waitsUntilTheNextStartOfDay() { // AC-F30
        val now = LocalDate.of(2026, 10, 30).atTime(23, 30).atZone(ny).toInstant()
        assertThat(Midnight.millisUntilNext(now, ny)).isEqualTo(30 * 60_000L)
    }

    @Test fun aDaylightSavingDayIsAsLongAsTheZoneSays() { // AC-F30
        // clocks go back at 2:00 on Nov 1 2026: from 0:00 that day to midnight is 25 hours
        val start = LocalDate.of(2026, 11, 1).atStartOfDay(ny).toInstant()
        assertThat(Midnight.millisUntilNext(start, ny)).isEqualTo(25 * 3_600_000L)
    }

    @Test fun ticksJustAfterEachMidnight() = runTest { // AC-F30
        val origin = LocalDate.of(2026, 10, 30).atTime(22, 0).atZone(ny).toInstant()
        val seen = mutableListOf<LocalDate>()
        Midnight.ticks(now = { origin.plusMillis(currentTime) }, zone = { ny }).take(2).collect {
            seen += Instant.ofEpochMilli(origin.toEpochMilli() + currentTime).atZone(ny).toLocalDate()
        }
        assertThat(seen).containsExactly(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1)).inOrder()
    }
}
