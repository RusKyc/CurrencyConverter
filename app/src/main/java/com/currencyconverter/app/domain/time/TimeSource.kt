package com.currencyconverter.app.domain.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Emits "now" periodically so relative timestamps ("5 min ago") stay current while visible. */
interface TimeSource {
    fun ticks(): Flow<Instant>
}

class SystemTimeSource @Inject constructor(
    private val clock: Clock,
) : TimeSource {
    override fun ticks(): Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
    }
}
