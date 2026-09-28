package com.currencyconverter.app.domain

import com.currencyconverter.app.domain.currency.CurrencyCatalog
import com.currencyconverter.app.domain.model.AppSettings
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.RefreshInterval
import com.currencyconverter.app.domain.model.RefreshResult
import com.currencyconverter.app.domain.usecase.AddFavoriteUseCase
import com.currencyconverter.app.domain.usecase.MoveFavoriteUseCase
import com.currencyconverter.app.domain.usecase.MoveFavoriteUseCase.Direction
import com.currencyconverter.app.domain.usecase.RefreshRatesUseCase
import com.currencyconverter.app.domain.usecase.SearchCurrenciesUseCase
import com.currencyconverter.app.domain.usecase.SetDefaultBaseCurrencyUseCase
import com.currencyconverter.app.domain.usecase.ToggleFavoriteUseCase
import com.currencyconverter.app.testing.FakeFavoritesRepository
import com.currencyconverter.app.testing.FakeRatesRepository
import com.currencyconverter.app.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.util.Locale

class UseCasesTest {

    private val usdEur = CurrencyPair("USD", "EUR")
    private val eurGbp = CurrencyPair("EUR", "GBP")
    private val usdJpy = CurrencyPair("USD", "JPY")

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `toggle adds a missing pair and removes an existing one`() = runTest {
        val repository = FakeFavoritesRepository()
        val toggle = ToggleFavoriteUseCase(repository)

        toggle(usdEur)
        assertEquals(listOf(usdEur), repository.stored.value)

        toggle(usdEur)
        assertTrue(repository.stored.value.isEmpty())
    }

    @Test
    fun `add ignores a pair with identical currencies`() = runTest {
        val repository = FakeFavoritesRepository()
        AddFavoriteUseCase(repository)(CurrencyPair("USD", "USD"))
        assertTrue(repository.stored.value.isEmpty())
    }

    @Test
    fun `move swaps neighbours`() = runTest {
        val repository = FakeFavoritesRepository(listOf(usdEur, eurGbp, usdJpy))
        val move = MoveFavoriteUseCase(repository)

        move(usdJpy, Direction.Up)
        assertEquals(listOf(usdEur, usdJpy, eurGbp), repository.stored.value)

        move(usdEur, Direction.Down)
        assertEquals(listOf(usdJpy, usdEur, eurGbp), repository.stored.value)
    }

    @Test
    fun `move does nothing at the edges or for unknown pairs`() = runTest {
        val repository = FakeFavoritesRepository(listOf(usdEur, eurGbp))
        val move = MoveFavoriteUseCase(repository)

        move(usdEur, Direction.Up)
        move(eurGbp, Direction.Down)
        move(usdJpy, Direction.Up)

        assertEquals(listOf(usdEur, eurGbp), repository.stored.value)
    }

    @Test
    fun `changing the default base also switches the converter`() = runTest {
        val settings = FakeSettingsRepository(initialPair = usdEur)
        SetDefaultBaseCurrencyUseCase(settings)("GBP")

        assertEquals("GBP", settings.current.value.defaultBase)
        assertEquals(CurrencyPair("GBP", "EUR"), settings.pair.value)
    }

    @Test
    fun `choosing the current target as default base swaps the converter sides`() = runTest {
        val settings = FakeSettingsRepository(initialPair = usdEur)
        SetDefaultBaseCurrencyUseCase(settings)("EUR")

        assertEquals(CurrencyPair("EUR", "USD"), settings.pair.value)
    }

    @Test
    fun `refresh passes the configured interval as maximum age`() = runTest {
        val ratesRepository = FakeRatesRepository().apply { nextResult = RefreshResult.Updated }
        val settings = FakeSettingsRepository(AppSettings(refreshInterval = RefreshInterval.Every3Hours))

        val result = RefreshRatesUseCase(ratesRepository, settings)(force = false)

        assertEquals(RefreshResult.Updated, result)
        assertEquals(listOf(false to Duration.ofHours(3)), ratesRepository.calls)
    }

    @Test
    fun `manual only interval means cached data is never stale`() = runTest {
        val ratesRepository = FakeRatesRepository()
        val settings = FakeSettingsRepository(AppSettings(refreshInterval = RefreshInterval.Manual))

        RefreshRatesUseCase(ratesRepository, settings)(force = true)

        assertEquals(listOf(true to null), ratesRepository.calls)
    }

    @Test
    fun `search matches code prefix name and symbol`() {
        val search = SearchCurrenciesUseCase()
        val all = CurrencyCatalog.ALL

        assertEquals(listOf("EUR"), search("eur", all).map { it.code })
        assertTrue(search("dollar", all).map { it.code }.containsAll(listOf("USD", "CAD", "AUD")))
        assertEquals(listOf("EUR"), search("€", all).map { it.code })
        assertEquals(all, search("  ", all))
        assertTrue(search("qqqq", all).isEmpty())
    }

    @Test
    fun `search ranks exact code matches first`() {
        val result = SearchCurrenciesUseCase()("nok", CurrencyCatalog.ALL)
        assertEquals("NOK", result.first().code)
    }

    @Test
    fun `pair helpers keep both sides different`() {
        assertEquals(CurrencyPair("GBP", "EUR"), usdEur.withFrom("GBP"))
        assertEquals(CurrencyPair("EUR", "USD"), usdEur.withFrom("EUR"))
        assertEquals(CurrencyPair("USD", "GBP"), usdEur.withTo("GBP"))
        assertEquals(CurrencyPair("EUR", "USD"), usdEur.withTo("USD"))
        assertEquals(CurrencyPair("EUR", "USD"), usdEur.reversed())
    }
}
