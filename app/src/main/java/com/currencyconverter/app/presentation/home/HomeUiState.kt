package com.currencyconverter.app.presentation.home

import com.currencyconverter.app.domain.model.Currency
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.presentation.common.DataFreshness
import java.time.Instant

/** Which of the two amount fields the user is typing into; the other one is calculated. */
enum class AmountSide { From, To }

/** What the rate area shows. Mirrors the Loading / Success / Empty / error states of the spec. */
sealed interface RatesContent {
    data object Loading : RatesContent

    /** Rates exist but none for the selected pair, or nothing is stored and nothing failed. */
    data object Empty : RatesContent

    /** Nothing stored to fall back on. Classified so the UI can explain what happened. */
    data class Error(val error: RatesError) : RatesContent

    data class Success(val rate: RateDisplay) : RatesContent
}

data class RateDisplay(
    val fromCode: String,
    val toCode: String,
    val rate: String,
    val inverseRate: String,
)

data class UpdateInfo(
    val updatedAt: Instant,
    val freshness: DataFreshness,
)

data class FavoriteItem(
    val pair: CurrencyPair,
    val from: Currency,
    val to: Currency,
    val rate: String?,
)

data class HomeUiState(
    val from: Currency,
    val to: Currency,
    val fromText: String,
    val toText: String,
    val activeSide: AmountSide,
    val content: RatesContent,
    /** `null` while no rates have ever been stored. */
    val updateInfo: UpdateInfo?,
    /** Failure of the last refresh; shown as a banner only when stored rates are still displayed. */
    val refreshError: RatesError?,
    val isRefreshing: Boolean,
    val isFavorite: Boolean,
    val favorites: List<FavoriteItem>,
    val now: Instant,
)

sealed interface HomeEvent {
    data object RefreshThrottled : HomeEvent
}
