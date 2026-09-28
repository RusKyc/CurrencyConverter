package com.currencyconverter.app.presentation

import androidx.lifecycle.SavedStateHandle
import com.currencyconverter.app.domain.currency.CurrencyCatalog
import com.currencyconverter.app.domain.format.AmountFormatter
import com.currencyconverter.app.domain.model.AppSettings
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.domain.model.RefreshResult
import com.currencyconverter.app.domain.usecase.ConvertCurrencyUseCase
import com.currencyconverter.app.domain.usecase.RefreshRatesUseCase
import com.currencyconverter.app.domain.usecase.SearchCurrenciesUseCase
import com.currencyconverter.app.domain.usecase.ToggleFavoriteUseCase
import com.currencyconverter.app.presentation.common.DataFreshness
import com.currencyconverter.app.presentation.home.AmountSide
import com.currencyconverter.app.presentation.home.HomeEvent
import com.currencyconverter.app.presentation.home.HomeUiState
import com.currencyconverter.app.presentation.home.HomeViewModel
import com.currencyconverter.app.presentation.home.RatesContent
import com.currencyconverter.app.testing.FakeFavoritesRepository
import com.currencyconverter.app.testing.FakeNetworkMonitor
import com.currencyconverter.app.testing.FakeRatesRepository
import com.currencyconverter.app.testing.FakeSettingsRepository
import com.currencyconverter.app.testing.FixedTimeSource
import com.currencyconverter.app.testing.TestNow
import com.currencyconverter.app.testing.sampleRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var ratesRepository: FakeRatesRepository
    private lateinit var settings: FakeSettingsRepository
    private lateinit var favorites: FakeFavoritesRepository
    private lateinit var network: FakeNetworkMonitor
    private lateinit var savedState: SavedStateHandle

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ratesRepository = FakeRatesRepository(sampleRates())
        settings = FakeSettingsRepository()
        favorites = FakeFavoritesRepository()
        network = FakeNetworkMonitor()
        savedState = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HomeViewModel(
        savedStateHandle = savedState,
        ratesRepository = ratesRepository,
        settingsRepository = settings,
        favoritesRepository = favorites,
        networkMonitor = network,
        timeSource = FixedTimeSource(),
        refreshRates = RefreshRatesUseCase(ratesRepository, settings),
        convertCurrency = ConvertCurrencyUseCase(),
        toggleFavorite = ToggleFavoriteUseCase(favorites),
        searchCurrencies = SearchCurrenciesUseCase(),
        catalog = CurrencyCatalog(),
        formatter = AmountFormatter(),
    )

    /** Starts collecting so the WhileSubscribed state flow is active, and returns the live state holder. */
    private fun TestScope.observe(vm: HomeViewModel): () -> HomeUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        return { vm.uiState.value }
    }

    @Test
    fun `shows the default amount converted with cached rates`() = runTest {
        val state = observe(viewModel())()

        // 100 USD -> EUR with EUR->USD = 1.25 gives 80.00
        assertEquals("USD", state.from.code)
        assertEquals("EUR", state.to.code)
        assertEquals("100", state.fromText)
        assertEquals("80.00", state.toText)
        val content = state.content as RatesContent.Success
        assertEquals("0.8000", content.rate.rate)
        assertEquals("1.2500", content.rate.inverseRate)
    }

    @Test
    fun `typing in the target field calculates the source field`() = runTest {
        val vm = viewModel()
        val state = observe(vm)

        vm.onAmountChange(AmountSide.To, "80")

        assertEquals("100.00", state().fromText)
        assertEquals("80", state().toText)
        assertEquals(AmountSide.To, state().activeSide)
    }

    @Test
    fun `typed text is sanitised and empty input clears the result`() = runTest {
        val vm = viewModel()
        val state = observe(vm)

        vm.onAmountChange(AmountSide.From, "1a2,5b")
        assertEquals("12,5", state().fromText)
        assertEquals("10.00", state().toText)

        vm.onAmountChange(AmountSide.From, "")
        assertEquals("", state().fromText)
        assertEquals("", state().toText)
    }

    @Test
    fun `decimal places setting is applied to the result`() = runTest {
        settings.current.value = AppSettings(decimalPlaces = DecimalPlaces.Fixed(4))
        val state = observe(viewModel())

        assertEquals("80.0000", state().toText)
    }

    @Test
    fun `yen result uses zero decimals with auto places`() = runTest {
        settings.pair.value = CurrencyPair("USD", "JPY")
        val state = observe(viewModel())

        // 100 USD = 80 EUR = 12,800 JPY
        assertEquals("12800", state().toText)
    }

    @Test
    fun `swap exchanges currencies and keeps the typed amount with its currency`() = runTest {
        val vm = viewModel()
        val state = observe(vm)

        vm.onSwap()

        assertEquals("EUR", state().from.code)
        assertEquals("USD", state().to.code)
        assertEquals(AmountSide.To, state().activeSide)
        assertEquals("100", state().toText)
        assertEquals("80.00", state().fromText)
        assertEquals(CurrencyPair("EUR", "USD"), settings.pair.value)

        vm.onSwap()
        assertEquals("USD", state().from.code)
        assertEquals(AmountSide.From, state().activeSide)
    }

    @Test
    fun `fast double swap returns to the original layout instead of desynchronising`() = runTest {
        settings.writeDelayMs = 50
        val vm = viewModel()
        val state = observe(vm)

        vm.onSwap()
        vm.onSwap()
        testScheduler.advanceUntilIdle()

        assertEquals("USD", state().from.code)
        assertEquals("EUR", state().to.code)
        assertEquals(AmountSide.From, state().activeSide)
        assertEquals("100", state().fromText)
        assertEquals("80.00", state().toText)
    }

    @Test
    fun `selecting the other side's currency swaps instead of duplicating`() = runTest {
        val vm = viewModel()
        val state = observe(vm)

        vm.onCurrencySelected(AmountSide.From, "EUR")

        assertEquals("EUR", state().from.code)
        assertEquals("USD", state().to.code)
    }

    @Test
    fun `selecting a currency updates only that side`() = runTest {
        val vm = viewModel()
        val state = observe(vm)

        vm.onCurrencySelected(AmountSide.To, "GBP")

        assertEquals("USD", state().from.code)
        assertEquals("GBP", state().to.code)
        assertEquals("64.00", state().toText)
    }

    @Test
    fun `favorite toggle adds and removes the current pair`() = runTest {
        val vm = viewModel()
        val state = observe(vm)
        assertFalse(state().isFavorite)

        vm.onToggleFavorite()
        assertTrue(state().isFavorite)
        assertEquals(listOf(CurrencyPair("USD", "EUR")), favorites.stored.value)
        assertEquals("0.8000", state().favorites.single().rate)

        vm.onToggleFavorite()
        assertFalse(state().isFavorite)
        assertTrue(state().favorites.isEmpty())
    }

    @Test
    fun `opening a favorite makes it the current pair`() = runTest {
        val vm = viewModel()
        val state = observe(vm)

        vm.onFavoriteSelected(CurrencyPair("GBP", "JPY"))

        assertEquals("GBP", state().from.code)
        assertEquals("JPY", state().to.code)
    }

    @Test
    fun `pair missing from the rates shows the empty state`() = runTest {
        settings.pair.value = CurrencyPair("USD", "CHF")
        val state = observe(viewModel())

        assertEquals(RatesContent.Empty, state().content)
        assertEquals("", state().toText)
    }

    @Test
    fun `starts in loading state until the first refresh finishes when nothing is cached`() = runTest {
        ratesRepository = FakeRatesRepository(initial = null)
        val vm = viewModel()
        val state = observe(vm)

        assertEquals(RatesContent.Loading, state().content)
        assertNull(state().updateInfo)
    }

    @Test
    fun `failed first refresh without cache shows a classified error`() = runTest {
        ratesRepository = FakeRatesRepository(initial = null).apply {
            nextResult = RefreshResult.Failed(RatesError.Network)
        }
        val vm = viewModel()
        val state = observe(vm)

        vm.onStart()

        assertEquals(RatesContent.Error(RatesError.Network), state().content)
    }

    @Test
    fun `every error kind is exposed to the ui`() = runTest {
        listOf(RatesError.Api(503), RatesError.Parsing, RatesError.Unknown("x")).forEach { error ->
            ratesRepository = FakeRatesRepository(initial = null).apply { nextResult = RefreshResult.Failed(error) }
            val vm = viewModel()
            val state = observe(vm)
            vm.onStart()
            assertEquals(RatesContent.Error(error), state().content)
        }
    }

    @Test
    fun `failed refresh with cache keeps showing rates and reports the error`() = runTest {
        ratesRepository.nextResult = RefreshResult.Failed(RatesError.Api(500))
        val vm = viewModel()
        val state = observe(vm)

        vm.onRefresh()

        assertTrue(state().content is RatesContent.Success)
        assertEquals("80.00", state().toText)
        assertEquals(RatesError.Api(500), state().refreshError)
        assertEquals(DataFreshness.CachedAfterError, state().updateInfo!!.freshness)
    }

    @Test
    fun `next successful refresh clears the error`() = runTest {
        ratesRepository.nextResult = RefreshResult.Failed(RatesError.Network)
        val vm = viewModel()
        val state = observe(vm)
        vm.onRefresh()
        assertNotNull(state().refreshError)

        ratesRepository.nextResult = RefreshResult.Updated
        vm.onRefresh()

        assertNull(state().refreshError)
        assertEquals(DataFreshness.Live, state().updateInfo!!.freshness)
    }

    @Test
    fun `offline device marks cached data as offline`() = runTest {
        network.state.value = false
        val state = observe(viewModel())

        assertEquals(DataFreshness.Offline, state().updateInfo!!.freshness)
        assertEquals(TestNow, state().updateInfo!!.updatedAt)
        assertEquals("80.00", state().toText)
    }

    @Test
    fun `manual refresh forces a request and lifecycle refresh does not`() = runTest {
        val vm = viewModel()
        observe(vm)

        vm.onStart()
        vm.onRefresh()

        assertEquals(listOf(false to Duration.ofHours(6), true to Duration.ofHours(6)), ratesRepository.calls)
    }

    @Test
    fun `manual refresh that is throttled tells the user`() = runTest {
        ratesRepository.nextResult = RefreshResult.Throttled
        val vm = viewModel()
        observe(vm)
        var received: HomeEvent? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { received = vm.events.first() }

        vm.onRefresh()

        assertEquals(HomeEvent.RefreshThrottled, received)
    }

    @Test
    fun `automatic refresh that is throttled stays silent`() = runTest {
        ratesRepository.nextResult = RefreshResult.Throttled
        val vm = viewModel()
        observe(vm)
        var received: HomeEvent? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { received = vm.events.first() }

        vm.onStart()

        assertNull(received)
    }

    @Test
    fun `refreshing flag comes from the repository`() = runTest {
        val vm = viewModel()
        val state = observe(vm)
        assertFalse(state().isRefreshing)

        ratesRepository.refreshing.value = true
        assertTrue(state().isRefreshing)
    }

    @Test
    fun `search delegates to the currency search`() {
        val vm = viewModel()
        assertEquals(listOf("PLN"), vm.search("pln").map { it.code })
    }

    @Test
    fun `amount survives recreation through saved state`() = runTest {
        val first = viewModel()
        val firstState = observe(first)
        first.onAmountChange(AmountSide.To, "42")
        assertEquals("42", firstState().toText)

        val second = viewModel()
        val secondState = observe(second)

        assertEquals("42", secondState().toText)
        assertEquals(AmountSide.To, secondState().activeSide)
    }
}
