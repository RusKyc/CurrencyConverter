package com.currencyconverter.app.data.repository

import com.currencyconverter.app.data.local.RateEntity
import com.currencyconverter.app.data.local.RatesDao
import com.currencyconverter.app.data.remote.RatesRemoteDataSource
import com.currencyconverter.app.domain.model.ExchangeRates
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.domain.model.RatesException
import com.currencyconverter.app.domain.model.RefreshResult
import com.currencyconverter.app.domain.repository.RatesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Offline-first source of truth: the UI only ever reads [rates] from the database; [refresh]
 * downloads a new snapshot and writes it there, which pushes the update to every observer.
 */
class RatesRepositoryImpl(
    private val dao: RatesDao,
    private val remote: RatesRemoteDataSource,
    private val clock: Clock,
    private val minRequestInterval: Duration = DEFAULT_MIN_REQUEST_INTERVAL,
) : RatesRepository {

    private val mutex = Mutex()
    private var lastServerAttempt: Instant? = null
    private val _isRefreshing = MutableStateFlow(false)

    override val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    override val rates: Flow<ExchangeRates?> = dao.observeAll()
        .map { it.toDomain() }
        .distinctUntilChanged()

    override suspend fun refresh(force: Boolean, maxAge: Duration?): RefreshResult = mutex.withLock {
        val now = clock.instant()

        if (!force) {
            val cachedAt = dao.getFetchedAtMillis()?.let(Instant::ofEpochMilli)
            if (cachedAt != null && (maxAge == null || Duration.between(cachedAt, now) < maxAge)) {
                return RefreshResult.UpToDate
            }
        }

        val previousAttempt = lastServerAttempt
        if (previousAttempt != null && Duration.between(previousAttempt, now) < minRequestInterval) {
            return RefreshResult.Throttled
        }

        _isRefreshing.value = true
        try {
            val downloaded = remote.fetchLatest(BASE_CURRENCY)
            // Offline failures never reached the server, so they must not block an immediate retry.
            lastServerAttempt = now
            val snapshot = ExchangeRates(
                base = downloaded.base,
                quotes = downloaded.quotes,
                providerDate = downloaded.date,
                fetchedAt = now,
            )
            dao.replaceAll(snapshot.toEntities())
            RefreshResult.Updated
        } catch (e: CancellationException) {
            throw e
        } catch (e: RatesException) {
            if (e.error != RatesError.Network) lastServerAttempt = now
            RefreshResult.Failed(e.error)
        } catch (e: Exception) {
            RefreshResult.Failed(RatesError.Unknown(e.message))
        } finally {
            _isRefreshing.value = false
        }
    }

    private fun ExchangeRates.toEntities(): List<RateEntity> = rates.map { (code, rate) ->
        RateEntity(
            code = code,
            rate = rate.toPlainString(),
            baseCode = base,
            providerDate = providerDate?.toString(),
            fetchedAtMillis = fetchedAt.toEpochMilli(),
        )
    }

    private fun List<RateEntity>.toDomain(): ExchangeRates? {
        val first = firstOrNull() ?: return null
        val quotes = mutableMapOf<String, BigDecimal>()
        for (row in this) {
            // A corrupted row is skipped rather than crashing the whole screen.
            row.rate.toBigDecimalOrNull()?.let { quotes[row.code] = it }
        }
        if (quotes.isEmpty()) return null
        return ExchangeRates(
            base = first.baseCode,
            quotes = quotes,
            providerDate = first.providerDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            fetchedAt = Instant.ofEpochMilli(first.fetchedAtMillis),
        )
    }

    companion object {
        /** Rates are downloaded against the ECB's native base; every other pair is derived locally. */
        const val BASE_CURRENCY = "EUR"

        val DEFAULT_MIN_REQUEST_INTERVAL: Duration = Duration.ofSeconds(15)
    }
}
