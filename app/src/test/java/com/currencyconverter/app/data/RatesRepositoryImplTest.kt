package com.currencyconverter.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.currencyconverter.app.data.local.AppDatabase
import com.currencyconverter.app.data.repository.RatesRepositoryImpl
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.domain.model.RatesException
import com.currencyconverter.app.domain.model.RefreshResult
import com.currencyconverter.app.testing.FakeRemote
import com.currencyconverter.app.testing.MutableClock
import com.currencyconverter.app.testing.TestNow
import com.currencyconverter.app.testing.remoteRates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Duration

/** Exercises the repository against a real (in-memory) Room database, i.e. the cache as well. */
@RunWith(RobolectricTestRunner::class)
class RatesRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var remote: FakeRemote
    private lateinit var clock: MutableClock
    private lateinit var repository: RatesRepositoryImpl

    private val sixHours = Duration.ofHours(6)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        remote = FakeRemote()
        clock = MutableClock()
        repository = RatesRepositoryImpl(database.ratesDao(), remote, clock, minRequestInterval = Duration.ofSeconds(15))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `empty database emits null`() = runTest {
        assertNull(repository.rates.first())
    }

    @Test
    fun `successful refresh stores rates and publishes them`() = runTest {
        remote.enqueue(remoteRates("USD" to "1.25", "GBP" to "0.8"))

        val result = repository.refresh(force = true, maxAge = sixHours)

        assertEquals(RefreshResult.Updated, result)
        val rates = repository.rates.first()!!
        assertEquals("EUR", rates.base)
        assertEquals(TestNow, rates.fetchedAt)
        assertEquals(0, BigDecimal("1.25").compareTo(rates.rates["USD"]))
        assertEquals(BigDecimal.ONE.compareTo(rates.rates["EUR"]), 0)
    }

    @Test
    fun `exact decimal text survives the round trip through the database`() = runTest {
        remote.enqueue(remoteRates("JPY" to "157.89", "CZK" to "21.238"))
        repository.refresh(force = true, maxAge = null)

        val rates = repository.rates.first()!!
        assertEquals("157.89", rates.rates["JPY"]!!.toPlainString())
        assertEquals("21.238", rates.rates["CZK"]!!.toPlainString())
    }

    @Test
    fun `a new snapshot replaces the old one completely`() = runTest {
        remote.enqueue(remoteRates("USD" to "1.25", "GBP" to "0.8"))
        repository.refresh(force = true, maxAge = null)

        clock.advance(Duration.ofMinutes(1))
        remote.enqueue(remoteRates("USD" to "1.30"))
        repository.refresh(force = true, maxAge = null)

        val rates = repository.rates.first()!!
        assertEquals(0, BigDecimal("1.30").compareTo(rates.rates["USD"]))
        assertFalse("GBP from the old snapshot must be gone", rates.rates.containsKey("GBP"))
        assertEquals(TestNow.plus(Duration.ofMinutes(1)), rates.fetchedAt)
    }

    @Test
    fun `fresh cache is not refreshed again`() = runTest {
        remote.enqueue(remoteRates("USD" to "1.25"))
        repository.refresh(force = false, maxAge = sixHours)

        clock.advance(Duration.ofHours(5))
        val result = repository.refresh(force = false, maxAge = sixHours)

        assertEquals(RefreshResult.UpToDate, result)
        assertEquals(1, remote.requests)
    }

    @Test
    fun `stale cache is refreshed`() = runTest {
        remote.enqueue(remoteRates("USD" to "1.25"))
        repository.refresh(force = false, maxAge = sixHours)

        clock.advance(Duration.ofHours(7))
        remote.enqueue(remoteRates("USD" to "1.30"))
        val result = repository.refresh(force = false, maxAge = sixHours)

        assertEquals(RefreshResult.Updated, result)
        assertEquals(2, remote.requests)
    }

    @Test
    fun `manual only mode still downloads when nothing is cached`() = runTest {
        remote.enqueue(remoteRates("USD" to "1.25"))
        assertEquals(RefreshResult.Updated, repository.refresh(force = false, maxAge = null))
    }

    @Test
    fun `manual only mode never treats cached data as stale`() = runTest {
        remote.enqueue(remoteRates("USD" to "1.25"))
        repository.refresh(force = false, maxAge = null)

        clock.advance(Duration.ofDays(30))
        assertEquals(RefreshResult.UpToDate, repository.refresh(force = false, maxAge = null))
        assertEquals(1, remote.requests)
    }

    @Test
    fun `forced refresh is throttled right after a request reached the server`() = runTest {
        remote.enqueue(remoteRates("USD" to "1.25"))
        repository.refresh(force = true, maxAge = null)

        clock.advance(Duration.ofSeconds(5))
        assertEquals(RefreshResult.Throttled, repository.refresh(force = true, maxAge = null))
        assertEquals(1, remote.requests)

        clock.advance(Duration.ofSeconds(11))
        remote.enqueue(remoteRates("USD" to "1.26"))
        assertEquals(RefreshResult.Updated, repository.refresh(force = true, maxAge = null))
    }

    @Test
    fun `offline failure keeps cached rates and allows an immediate retry`() = runTest {
        remote.enqueue(remoteRates("USD" to "1.25"))
        repository.refresh(force = true, maxAge = null)

        clock.advance(Duration.ofMinutes(1))
        remote.enqueueFailure(RatesException(RatesError.Network))
        val failed = repository.refresh(force = true, maxAge = null)

        assertEquals(RefreshResult.Failed(RatesError.Network), failed)
        assertNotNull("cache must survive a failed refresh", repository.rates.first())
        assertEquals(0, BigDecimal("1.25").compareTo(repository.rates.first()!!.rates["USD"]))

        // The failed attempt never reached the server, so the user may retry right away.
        remote.enqueue(remoteRates("USD" to "1.27"))
        assertEquals(RefreshResult.Updated, repository.refresh(force = true, maxAge = null))
    }

    @Test
    fun `api errors are reported and throttled like any server contact`() = runTest {
        remote.enqueueFailure(RatesException(RatesError.Api(503)))

        assertEquals(RefreshResult.Failed(RatesError.Api(503)), repository.refresh(force = true, maxAge = null))
        assertEquals(RefreshResult.Throttled, repository.refresh(force = true, maxAge = null))
        assertEquals(1, remote.requests)
    }

    @Test
    fun `parsing errors are reported`() = runTest {
        remote.enqueueFailure(RatesException(RatesError.Parsing))
        assertEquals(RefreshResult.Failed(RatesError.Parsing), repository.refresh(force = true, maxAge = null))
    }

    @Test
    fun `unexpected exceptions become unknown errors instead of crashing`() = runTest {
        remote.enqueueCrash(IllegalStateException("boom"))
        assertEquals(RefreshResult.Failed(RatesError.Unknown("boom")), repository.refresh(force = true, maxAge = null))
    }

    @Test
    fun `refreshing flag is reset after success and after failure`() = runTest {
        remote.enqueue(remoteRates("USD" to "1.25"))
        repository.refresh(force = true, maxAge = null)
        assertFalse(repository.isRefreshing.value)

        clock.advance(Duration.ofMinutes(1))
        remote.enqueueFailure(RatesException(RatesError.Network))
        repository.refresh(force = true, maxAge = null)
        assertFalse(repository.isRefreshing.value)
    }

    @Test
    fun `cached rates are available to a brand new repository instance`() = runTest {
        remote.enqueue(remoteRates("USD" to "1.25"))
        repository.refresh(force = true, maxAge = null)

        val afterRestart = RatesRepositoryImpl(database.ratesDao(), FakeRemote(), clock)

        assertEquals(0, BigDecimal("1.25").compareTo(afterRestart.rates.first()!!.rates["USD"]))
        assertEquals(RefreshResult.UpToDate, afterRestart.refresh(force = false, maxAge = sixHours))
    }
}
