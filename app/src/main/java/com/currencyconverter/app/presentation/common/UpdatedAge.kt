package com.currencyconverter.app.presentation.common

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** How long ago rates were downloaded, in the coarse units the UI can express. */
sealed interface UpdatedAge {
    data object JustNow : UpdatedAge
    data class Minutes(val count: Int) : UpdatedAge
    data class Hours(val count: Int) : UpdatedAge
    data class Today(val time: LocalTime) : UpdatedAge
    data class Yesterday(val time: LocalTime) : UpdatedAge
    data class Earlier(val dateTime: LocalDateTime) : UpdatedAge
}

private const val RELATIVE_LIMIT_HOURS = 6L

/** Relative wording ("5 min ago") for fresh data, calendar wording afterwards. */
fun relativeAge(updatedAt: Instant, now: Instant, zone: ZoneId): UpdatedAge {
    val elapsed = Duration.between(updatedAt, now)
    return when {
        elapsed.isNegative || elapsed.toMinutes() < 1 -> UpdatedAge.JustNow
        elapsed.toMinutes() < 60 -> UpdatedAge.Minutes(elapsed.toMinutes().toInt())
        elapsed.toHours() < RELATIVE_LIMIT_HOURS -> UpdatedAge.Hours(elapsed.toHours().toInt())
        else -> absoluteAge(updatedAt, now, zone)
    }
}

/** Calendar wording ("today at 14:32") used when the data is offline or cached. */
fun absoluteAge(updatedAt: Instant, now: Instant, zone: ZoneId): UpdatedAge {
    val updated = LocalDateTime.ofInstant(updatedAt, zone)
    val today: LocalDate = LocalDateTime.ofInstant(now, zone).toLocalDate()
    return when (updated.toLocalDate()) {
        today -> UpdatedAge.Today(updated.toLocalTime())
        today.minusDays(1) -> UpdatedAge.Yesterday(updated.toLocalTime())
        else -> UpdatedAge.Earlier(updated)
    }
}
