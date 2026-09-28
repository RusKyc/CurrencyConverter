package com.currencyconverter.app.presentation.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.currencyconverter.app.domain.currency.CurrencyCatalog
import com.currencyconverter.app.domain.format.AmountFormatter
import com.currencyconverter.app.domain.format.AmountInput
import com.currencyconverter.app.domain.model.AppSettings
import com.currencyconverter.app.domain.model.Currency
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.ExchangeRates
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.domain.model.RefreshResult
import com.currencyconverter.app.domain.repository.FavoritesRepository
import com.currencyconverter.app.domain.repository.NetworkMonitor
import com.currencyconverter.app.domain.repository.RatesRepository
import com.currencyconverter.app.domain.repository.SettingsRepository
import com.currencyconverter.app.domain.time.TimeSource
import com.currencyconverter.app.domain.usecase.ConvertCurrencyUseCase
import com.currencyconverter.app.domain.usecase.RefreshRatesUseCase
import com.currencyconverter.app.domain.usecase.SearchCurrenciesUseCase
import com.currencyconverter.app.domain.usecase.ToggleFavoriteUseCase
import com.currencyconverter.app.presentation.common.DataFreshness
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val favoritesRepository: FavoritesRepository,
    networkMonitor: NetworkMonitor,
    timeSource: TimeSource,
    private val refreshRates: RefreshRatesUseCase,
    private val convertCurrency: ConvertCurrencyUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val searchCurrencies: SearchCurrenciesUseCase,
    private val catalog: CurrencyCatalog,
    private val formatter: AmountFormatter,
) : ViewModel() {

    private sealed interface Cache {
        data object Loading : Cache
        data class Loaded(val rates: ExchangeRates?) : Cache
    }

    private data class Sources(
        val cache: Cache,
        val settings: AppSettings,
        val pair: CurrencyPair,
        val favorites: List<CurrencyPair>,
    )

    private data class Transient(
        val amountText: String,
        val side: AmountSide,
        val refreshError: RatesError?,
        val refreshAttempted: Boolean,
        val isRefreshing: Boolean,
        val isOnline: Boolean,
    )

    private val lastRefreshError = MutableStateFlow<RatesError?>(null)
    private val refreshAttempted = MutableStateFlow(false)
    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)

    /** Read-modify-write actions on the pair must not interleave (e.g. a fast double tap on swap). */
    private val pairMutex = Mutex()

    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    /** All selectable currencies, for the picker. */
    val currencies: List<Currency> get() = catalog.all

    private val cache: Flow<Cache> = ratesRepository.rates
        .map<ExchangeRates?, Cache> { Cache.Loaded(it) }
        .onStart { emit(Cache.Loading) }

    private val sources: Flow<Sources> = combine(
        cache,
        settingsRepository.settings,
        settingsRepository.converterPair,
        favoritesRepository.favorites,
        ::Sources,
    )

    private val transient: Flow<Transient> = combine(
        savedStateHandle.getStateFlow(KEY_AMOUNT, DEFAULT_AMOUNT),
        savedStateHandle.getStateFlow(KEY_SIDE, AmountSide.From.name),
        lastRefreshError,
        refreshAttempted,
        combine(ratesRepository.isRefreshing, networkMonitor.isOnline, ::Pair),
    ) { amount, side, error, attempted, (refreshing, online) ->
        Transient(
            amountText = amount,
            side = AmountSide.entries.firstOrNull { it.name == side } ?: AmountSide.From,
            refreshError = error,
            refreshAttempted = attempted,
            isRefreshing = refreshing,
            isOnline = online,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(sources, transient, timeSource.ticks(), ::buildState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialState())

    /** Called whenever the screen becomes visible: refreshes only if stored rates are stale. */
    fun onStart() {
        viewModelScope.launch { handle(refreshRates(force = false), manual = false) }
    }

    fun onRefresh() {
        viewModelScope.launch { handle(refreshRates(force = true), manual = true) }
    }

    fun onAmountChange(side: AmountSide, raw: String) {
        savedStateHandle[KEY_AMOUNT] = AmountInput.sanitize(raw)
        savedStateHandle[KEY_SIDE] = side.name
    }

    fun sanitizeAmount(raw: String): String = AmountInput.sanitize(raw)

    fun onSwap() {
        viewModelScope.launch {
            pairMutex.withLock {
                val pair = settingsRepository.converterPair.first()
                settingsRepository.setConverterPair(pair.reversed())
                // The typed amount stays attached to its currency, so it moves to the other row.
                val current = savedStateHandle.get<String>(KEY_SIDE)
                savedStateHandle[KEY_SIDE] =
                    if (current == AmountSide.To.name) AmountSide.From.name else AmountSide.To.name
            }
        }
    }

    fun onCurrencySelected(side: AmountSide, code: String) {
        viewModelScope.launch {
            pairMutex.withLock {
                val pair = settingsRepository.converterPair.first()
                settingsRepository.setConverterPair(
                    if (side == AmountSide.From) pair.withFrom(code) else pair.withTo(code),
                )
            }
        }
    }

    fun onToggleFavorite() {
        viewModelScope.launch {
            pairMutex.withLock { toggleFavorite(settingsRepository.converterPair.first()) }
        }
    }

    fun onFavoriteSelected(pair: CurrencyPair) {
        viewModelScope.launch { settingsRepository.setConverterPair(pair) }
    }

    fun search(query: String): List<Currency> = searchCurrencies(query, catalog.all)

    private fun handle(result: RefreshResult, manual: Boolean) {
        refreshAttempted.value = true
        when (result) {
            RefreshResult.Updated, RefreshResult.UpToDate -> lastRefreshError.value = null
            RefreshResult.Throttled -> if (manual) eventChannel.trySend(HomeEvent.RefreshThrottled)
            is RefreshResult.Failed -> lastRefreshError.value = result.error
        }
    }

    private fun buildState(sources: Sources, transient: Transient, now: Instant): HomeUiState {
        val from = resolve(sources.pair.from)
        val to = resolve(sources.pair.to)
        val places = sources.settings.decimalPlaces
        val rates = (sources.cache as? Cache.Loaded)?.rates
        val rate = rates?.rate(from.code, to.code)
        val typed = AmountInput.parse(transient.amountText)

        val calculated = if (typed != null && rates != null) {
            val fromTyped = transient.side == AmountSide.From
            val conversion = if (fromTyped) {
                convertCurrency(typed, from.code, to.code, rates)
            } else {
                convertCurrency(typed, to.code, from.code, rates)
            }
            conversion?.let { formatter.formatEditable(it.result, if (fromTyped) to else from, places) }.orEmpty()
        } else {
            ""
        }
        val fromText = if (transient.side == AmountSide.From) transient.amountText else calculated
        val toText = if (transient.side == AmountSide.To) transient.amountText else calculated

        val content = when {
            sources.cache is Cache.Loading -> RatesContent.Loading
            rates == null -> when {
                transient.isRefreshing || !transient.refreshAttempted -> RatesContent.Loading
                transient.refreshError != null -> RatesContent.Error(transient.refreshError)
                else -> RatesContent.Empty
            }
            rate == null -> RatesContent.Empty
            else -> RatesContent.Success(
                RateDisplay(
                    fromCode = from.code,
                    toCode = to.code,
                    rate = formatter.formatRate(rate.value),
                    inverseRate = formatter.formatRate(rate.inverse().value),
                ),
            )
        }

        val freshness = when {
            !transient.isOnline -> DataFreshness.Offline
            transient.refreshError != null -> DataFreshness.CachedAfterError
            else -> DataFreshness.Live
        }

        return HomeUiState(
            from = from,
            to = to,
            fromText = fromText,
            toText = toText,
            activeSide = transient.side,
            content = content,
            updateInfo = rates?.let { UpdateInfo(it.fetchedAt, freshness) },
            refreshError = transient.refreshError,
            isRefreshing = transient.isRefreshing,
            isFavorite = sources.pair in sources.favorites,
            favorites = sources.favorites.mapNotNull { pair -> favoriteItem(pair, rates) },
            now = now,
        )
    }

    private fun favoriteItem(pair: CurrencyPair, rates: ExchangeRates?): FavoriteItem? {
        val from = catalog.find(pair.from) ?: return null
        val to = catalog.find(pair.to) ?: return null
        val rate = rates?.rate(pair.from, pair.to)?.let { formatter.formatRate(it.value) }
        return FavoriteItem(pair, from, to, rate)
    }

    private fun resolve(code: String): Currency = catalog.find(code) ?: catalog.all.first()

    private fun initialState(): HomeUiState = HomeUiState(
        from = resolve("USD"),
        to = resolve("EUR"),
        fromText = DEFAULT_AMOUNT,
        toText = "",
        activeSide = AmountSide.From,
        content = RatesContent.Loading,
        updateInfo = null,
        refreshError = null,
        isRefreshing = false,
        isFavorite = false,
        favorites = emptyList(),
        now = Instant.EPOCH,
    )

    private companion object {
        const val KEY_AMOUNT = "amount"
        const val KEY_SIDE = "side"
        const val DEFAULT_AMOUNT = "100"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
