package com.currencyconverter.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.currencyconverter.app.domain.currency.CurrencyCatalog
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.presentation.common.DataFreshness
import com.currencyconverter.app.presentation.home.AmountSide
import com.currencyconverter.app.presentation.home.FavoriteItem
import com.currencyconverter.app.presentation.home.HomeUiState
import com.currencyconverter.app.presentation.home.RateDisplay
import com.currencyconverter.app.presentation.home.RatesContent
import com.currencyconverter.app.presentation.home.UpdateInfo
import com.currencyconverter.app.testing.TestNow
import java.time.Duration

val catalog = CurrencyCatalog()
val usd = catalog.find("USD")!!
val eur = catalog.find("EUR")!!
val gbp = catalog.find("GBP")!!

fun string(resId: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(resId, *args)

fun homeState(
    content: RatesContent = RatesContent.Success(RateDisplay("USD", "EUR", "0.8642", "1.1571")),
    updateInfo: UpdateInfo? = UpdateInfo(TestNow.minus(Duration.ofMinutes(5)), DataFreshness.Live),
    refreshError: com.currencyconverter.app.domain.model.RatesError? = null,
    favorites: List<FavoriteItem> = emptyList(),
    isFavorite: Boolean = false,
    isRefreshing: Boolean = false,
) = HomeUiState(
    from = usd,
    to = eur,
    fromText = "100",
    toText = "86.42",
    activeSide = AmountSide.From,
    content = content,
    updateInfo = updateInfo,
    refreshError = refreshError,
    isRefreshing = isRefreshing,
    isFavorite = isFavorite,
    favorites = favorites,
    now = TestNow,
)

fun favoriteItem(from: String, to: String, rate: String? = "0.8642") = FavoriteItem(
    pair = CurrencyPair(from, to),
    from = catalog.find(from)!!,
    to = catalog.find(to)!!,
    rate = rate,
)
