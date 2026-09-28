package com.currencyconverter.app.domain

import com.currencyconverter.app.domain.format.AmountFormatter
import com.currencyconverter.app.domain.currency.CurrencyCatalog
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.ExchangeRates
import com.currencyconverter.app.domain.usecase.ConvertCurrencyUseCase
import com.currencyconverter.app.testing.sampleRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale

class ConvertCurrencyUseCaseTest {

    private val convert = ConvertCurrencyUseCase()
    private val formatter = AmountFormatter()
    private val catalog = CurrencyCatalog()

    @Test
    fun `converts through the base currency`() {
        val conversion = convert(BigDecimal("100"), "USD", "GBP", sampleRates())!!
        assertEquals(0, BigDecimal("64").compareTo(conversion.result))
    }

    @Test
    fun `zero amount converts to zero`() {
        val conversion = convert(BigDecimal.ZERO, "USD", "GBP", sampleRates())!!
        assertEquals(0, BigDecimal.ZERO.compareTo(conversion.result))
    }

    @Test
    fun `returns null when the pair cannot be priced`() {
        assertNull(convert(BigDecimal.TEN, "USD", "XXX", sampleRates()))
    }

    @Test
    fun `converting there and back returns the original amount after rounding`() {
        val there = convert(BigDecimal("123.45"), "USD", "JPY", sampleRates())!!
        val back = convert(there.result, "JPY", "USD", sampleRates())!!
        assertEquals("123.45", formatter.formatEditable(back.result, catalog.find("USD")!!, DecimalPlaces.Auto, Locale.US))
    }

    @Test
    fun `non terminating rates do not leak floating point noise into the display`() {
        // 1 EUR = 3 XXX  ->  1 XXX = 0.3333... EUR; three of them must display as exactly 1.00
        val rates = ExchangeRates("EUR", mapOf("XXX" to BigDecimal("3")), null, Instant.EPOCH)
        val conversion = convert(BigDecimal("3"), "XXX", "EUR", rates)!!

        assertEquals("1.00", formatter.formatEditable(conversion.result, catalog.find("EUR")!!, DecimalPlaces.Auto, Locale.US))
    }

    @Test
    fun `real world example rounds to two decimals`() {
        // Frankfurter data of 2026-09-18: 1 EUR = 1.146... USD, expressed as USD -> EUR = 0.8726
        val rates = ExchangeRates(
            base = "EUR",
            quotes = mapOf("USD" to BigDecimal("1").divide(BigDecimal("0.8726"), java.math.MathContext.DECIMAL128)),
            providerDate = null,
            fetchedAt = Instant.EPOCH,
        )
        val conversion = convert(BigDecimal("100"), "USD", "EUR", rates)!!

        assertEquals("87.26", formatter.formatEditable(conversion.result, catalog.find("EUR")!!, DecimalPlaces.Auto, Locale.US))
    }
}
