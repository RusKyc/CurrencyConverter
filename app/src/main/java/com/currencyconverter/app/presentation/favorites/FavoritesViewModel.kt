package com.currencyconverter.app.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.currencyconverter.app.domain.currency.CurrencyCatalog
import com.currencyconverter.app.domain.format.AmountFormatter
import com.currencyconverter.app.domain.model.Currency
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.ExchangeRates
import com.currencyconverter.app.domain.repository.FavoritesRepository
import com.currencyconverter.app.domain.repository.RatesRepository
import com.currencyconverter.app.domain.repository.SettingsRepository
import com.currencyconverter.app.domain.usecase.AddFavoriteUseCase
import com.currencyconverter.app.domain.usecase.MoveFavoriteUseCase
import com.currencyconverter.app.domain.usecase.MoveFavoriteUseCase.Direction
import com.currencyconverter.app.domain.usecase.SearchCurrenciesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoriteRow(
    val pair: CurrencyPair,
    val from: Currency,
    val to: Currency,
    val rate: String?,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

data class FavoritesUiState(
    val rows: List<FavoriteRow> = emptyList(),
    /** `false` until the first read from the database, so an empty list is not flashed. */
    val isLoaded: Boolean = false,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val addFavorite: AddFavoriteUseCase,
    private val moveFavorite: MoveFavoriteUseCase,
    private val searchCurrencies: SearchCurrenciesUseCase,
    private val catalog: CurrencyCatalog,
    private val formatter: AmountFormatter,
) : ViewModel() {

    val currencies: List<Currency> get() = catalog.all

    val uiState: StateFlow<FavoritesUiState> = combine(
        favoritesRepository.favorites,
        ratesRepository.rates.onStart { emit(null) },
    ) { pairs, rates -> buildState(pairs, rates) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FavoritesUiState())

    fun onMoveUp(pair: CurrencyPair) {
        viewModelScope.launch { moveFavorite(pair, Direction.Up) }
    }

    fun onMoveDown(pair: CurrencyPair) {
        viewModelScope.launch { moveFavorite(pair, Direction.Down) }
    }

    fun onRemove(pair: CurrencyPair) {
        viewModelScope.launch { favoritesRepository.remove(pair) }
    }

    fun onAdd(from: Currency, to: Currency) {
        viewModelScope.launch { addFavorite(CurrencyPair(from.code, to.code)) }
    }

    /** Makes [pair] the converter's current pair, then lets the caller navigate. */
    fun onOpen(pair: CurrencyPair, onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setConverterPair(pair)
            onDone()
        }
    }

    fun search(query: String): List<Currency> = searchCurrencies(query, catalog.all)

    private fun buildState(pairs: List<CurrencyPair>, rates: ExchangeRates?): FavoritesUiState {
        val rows = pairs.mapIndexedNotNull { index, pair ->
            val from = catalog.find(pair.from) ?: return@mapIndexedNotNull null
            val to = catalog.find(pair.to) ?: return@mapIndexedNotNull null
            FavoriteRow(
                pair = pair,
                from = from,
                to = to,
                rate = rates?.rate(pair.from, pair.to)?.let { formatter.formatRate(it.value) },
                canMoveUp = index > 0,
                canMoveDown = index < pairs.lastIndex,
            )
        }
        return FavoritesUiState(rows = rows, isLoaded = true)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
