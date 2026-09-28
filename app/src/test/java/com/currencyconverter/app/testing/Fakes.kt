package com.currencyconverter.app.testing

import com.currencyconverter.app.data.remote.RatesRemoteDataSource
import com.currencyconverter.app.data.remote.RemoteRates
import com.currencyconverter.app.domain.model.AppSettings
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.ExchangeRates
import com.currencyconverter.app.domain.model.RatesException
import com.currencyconverter.app.domain.model.RefreshInterval
import com.currencyconverter.app.domain.model.RefreshResult
import com.currencyconverter.app.domain.model.ThemeMode
import com.currencyconverter.app.domain.repository.FavoritesRepository
import com.currencyconverter.app.domain.repository.NetworkMonitor
import com.currencyconverter.app.domain.repository.RatesRepository
import com.currencyconverter.app.domain.repository.SettingsRepository
import com.currencyconverter.app.domain.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

val TestNow: Instant = Instant.parse("2026-09-21T12:00:00Z")

/** EUR-based snapshot with exactly representable numbers so expectations are easy to verify. */
fun sampleRates(fetchedAt: Instant = TestNow): ExchangeRates = ExchangeRates(
    base = "EUR",
    quotes = mapOf(
        "USD" to BigDecimal("1.25"),
        "GBP" to BigDecimal("0.8"),
        "JPY" to BigDecimal("160"),
    ),
    providerDate = null,
    fetchedAt = fetchedAt,
)

class MutableClock(var now: Instant = TestNow) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

class FixedTimeSource(private val now: Instant = TestNow) : TimeSource {
    override fun ticks(): Flow<Instant> = flowOf(now)
}

class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    val state = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = state
}

class FakeRatesRepository(initial: ExchangeRates? = null) : RatesRepository {
    val stored = MutableStateFlow(initial)
    val refreshing = MutableStateFlow(false)
    var nextResult: RefreshResult = RefreshResult.UpToDate
    val calls = mutableListOf<Pair<Boolean, Duration?>>()

    override val rates: Flow<ExchangeRates?> = stored
    override val isRefreshing: StateFlow<Boolean> = refreshing.asStateFlow()

    override suspend fun refresh(force: Boolean, maxAge: Duration?): RefreshResult {
        calls += force to maxAge
        return nextResult
    }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
    initialPair: CurrencyPair = CurrencyPair("USD", "EUR"),
) : SettingsRepository {
    val current = MutableStateFlow(initial)
    val pair = MutableStateFlow(initialPair)

    override val settings: Flow<AppSettings> = current
    override val converterPair: Flow<CurrencyPair> = pair

    override suspend fun setDefaultBase(code: String) = current.update { it.copy(defaultBase = code) }

    override suspend fun setThemeMode(mode: ThemeMode) = current.update { it.copy(themeMode = mode) }

    override suspend fun setRefreshInterval(interval: RefreshInterval) =
        current.update { it.copy(refreshInterval = interval) }

    override suspend fun setDecimalPlaces(places: DecimalPlaces) = current.update { it.copy(decimalPlaces = places) }

    /** Simulates slow persistence so racing read-modify-write sequences would interleave. */
    var writeDelayMs: Long = 0

    override suspend fun setConverterPair(pair: CurrencyPair) {
        if (writeDelayMs > 0) kotlinx.coroutines.delay(writeDelayMs)
        this.pair.value = pair
    }

    private fun MutableStateFlow<AppSettings>.update(transform: (AppSettings) -> AppSettings) {
        value = transform(value)
    }
}

class FakeFavoritesRepository(initial: List<CurrencyPair> = emptyList()) : FavoritesRepository {
    val stored = MutableStateFlow(initial)
    override val favorites: Flow<List<CurrencyPair>> = stored.map { it }

    override suspend fun add(pair: CurrencyPair) {
        if (pair !in stored.value) stored.value = stored.value + pair
    }

    override suspend fun remove(pair: CurrencyPair) {
        stored.value = stored.value - pair
    }

    override suspend fun setOrder(pairs: List<CurrencyPair>) {
        stored.value = pairs
    }
}

/** Scriptable remote: each call takes the next queued outcome and counts requests. */
class FakeRemote : RatesRemoteDataSource {
    private val outcomes = ArrayDeque<() -> RemoteRates>()
    var requests = 0
        private set

    fun enqueue(rates: RemoteRates) {
        outcomes += { rates }
    }

    fun enqueueFailure(exception: RatesException) {
        outcomes += { throw exception }
    }

    fun enqueueCrash(throwable: RuntimeException) {
        outcomes += { throw throwable }
    }

    override suspend fun fetchLatest(base: String): RemoteRates {
        requests++
        return (outcomes.removeFirstOrNull() ?: error("No outcome queued")).invoke()
    }
}

fun remoteRates(vararg quotes: Pair<String, String>) = RemoteRates(
    base = "EUR",
    date = null,
    quotes = quotes.associate { (code, rate) -> code to BigDecimal(rate) },
)
