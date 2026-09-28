package com.currencyconverter.app.domain

import com.currencyconverter.app.domain.model.ExchangeRate
import com.currencyconverter.app.domain.model.ExchangeRates
import com.currencyconverter.app.testing.sampleRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class ExchangeRatesTest {

    @Test
    fun `base currency is always part of the snapshot with rate one`() {
        assertEquals(BigDecimal.ONE, sampleRates().rates["EUR"])
    }

    @Test
    fun `direct rate from the base equals the stored quote`() {
        assertEquals(0, BigDecimal("1.25").compareTo(sampleRates().rate("EUR", "USD")!!.value))
    }

    @Test
    fun `cross rate is derived by triangulation`() {
        // 1 USD = 1/1.25 EUR = 0.8 EUR; 1 EUR = 0.8 GBP  ->  1 USD = 0.64 GBP
        val rate = sampleRates().rate("USD", "GBP")!!
        assertEquals(0, BigDecimal("0.64").compareTo(rate.value))
    }

    @Test
    fun `inverse rate is the reciprocal`() {
        val rate = sampleRates().rate("USD", "GBP")!!
        val inverse = rate.inverse()

        assertEquals("GBP", inverse.from)
        assertEquals("USD", inverse.to)
        assertEquals(0, BigDecimal("1.5625").compareTo(inverse.value))
    }

    @Test
    fun `inverse of inverse returns the original rate`() {
        val rate = ExchangeRate("USD", "JPY", BigDecimal("157.89"))
        assertEquals(0, rate.value.compareTo(rate.inverse().inverse().value.setScale(2, java.math.RoundingMode.HALF_UP)))
    }

    @Test
    fun `direct and reversed lookups are consistent`() {
        val rates = sampleRates()
        val forward = rates.rate("USD", "JPY")!!
        val backward = rates.rate("JPY", "USD")!!
        // forward * backward == 1 up to the calculation precision
        val product = forward.value.multiply(backward.value)
        assertTrue(product.subtract(BigDecimal.ONE).abs() < BigDecimal("1e-30"))
    }

    @Test
    fun `same currency has rate one`() {
        assertEquals(BigDecimal.ONE, sampleRates().rate("GBP", "GBP")!!.value)
    }

    @Test
    fun `unknown currency yields no rate`() {
        assertNull(sampleRates().rate("USD", "XXX"))
        assertNull(sampleRates().rate("XXX", "USD"))
    }

    @Test
    fun `non positive quotes are ignored instead of dividing by zero`() {
        val rates = ExchangeRates("EUR", mapOf("USD" to BigDecimal.ZERO, "GBP" to BigDecimal("0.8")), null, Instant.EPOCH)
        assertNull(rates.rate("USD", "GBP"))
        assertNull(rates.rate("GBP", "USD"))
        assertNotNull(rates.rate("EUR", "GBP"))
    }

    @Test
    fun `inverse of a zero rate is rejected`() {
        val result = runCatching { ExchangeRate("A", "B", BigDecimal.ZERO).inverse() }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
