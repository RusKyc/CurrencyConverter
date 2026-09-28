package com.currencyconverter.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.currencyconverter.app.R
import com.currencyconverter.app.domain.model.AppInfo
import com.currencyconverter.app.domain.model.AppSettings
import com.currencyconverter.app.domain.model.Currency
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.RefreshInterval
import com.currencyconverter.app.domain.model.ThemeMode
import com.currencyconverter.app.domain.usecase.SearchCurrenciesUseCase
import com.currencyconverter.app.presentation.components.CurrencyPickerContent
import com.currencyconverter.app.presentation.favorites.FavoriteRow
import com.currencyconverter.app.presentation.favorites.FavoritesActions
import com.currencyconverter.app.presentation.favorites.FavoritesContent
import com.currencyconverter.app.presentation.favorites.FavoritesUiState
import com.currencyconverter.app.presentation.settings.SettingsContent
import com.currencyconverter.app.presentation.settings.SettingsUiState
import com.currencyconverter.app.presentation.theme.CurrencyConverterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w411dp-h2400dp-xxhdpi")
class FavoritesPickerSettingsTest {

    @get:Rule
    val rule = createComposeRule()

    private val search = SearchCurrenciesUseCase()

    // ---------- Favorites ----------

    private fun row(from: String, to: String, up: Boolean, down: Boolean) = FavoriteRow(
        pair = CurrencyPair(from, to),
        from = catalog.find(from)!!,
        to = catalog.find(to)!!,
        rate = "0.8642",
        canMoveUp = up,
        canMoveDown = down,
    )

    private fun showFavorites(state: FavoritesUiState, actions: FavoritesActions = FavoritesActions()) {
        rule.setContent {
            CurrencyConverterTheme(darkTheme = false) {
                FavoritesContent(state = state, currencies = catalog.all, actions = actions.copy(search = { search(it, catalog.all) }))
            }
        }
    }

    @Test
    fun `empty favorites explain how to add a pair`() {
        showFavorites(FavoritesUiState(isLoaded = true))

        rule.onNodeWithTag("favorites_empty").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.favorites_empty_title)).assertIsDisplayed()
    }

    @Test
    fun `favorites are listed with their rate`() {
        showFavorites(
            FavoritesUiState(
                rows = listOf(row("USD", "EUR", up = false, down = true), row("EUR", "GBP", up = true, down = false)),
                isLoaded = true,
            ),
        )

        rule.onNodeWithTag("favorite_USD_EUR").assertTextContains("USD/EUR")
        rule.onNodeWithTag("favorite_EUR_GBP").assertTextContains("EUR/GBP")
    }

    @Test
    fun `tapping a pair opens it`() {
        var opened: CurrencyPair? = null
        showFavorites(
            FavoritesUiState(rows = listOf(row("USD", "EUR", up = false, down = false)), isLoaded = true),
            FavoritesActions(onOpen = { opened = it }),
        )

        rule.onNodeWithTag("favorite_USD_EUR").performClick()

        assertEquals(CurrencyPair("USD", "EUR"), opened)
    }

    @Test
    fun `reorder and remove buttons report the pair and respect the list edges`() {
        val moved = mutableListOf<String>()
        showFavorites(
            FavoritesUiState(
                rows = listOf(row("USD", "EUR", up = false, down = true), row("EUR", "GBP", up = true, down = false)),
                isLoaded = true,
            ),
            FavoritesActions(
                onMoveUp = { moved += "up ${it.from}${it.to}" },
                onMoveDown = { moved += "down ${it.from}${it.to}" },
                onRemove = { moved += "remove ${it.from}${it.to}" },
            ),
        )

        rule.onNodeWithTag("move_up_USD_EUR").assertIsNotEnabled()
        rule.onNodeWithTag("move_down_EUR_GBP").assertIsNotEnabled()
        rule.onNodeWithTag("move_down_USD_EUR").assertIsEnabled().performClick()
        rule.onNodeWithTag("move_up_EUR_GBP").assertIsEnabled().performClick()
        rule.onNodeWithTag("remove_EUR_GBP").performClick()

        assertEquals(listOf("down USDEUR", "up EURGBP", "remove EURGBP"), moved)
    }

    @Test
    fun `add pair dialog adds the chosen currencies`() {
        var added: Pair<Currency, Currency>? = null
        showFavorites(
            FavoritesUiState(isLoaded = true),
            FavoritesActions(onAdd = { from, to -> added = from to to }),
        )

        rule.onNodeWithTag("add_pair_fab").performClick()
        rule.onNodeWithText(string(R.string.add_pair_title)).assertIsDisplayed()
        rule.onNodeWithTag("add_pair_confirm").performClick()

        // Dialog defaults to the first two currencies of the catalog.
        assertEquals(catalog.all[0] to catalog.all[1], added)
    }

    // ---------- Currency picker ----------

    private fun showPicker(selected: String? = "USD", onSelect: (Currency) -> Unit = {}) {
        rule.setContent {
            CurrencyConverterTheme(darkTheme = false) {
                CurrencyPickerContent(
                    currencies = catalog.all,
                    selectedCode = selected,
                    search = { search(it, catalog.all) },
                    onSelect = onSelect,
                )
            }
        }
    }

    @Test
    fun `picker lists the starting currencies`() {
        Locale.setDefault(Locale.US)
        showPicker()

        listOf("USD", "EUR", "GBP", "CHF", "JPY").forEach { rule.onNodeWithTag("currency_$it").assertIsDisplayed() }
        rule.onNodeWithTag("currency_EUR").assertTextContains("Euro")
    }

    @Test
    fun `picker search filters the list`() {
        showPicker()

        rule.onNodeWithTag("picker_search").performTextInput("eur")

        rule.onNodeWithTag("currency_EUR").assertIsDisplayed()
        rule.onNodeWithTag("currency_USD").assertDoesNotExist()
    }

    @Test
    fun `picker shows a message when nothing matches`() {
        showPicker()

        rule.onNodeWithTag("picker_search").performTextInput("zzzz")

        rule.onNodeWithTag("picker_empty").assertIsDisplayed()
    }

    @Test
    fun `picker reports the chosen currency`() {
        var chosen: Currency? = null
        showPicker(onSelect = { chosen = it })

        rule.onNodeWithTag("currency_GBP").performClick()

        assertEquals("GBP", chosen?.code)
    }

    // ---------- Settings ----------

    private fun showSettings(
        settings: AppSettings = AppSettings(),
        onTheme: (ThemeMode) -> Unit = {},
        onInterval: (RefreshInterval) -> Unit = {},
        onDecimals: (DecimalPlaces) -> Unit = {},
        onBase: () -> Unit = {},
    ) {
        rule.setContent {
            CurrencyConverterTheme(darkTheme = false) {
                SettingsContent(
                    state = SettingsUiState(settings, catalog.find(settings.defaultBase)!!, AppInfo("1.0.0", "ExchangeRate-API", "https://www.exchangerate-api.com")),
                    refreshIntervals = RefreshInterval.entries,
                    decimalOptions = listOf(DecimalPlaces.Auto) + (0..6).map { DecimalPlaces.Fixed(it) },
                    onPickBaseCurrency = onBase,
                    onThemeSelected = onTheme,
                    onRefreshIntervalSelected = onInterval,
                    onDecimalPlacesSelected = onDecimals,
                )
            }
        }
    }

    @Test
    fun `settings shows base currency, version and data source`() {
        showSettings()

        rule.onNodeWithTag("default_base_row").assertTextContains("USD")
        rule.onNodeWithTag("app_version").assertTextContains("1.0.0", substring = true)
        rule.onNodeWithTag("data_source").assertTextContains("ExchangeRate-API", substring = true)
    }

    @Test
    fun `settings report theme, interval and decimal choices`() {
        var theme: ThemeMode? = null
        var interval: RefreshInterval? = null
        var decimals: DecimalPlaces? = null
        var baseClicks = 0
        showSettings(
            onTheme = { theme = it },
            onInterval = { interval = it },
            onDecimals = { decimals = it },
            onBase = { baseClicks++ },
        )

        rule.onNodeWithTag("theme_Dark").performClick()
        rule.onNodeWithTag("interval_Hourly").performClick()
        rule.onNodeWithTag("decimals_4").performClick()
        rule.onNodeWithTag("default_base_row").performClick()

        assertEquals(ThemeMode.Dark, theme)
        assertEquals(RefreshInterval.Hourly, interval)
        assertEquals(DecimalPlaces.Fixed(4), decimals)
        assertEquals(1, baseClicks)
    }
}
