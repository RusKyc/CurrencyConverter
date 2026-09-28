package com.currencyconverter.app.presentation

import com.currencyconverter.app.domain.currency.CurrencyCatalog
import com.currencyconverter.app.domain.format.AmountFormatter
import com.currencyconverter.app.domain.model.AppInfo
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.RefreshInterval
import com.currencyconverter.app.domain.model.ThemeMode
import com.currencyconverter.app.domain.usecase.AddFavoriteUseCase
import com.currencyconverter.app.domain.usecase.MoveFavoriteUseCase
import com.currencyconverter.app.domain.usecase.SearchCurrenciesUseCase
import com.currencyconverter.app.domain.usecase.SetDefaultBaseCurrencyUseCase
import com.currencyconverter.app.presentation.favorites.FavoritesUiState
import com.currencyconverter.app.presentation.favorites.FavoritesViewModel
import com.currencyconverter.app.presentation.settings.SettingsUiState
import com.currencyconverter.app.presentation.settings.SettingsViewModel
import com.currencyconverter.app.testing.FakeFavoritesRepository
import com.currencyconverter.app.testing.FakeRatesRepository
import com.currencyconverter.app.testing.FakeSettingsRepository
import com.currencyconverter.app.testing.sampleRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesAndSettingsViewModelTest {

    private val usdEur = CurrencyPair("USD", "EUR")
    private val eurGbp = CurrencyPair("EUR", "GBP")
    private val usdJpy = CurrencyPair("USD", "JPY")
    private val catalog = CurrencyCatalog()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun favoritesViewModel(
        favorites: FakeFavoritesRepository,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        rates: FakeRatesRepository = FakeRatesRepository(sampleRates()),
    ) = FavoritesViewModel(
        favoritesRepository = favorites,
        ratesRepository = rates,
        settingsRepository = settings,
        addFavorite = AddFavoriteUseCase(favorites),
        moveFavorite = MoveFavoriteUseCase(favorites),
        searchCurrencies = SearchCurrenciesUseCase(),
        catalog = catalog,
        formatter = AmountFormatter(),
    )

    private fun TestScope.observe(vm: FavoritesViewModel): () -> FavoritesUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        return { vm.uiState.value }
    }

    @Test
    fun `rows carry rates and move flags`() = runTest {
        val vm = favoritesViewModel(FakeFavoritesRepository(listOf(usdEur, eurGbp, usdJpy)))
        val state = observe(vm)()

        assertTrue(state.isLoaded)
        assertEquals(listOf(usdEur, eurGbp, usdJpy), state.rows.map { it.pair })
        assertEquals(listOf("0.8000", "0.8000", "128.00"), state.rows.map { it.rate })
        assertEquals(listOf(false, true, true), state.rows.map { it.canMoveUp })
        assertEquals(listOf(true, true, false), state.rows.map { it.canMoveDown })
    }

    @Test
    fun `empty favorites are loaded but empty`() = runTest {
        val state = observe(favoritesViewModel(FakeFavoritesRepository()))()

        assertTrue(state.isLoaded)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun `rows without rates still show the pair`() = runTest {
        val vm = favoritesViewModel(FakeFavoritesRepository(listOf(usdEur)), rates = FakeRatesRepository(null))
        val row = observe(vm)().rows.single()

        assertEquals(usdEur, row.pair)
        assertNull(row.rate)
    }

    @Test
    fun `move up and down reorder the list`() = runTest {
        val repository = FakeFavoritesRepository(listOf(usdEur, eurGbp, usdJpy))
        val vm = favoritesViewModel(repository)
        val state = observe(vm)

        vm.onMoveDown(usdEur)
        assertEquals(listOf(eurGbp, usdEur, usdJpy), state().rows.map { it.pair })

        vm.onMoveUp(usdJpy)
        assertEquals(listOf(eurGbp, usdJpy, usdEur), state().rows.map { it.pair })
    }

    @Test
    fun `remove deletes the pair`() = runTest {
        val vm = favoritesViewModel(FakeFavoritesRepository(listOf(usdEur, eurGbp)))
        val state = observe(vm)

        vm.onRemove(usdEur)

        assertEquals(listOf(eurGbp), state().rows.map { it.pair })
    }

    @Test
    fun `add appends a new pair but rejects identical currencies`() = runTest {
        val vm = favoritesViewModel(FakeFavoritesRepository(listOf(usdEur)))
        val state = observe(vm)
        val gbp = catalog.find("GBP")!!
        val eur = catalog.find("EUR")!!

        vm.onAdd(eur, gbp)
        vm.onAdd(eur, eur)

        assertEquals(listOf(usdEur, eurGbp), state().rows.map { it.pair })
    }

    @Test
    fun `open selects the pair for the converter and then navigates`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = favoritesViewModel(FakeFavoritesRepository(listOf(usdJpy)), settings)
        var navigated = false

        vm.onOpen(usdJpy) { navigated = true }

        assertEquals(usdJpy, settings.pair.value)
        assertTrue(navigated)
    }

    // ---- Settings ----

    private fun settingsViewModel(settings: FakeSettingsRepository) = SettingsViewModel(
        settingsRepository = settings,
        setDefaultBaseCurrency = SetDefaultBaseCurrencyUseCase(settings),
        searchCurrencies = SearchCurrenciesUseCase(),
        catalog = catalog,
        appInfo = AppInfo("1.2.3", "Provider", "https://example.org"),
    )

    private fun TestScope.observe(vm: SettingsViewModel): () -> SettingsUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        return { vm.uiState.value }
    }

    @Test
    fun `settings state exposes values and app info`() = runTest {
        val state = observe(settingsViewModel(FakeSettingsRepository()))()

        assertEquals("USD", state.baseCurrency.code)
        assertEquals("1.2.3", state.appInfo.versionName)
        assertEquals("Provider", state.appInfo.ratesProviderName)
    }

    @Test
    fun `every setting can be changed`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = settingsViewModel(settings)
        val state = observe(vm)

        vm.onThemeSelected(ThemeMode.Dark)
        vm.onRefreshIntervalSelected(RefreshInterval.Daily)
        vm.onDecimalPlacesSelected(DecimalPlaces.Fixed(3))
        vm.onDefaultBaseSelected(catalog.find("CHF")!!)

        val current = state().settings
        assertEquals(ThemeMode.Dark, current.themeMode)
        assertEquals(RefreshInterval.Daily, current.refreshInterval)
        assertEquals(DecimalPlaces.Fixed(3), current.decimalPlaces)
        assertEquals("CHF", state().baseCurrency.code)
        assertEquals("CHF", settings.pair.value.from)
    }

    @Test
    fun `settings offers all intervals and decimal options`() {
        val vm = settingsViewModel(FakeSettingsRepository())

        assertEquals(RefreshInterval.entries, vm.refreshIntervals)
        assertEquals(DecimalPlaces.Auto, vm.decimalOptions.first())
        assertEquals(8, vm.decimalOptions.size)
        assertFalse(vm.currencies.isEmpty())
    }
}
