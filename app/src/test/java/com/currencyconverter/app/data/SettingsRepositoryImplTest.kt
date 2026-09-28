package com.currencyconverter.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.currencyconverter.app.data.repository.SettingsRepositoryImpl
import com.currencyconverter.app.domain.model.AppSettings
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.RefreshInterval
import com.currencyconverter.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same contract as the file-backed store, without file IO (which is flaky on Windows CI machines). */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class SettingsRepositoryImplTest {

    private fun newDataStore(): DataStore<Preferences> = InMemoryPreferencesDataStore()

    private fun newRepository() = SettingsRepositoryImpl(newDataStore())

    @Test
    fun `defaults are used on first launch`() = runTest {
        val repository = newRepository()

        assertEquals(AppSettings(), repository.settings.first())
        assertEquals(CurrencyPair("USD", "EUR"), repository.converterPair.first())
    }

    @Test
    fun `every setting is stored and read back`() = runTest {
        val dataStore = newDataStore()
        val repository = SettingsRepositoryImpl(dataStore)

        repository.setDefaultBase("GBP")
        repository.setThemeMode(ThemeMode.Dark)
        repository.setRefreshInterval(RefreshInterval.Hourly)
        repository.setDecimalPlaces(DecimalPlaces.Fixed(4))

        assertEquals(
            AppSettings("GBP", ThemeMode.Dark, RefreshInterval.Hourly, DecimalPlaces.Fixed(4)),
            repository.settings.first(),
        )

        repository.setDecimalPlaces(DecimalPlaces.Auto)
        assertEquals(DecimalPlaces.Auto, repository.settings.first().decimalPlaces)
    }

    @Test
    fun `converter pair is stored`() = runTest {
        val repository = newRepository()
        repository.setConverterPair(CurrencyPair("EUR", "JPY"))

        assertEquals(CurrencyPair("EUR", "JPY"), repository.converterPair.first())
    }

    @Test
    fun `converter pair starts from the default base until a pair is chosen`() = runTest {
        val repository = newRepository()
        repository.setDefaultBase("CHF")

        assertEquals(CurrencyPair("CHF", "EUR"), repository.converterPair.first())
    }

    @Test
    fun `identical stored sides are repaired`() = runTest {
        val repository = newRepository()
        repository.setConverterPair(CurrencyPair("EUR", "EUR"))

        assertEquals(CurrencyPair("EUR", "USD"), repository.converterPair.first())
    }

    @Test
    fun `unknown stored values fall back to defaults instead of crashing`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit {
            it[stringPreferencesKey("theme_mode")] = "Neon"
            it[stringPreferencesKey("refresh_interval")] = "EveryMinute"
        }

        assertEquals(AppSettings(), SettingsRepositoryImpl(dataStore).settings.first())
    }
}
