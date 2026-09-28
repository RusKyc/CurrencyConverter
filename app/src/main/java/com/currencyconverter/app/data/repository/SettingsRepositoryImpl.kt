package com.currencyconverter.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.currencyconverter.app.domain.model.AppSettings
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.RefreshInterval
import com.currencyconverter.app.domain.model.ThemeMode
import com.currencyconverter.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val settings: Flow<AppSettings> = preferences.map { it.toSettings() }.distinctUntilChanged()

    override val converterPair: Flow<CurrencyPair> = preferences.map { prefs ->
        val base = prefs[DEFAULT_BASE] ?: AppSettings().defaultBase
        val from = prefs[PAIR_FROM] ?: base
        val to = prefs[PAIR_TO]?.takeIf { it != from } ?: fallbackQuote(from)
        CurrencyPair(from, to)
    }.distinctUntilChanged()

    override suspend fun setDefaultBase(code: String) {
        dataStore.edit { it[DEFAULT_BASE] = code }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    override suspend fun setRefreshInterval(interval: RefreshInterval) {
        dataStore.edit { it[REFRESH] = interval.name }
    }

    override suspend fun setDecimalPlaces(places: DecimalPlaces) {
        dataStore.edit {
            it[DECIMALS] = when (places) {
                DecimalPlaces.Auto -> AUTO_DECIMALS
                is DecimalPlaces.Fixed -> places.count
            }
        }
    }

    override suspend fun setConverterPair(pair: CurrencyPair) {
        dataStore.edit {
            it[PAIR_FROM] = pair.from
            it[PAIR_TO] = pair.to
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            defaultBase = this[DEFAULT_BASE] ?: defaults.defaultBase,
            themeMode = this[THEME].toEnum(defaults.themeMode),
            refreshInterval = this[REFRESH].toEnum(defaults.refreshInterval),
            decimalPlaces = this[DECIMALS].toDecimalPlaces(),
        )
    }

    private fun Int?.toDecimalPlaces(): DecimalPlaces = when {
        this == null || this == AUTO_DECIMALS -> DecimalPlaces.Auto
        this in DecimalPlaces.MIN..DecimalPlaces.MAX -> DecimalPlaces.Fixed(this)
        else -> DecimalPlaces.Auto
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun fallbackQuote(from: String): String = if (from == "EUR") "USD" else "EUR"

    private companion object {
        val DEFAULT_BASE = stringPreferencesKey("default_base")
        val THEME = stringPreferencesKey("theme_mode")
        val REFRESH = stringPreferencesKey("refresh_interval")
        val DECIMALS = intPreferencesKey("decimal_places")
        val PAIR_FROM = stringPreferencesKey("pair_from")
        val PAIR_TO = stringPreferencesKey("pair_to")
        const val AUTO_DECIMALS = -1
    }
}
