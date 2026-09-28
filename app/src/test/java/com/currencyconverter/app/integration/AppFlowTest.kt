package com.currencyconverter.app.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import com.currencyconverter.app.MainActivity
import com.currencyconverter.app.data.local.RateEntity
import com.currencyconverter.app.data.local.RatesDao
import com.currencyconverter.app.data.remote.RatesRemoteDataSource
import com.currencyconverter.app.data.remote.RemoteRates
import com.currencyconverter.app.di.ConnectivityModule
import com.currencyconverter.app.di.DataStoreModule
import com.currencyconverter.app.di.RemoteModule
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.domain.model.RatesException
import com.currencyconverter.app.domain.repository.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.testing.TestInstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences

@Singleton
class ScriptedRemote @Inject constructor() : RatesRemoteDataSource {
    @Volatile
    var failure: RatesException? = null

    @Volatile
    var requests = 0

    override suspend fun fetchLatest(base: String): RemoteRates {
        requests++
        failure?.let { throw it }
        return RemoteRates(
            base = "EUR",
            date = null,
            quotes = mapOf("USD" to BigDecimal("1.25"), "GBP" to BigDecimal("0.8"), "JPY" to BigDecimal("160")),
        )
    }
}

class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RemoteModule::class])
abstract class TestRemoteBinding {
    @Binds
    abstract fun bind(impl: ScriptedRemote): RatesRemoteDataSource
}

@Singleton
class ScriptedNetwork @Inject constructor() : NetworkMonitor {
    val online = MutableStateFlow(true)
    override val isOnline: Flow<Boolean> = online
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ConnectivityModule::class])
abstract class TestConnectivityBinding {
    @Binds
    abstract fun bind(impl: ScriptedNetwork): NetworkMonitor
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStore {
    @Provides
    @Singleton
    fun provide(): DataStore<Preferences> = InMemoryPreferences()
}

/**
 * Boots the real MainActivity with the real Hilt graph, Room database, ViewModels and navigation.
 * Only the network and the preferences file are replaced.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS-w411dp-h2400dp-xxhdpi")
class AppFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var remote: ScriptedRemote

    @Inject lateinit var network: ScriptedNetwork

    @Inject lateinit var ratesDao: RatesDao

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hilt.inject()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `first launch downloads rates and converts the default amount`() {
        launch()

        waitForTag("rate_text")

        compose.onNodeWithTag("pair_title").assertTextEquals("USD → EUR")
        compose.onNodeWithTag("rate_text").assertTextEquals("1 USD = 0.8000 EUR")
        compose.onNodeWithTag("amount_to").assertTextContains("80.00")
        compose.onNodeWithTag("update_status").assertTextEquals("Updated just now")
    }

    @Test
    fun `typing an amount converts immediately and swap keeps the value with its currency`() {
        launch()
        waitForTag("rate_text")

        compose.onNodeWithTag("amount_from").performTextReplacement("250")
        compose.waitUntil(5_000) { hasText("amount_to", "200.00") }

        compose.onNodeWithTag("swap_button").performClick()
        compose.waitUntil(5_000) { hasText("pair_title", "EUR → USD") }

        // 250 still belongs to USD (now the lower row); EUR on top is calculated: 250 USD = 200 EUR.
        compose.onNodeWithTag("amount_to").assertTextContains("250")
        compose.onNodeWithTag("amount_from").assertTextContains("200.00")
    }

    @Test
    fun `start without network and without cache shows a clear error and recovers on retry`() {
        remote.failure = RatesException(RatesError.Network)
        launch()

        waitForTag("rates_error")
        compose.onNodeWithTag("retry_button").assertIsDisplayed()

        remote.failure = null
        compose.onNodeWithTag("retry_button").performClick()

        waitForTag("rate_text")
        compose.onNodeWithTag("rate_text").assertTextEquals("1 USD = 0.8000 EUR")
    }

    @Test
    fun `stored rates are shown when the service is down`() {
        runBlocking {
            val now = Instant.now().toEpochMilli()
            ratesDao.replaceAll(
                listOf(
                    RateEntity("EUR", "1", "EUR", null, now - 10 * 60 * 60 * 1000),
                    RateEntity("USD", "1.25", "EUR", null, now - 10 * 60 * 60 * 1000),
                ),
            )
        }
        remote.failure = RatesException(RatesError.Api(503))
        launch()

        waitForTag("error_banner")

        compose.onNodeWithTag("rate_text").assertTextEquals("1 USD = 0.8000 EUR")
        compose.onNodeWithTag("amount_to").assertTextContains("80.00")
        compose.onNodeWithTag("error_banner").assertIsDisplayed()
    }

    @Test
    fun `offline device labels the data as cached and still converts`() {
        network.online.value = false
        launch()
        waitForTag("rate_text")

        compose.onNodeWithTag("update_status").assertTextContains("Offline · Last updated", substring = true)
        compose.onNodeWithTag("amount_to").assertTextContains("80.00")

        network.online.value = true
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("update_status").assertTextContains("Updated", substring = true) }.isSuccess
        }
    }

    @Test
    fun `favorites can be added from the converter and opened again`() {
        launch()
        waitForTag("rate_text")

        compose.onNodeWithTag("favorite_toggle").performClick()
        compose.onNodeWithTag("nav_favorites").performClick()
        waitForTag("favorite_USD_EUR")
        compose.onNodeWithTag("favorite_USD_EUR").assertIsDisplayed()

        // Select another pair on the converter, then open the favorite to restore USD/EUR.
        compose.onNodeWithTag("nav_home").performClick()
        waitForTag("swap_button")
        compose.onNodeWithTag("swap_button").performClick()
        compose.waitUntil(5_000) { hasText("pair_title", "EUR → USD") }

        compose.onNodeWithTag("nav_favorites").performClick()
        waitForTag("favorite_USD_EUR")
        compose.onNodeWithTag("favorite_USD_EUR").performClick()

        compose.waitUntil(5_000) { hasText("pair_title", "USD → EUR") }
    }

    @Test
    fun `theme choice in settings is kept after the activity is recreated`() {
        launch()
        waitForTag("rate_text")

        compose.onNodeWithTag("nav_settings").performClick()
        waitForTag("theme_Dark")
        compose.onNodeWithTag("theme_Dark").performClick()
        compose.waitUntil(5_000) { isSelected("theme_Dark") }

        scenario!!.recreate()
        compose.onNodeWithTag("nav_settings").performClick()
        waitForTag("theme_Dark")
        compose.onNodeWithTag("theme_Dark").assertIsSelected()
    }

    private fun hasText(tag: String, text: String): Boolean =
        runCatching { compose.onNodeWithTag(tag).assertTextContains(text) }.isSuccess

    private fun isSelected(tag: String): Boolean =
        runCatching { compose.onNodeWithTag(tag).assertIsSelected() }.isSuccess
}
