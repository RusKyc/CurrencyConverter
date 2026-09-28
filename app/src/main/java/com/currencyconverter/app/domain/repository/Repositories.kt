package com.currencyconverter.app.domain.repository

import com.currencyconverter.app.domain.model.AppSettings
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.ExchangeRates
import com.currencyconverter.app.domain.model.RefreshInterval
import com.currencyconverter.app.domain.model.RefreshResult
import com.currencyconverter.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration

interface RatesRepository {
    /** Latest stored rates. Emits `null` while nothing has ever been downloaded. Backed by the local database. */
    val rates: Flow<ExchangeRates?>

    /** `true` only while a network request is really in flight. */
    val isRefreshing: StateFlow<Boolean>

    /**
     * Downloads fresh rates and stores them.
     *
     * @param force ignore [maxAge] (still protected against request flooding)
     * @param maxAge cached data younger than this is considered fresh; `null` = never stale
     */
    suspend fun refresh(force: Boolean, maxAge: Duration?): RefreshResult
}

interface FavoritesRepository {
    /** Favorite pairs in the order chosen by the user. */
    val favorites: Flow<List<CurrencyPair>>

    suspend fun add(pair: CurrencyPair)

    suspend fun remove(pair: CurrencyPair)

    suspend fun setOrder(pairs: List<CurrencyPair>)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** The pair currently shown in the converter; survives app restarts. */
    val converterPair: Flow<CurrencyPair>

    suspend fun setDefaultBase(code: String)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setRefreshInterval(interval: RefreshInterval)

    suspend fun setDecimalPlaces(places: DecimalPlaces)

    suspend fun setConverterPair(pair: CurrencyPair)
}

interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
