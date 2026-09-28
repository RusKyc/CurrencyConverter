package com.currencyconverter.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.currencyconverter.app.domain.currency.CurrencyCatalog
import com.currencyconverter.app.domain.model.AppInfo
import com.currencyconverter.app.domain.model.AppSettings
import com.currencyconverter.app.domain.model.Currency
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.RefreshInterval
import com.currencyconverter.app.domain.model.ThemeMode
import com.currencyconverter.app.domain.repository.SettingsRepository
import com.currencyconverter.app.domain.usecase.SearchCurrenciesUseCase
import com.currencyconverter.app.domain.usecase.SetDefaultBaseCurrencyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings,
    val baseCurrency: Currency,
    val appInfo: AppInfo,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val setDefaultBaseCurrency: SetDefaultBaseCurrencyUseCase,
    private val searchCurrencies: SearchCurrenciesUseCase,
    private val catalog: CurrencyCatalog,
    private val appInfo: AppInfo,
) : ViewModel() {

    val currencies: List<Currency> get() = catalog.all

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { SettingsUiState(it, resolve(it.defaultBase), appInfo) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AppSettings().let { SettingsUiState(it, resolve(it.defaultBase), appInfo) },
        )

    val refreshIntervals: List<RefreshInterval> = RefreshInterval.entries

    val decimalOptions: List<DecimalPlaces> =
        listOf(DecimalPlaces.Auto) + (DecimalPlaces.MIN..DecimalPlaces.MAX).map { DecimalPlaces.Fixed(it) }

    fun onDefaultBaseSelected(currency: Currency) {
        viewModelScope.launch { setDefaultBaseCurrency(currency.code) }
    }

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun onRefreshIntervalSelected(interval: RefreshInterval) {
        viewModelScope.launch { settingsRepository.setRefreshInterval(interval) }
    }

    fun onDecimalPlacesSelected(places: DecimalPlaces) {
        viewModelScope.launch { settingsRepository.setDecimalPlaces(places) }
    }

    fun search(query: String): List<Currency> = searchCurrencies(query, catalog.all)

    private fun resolve(code: String): Currency = catalog.find(code) ?: catalog.all.first()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
