package com.currencyconverter.app.presentation

import com.currencyconverter.app.presentation.common.UpdatedAge
import com.currencyconverter.app.presentation.common.absoluteAge
import com.currencyconverter.app.presentation.common.relativeAge
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class UpdatedAgeTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-09-21T14:40:00Z")

    private fun ago(minutes: Long): Instant = now.minusSeconds(minutes * 60)

    @Test
    fun `under a minute is just now`() {
        assertEquals(UpdatedAge.JustNow, relativeAge(now.minusSeconds(59), now, zone))
        assertEquals(UpdatedAge.JustNow, relativeAge(now, now, zone))
    }

    @Test
    fun `future timestamps from clock skew count as just now`() {
        assertEquals(UpdatedAge.JustNow, relativeAge(now.plusSeconds(300), now, zone))
    }

    @Test
    fun `minutes and hours are counted`() {
        assertEquals(UpdatedAge.Minutes(5), relativeAge(ago(5), now, zone))
        assertEquals(UpdatedAge.Minutes(59), relativeAge(ago(59), now, zone))
        assertEquals(UpdatedAge.Hours(1), relativeAge(ago(60), now, zone))
        assertEquals(UpdatedAge.Hours(5), relativeAge(ago(5 * 60 + 30), now, zone))
    }

    @Test
    fun `older data switches to calendar wording`() {
        assertEquals(UpdatedAge.Today(LocalTime.of(8, 40)), relativeAge(ago(6 * 60), now, zone))
    }

    @Test
    fun `absolute wording distinguishes today yesterday and earlier`() {
        val today = Instant.parse("2026-09-21T14:32:00Z")
        val yesterday = Instant.parse("2026-09-20T23:59:00Z")
        val earlier = Instant.parse("2026-09-01T10:00:00Z")

        assertEquals(UpdatedAge.Today(LocalTime.of(14, 32)), absoluteAge(today, now, zone))
        assertEquals(UpdatedAge.Yesterday(LocalTime.of(23, 59)), absoluteAge(yesterday, now, zone))
        assertEquals(
            UpdatedAge.Earlier(LocalDateTime.of(2026, 9, 1, 10, 0)),
            absoluteAge(earlier, now, zone),
        )
    }
}
