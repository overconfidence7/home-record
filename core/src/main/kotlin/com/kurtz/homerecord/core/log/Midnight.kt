package com.kurtz.homerecord.core.log

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** When the calendar day turns, so a screen left open overnight moves its "today" on (R09d). */
object Midnight {
    /** Millis from [now] to the next start of day in [zone]; a daylight-saving day is as long as the zone says. */
    fun millisUntilNext(now: Instant, zone: ZoneId): Long {
        val next = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return Duration.between(now, next).toMillis().coerceAtLeast(1)
    }

    /**
     * Emits a second after every local midnight for as long as it is collected. A phone asleep over midnight emits late
     * (the wait does not count deep sleep), so screens also check the day when they come back to the front.
     */
    fun ticks(now: () -> Instant = Instant::now, zone: () -> ZoneId = ZoneId::systemDefault): Flow<Unit> = flow {
        while (true) {
            delay(millisUntilNext(now(), zone()) + SETTLE_MS)
            emit(Unit)
        }
    }

    private const val SETTLE_MS = 1_000L
}
