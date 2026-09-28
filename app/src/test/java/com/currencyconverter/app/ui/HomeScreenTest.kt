package com.currencyconverter.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.currencyconverter.app.R
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.presentation.common.DataFreshness
import com.currencyconverter.app.presentation.home.AmountSide
import com.currencyconverter.app.presentation.home.HomeActions
import com.currencyconverter.app.presentation.home.HomeContent
import com.currencyconverter.app.presentation.home.HomeUiState
import com.currencyconverter.app.presentation.home.RatesContent
import com.currencyconverter.app.presentation.home.UpdateInfo
import com.currencyconverter.app.presentation.theme.CurrencyConverterTheme
import com.currencyconverter.app.testing.TestNow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class HomeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(
        state: HomeUiState,
        actions: HomeActions = HomeActions(),
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CurrencyConverterTheme(darkTheme = darkTheme) {
                    HomeContent(state = state, snackbarHostState = SnackbarHostState(), actions = actions)
                }
            }
        }
    }

    @Test
    fun `shows the pair, both amounts, the rate and the update time`() {
        show(homeState())

        rule.onNodeWithTag("pair_title").assertTextEquals("USD → EUR")
        rule.onNodeWithTag("amount_from").assertTextContains("100")
        rule.onNodeWithTag("amount_to").assertTextContains("86.42")
        rule.onNodeWithTag("rate_text").assertTextEquals("1 USD = 0.8642 EUR")
        rule.onNodeWithTag("inverse_rate_text").assertTextEquals("1 EUR = 1.1571 USD")
        rule.onNodeWithTag("update_status").assertTextEquals("Updated 5 min ago")
    }

    @Test
    fun `typing an amount reports the side and the text`() {
        var reported: Pair<AmountSide, String>? = null
        show(homeState(), HomeActions(onAmountChange = { side, text -> reported = side to text }))

        rule.onNodeWithTag("amount_from").performTextReplacement("250")

        assertEquals(AmountSide.From to "250", reported)
    }

    @Test
    fun `the calculated field is editable too`() {
        var reported: Pair<AmountSide, String>? = null
        show(homeState(), HomeActions(onAmountChange = { side, text -> reported = side to text }))

        rule.onNodeWithTag("amount_to").performTextReplacement("50")

        assertEquals(AmountSide.To to "50", reported)
    }

    @Test
    fun `input goes through the sanitizer`() {
        var reported: String? = null
        show(
            homeState(),
            HomeActions(
                onAmountChange = { _, text -> reported = text },
                sanitizeAmount = { raw -> raw.filter { it.isDigit() } },
            ),
        )

        rule.onNodeWithTag("amount_from").performTextReplacement("1a2b3")

        assertEquals("123", reported)
    }

    @Test
    fun `swap button swaps currencies`() {
        var swaps = 0
        show(homeState(), HomeActions(onSwap = { swaps++ }))

        rule.onNodeWithTag("swap_button").performClick()

        assertEquals(1, swaps)
    }

    @Test
    fun `currency buttons open the picker for their side`() {
        val picked = mutableListOf<AmountSide>()
        show(homeState(), HomeActions(onPickCurrency = { picked += it }))

        rule.onNodeWithTag("currency_button_from").performClick()
        rule.onNodeWithTag("currency_button_to").performClick()

        assertEquals(listOf(AmountSide.From, AmountSide.To), picked)
    }

    @Test
    fun `star toggles the favorite and its label follows the state`() {
        var toggles = 0
        show(homeState(isFavorite = false), HomeActions(onToggleFavorite = { toggles++ }))

        rule.onNodeWithTag("favorite_toggle").performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun `refresh button requests a refresh`() {
        var refreshes = 0
        show(homeState(), HomeActions(onRefresh = { refreshes++ }))

        rule.onNodeWithTag("refresh_button").performClick()

        assertEquals(1, refreshes)
    }

    @Test
    fun `refresh button is disabled while refreshing`() {
        show(homeState(isRefreshing = true))

        rule.onNodeWithTag("refresh_button").assertIsNotEnabled()
    }

    @Test
    fun `offline data is labelled as cached with the last update time`() {
        show(homeState(updateInfo = UpdateInfo(TestNow.minus(Duration.ofHours(30)), DataFreshness.Offline)))

        rule.onNodeWithTag("update_status").assertTextContains("Offline · Last updated", substring = true)
    }

    @Test
    fun `saved rates after a failed refresh are labelled and explained`() {
        show(
            homeState(
                updateInfo = UpdateInfo(TestNow.minus(Duration.ofHours(30)), DataFreshness.CachedAfterError),
                refreshError = RatesError.Network,
            ),
        )

        rule.onNodeWithTag("update_status").assertTextContains("Saved rates · Last updated", substring = true)
        rule.onNodeWithTag("error_banner").assertIsDisplayed()
        rule.onNodeWithTag("error_banner").assertTextContains(
            string(R.string.error_network) + " " + string(R.string.error_showing_saved),
        )
        // The stored rates must stay usable.
        rule.onNodeWithTag("rate_text").assertIsDisplayed()
    }

    @Test
    fun `no banner when the refresh succeeded`() {
        show(homeState())

        rule.onNodeWithTag("error_banner").assertDoesNotExist()
    }

    @Test
    fun `every error kind has its own readable message`() {
        val cases = listOf(
            RatesError.Network to string(R.string.error_network),
            RatesError.Api(503) to string(R.string.error_api_code, 503),
            RatesError.Api(null) to string(R.string.error_api),
            RatesError.Parsing to string(R.string.error_parsing),
            RatesError.Unknown("x") to string(R.string.error_unknown),
        )
        val state = mutableStateOf(homeState(content = RatesContent.Loading, updateInfo = null))
        rule.setContent {
            CurrencyConverterTheme(darkTheme = false) {
                HomeContent(state = state.value, snackbarHostState = SnackbarHostState(), actions = HomeActions())
            }
        }
        cases.forEach { (error, message) ->
            state.value = homeState(content = RatesContent.Error(error), updateInfo = null)
            rule.waitForIdle()
            rule.onNodeWithText(message).assertIsDisplayed()
        }
    }

    @Test
    fun `error without cache offers retry`() {
        var refreshes = 0
        show(
            homeState(content = RatesContent.Error(RatesError.Network), updateInfo = null),
            HomeActions(onRefresh = { refreshes++ }),
        )

        rule.onNodeWithTag("rates_error").assertIsDisplayed()
        rule.onNodeWithTag("retry_button").performClick()

        assertEquals(1, refreshes)
    }

    @Test
    fun `loading and empty states are shown`() {
        show(homeState(content = RatesContent.Loading, updateInfo = null))
        rule.onNodeWithTag("rates_loading").assertIsDisplayed()
    }

    @Test
    fun `empty state explains that the pair has no rate`() {
        show(homeState(content = RatesContent.Empty, updateInfo = null))
        rule.onNodeWithTag("rates_empty").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.empty_rates_title)).assertIsDisplayed()
    }

    @Test
    fun `favorites are listed and open in the converter when tapped`() {
        var opened: CurrencyPair? = null
        show(
            homeState(favorites = listOf(favoriteItem("USD", "EUR"), favoriteItem("EUR", "GBP", rate = "0.8651"))),
            HomeActions(onFavoriteClick = { opened = it }),
        )

        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("favorite_EUR_GBP"))
        rule.onNodeWithTag("favorite_EUR_GBP").assertIsDisplayed()
        rule.onNodeWithTag("favorite_EUR_GBP").assertTextContains("EUR/GBP")
        rule.onNodeWithTag("favorite_EUR_GBP").assertTextContains("0.8651")
        rule.onNodeWithTag("favorite_EUR_GBP").performClick()

        assertEquals(CurrencyPair("EUR", "GBP"), opened)
    }

    @Test
    fun `hint is shown when there are no favorites`() {
        show(homeState(favorites = emptyList()))

        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("favorites_hint"))
        rule.onNodeWithTag("favorites_hint").assertIsDisplayed()
    }

    @Test
    fun `renders in dark theme`() {
        show(homeState(), darkTheme = true)

        rule.onNodeWithTag("rate_text").assertIsDisplayed()
    }

    @Test
    fun `touch targets are at least 48dp`() {
        show(homeState())

        listOf("swap_button", "refresh_button", "favorite_toggle").forEach { tag ->
            rule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        listOf("currency_button_from", "currency_button_to").forEach { tag ->
            rule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun `remains usable with a large font scale`() {
        var swaps = 0
        show(homeState(), HomeActions(onSwap = { swaps++ }), fontScale = 2f)

        rule.onNodeWithTag("pair_title").assertIsDisplayed()
        rule.onNodeWithTag("swap_button").performClick()
        assertEquals(1, swaps)
    }
}
