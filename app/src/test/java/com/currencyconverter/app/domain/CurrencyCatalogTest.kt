package com.currencyconverter.app.domain

import com.currencyconverter.app.domain.currency.CurrencyCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyCatalogTest {

    private val catalog = CurrencyCatalog()

    @Test
    fun `has about 100 currencies`() {
        assertTrue("expected ~100 currencies, was ${catalog.all.size}", catalog.all.size in 95..105)
    }

    @Test
    fun `contains the original starting currencies`() {
        val required = listOf("USD", "EUR", "GBP", "CHF", "JPY", "CNY", "CAD", "AUD", "PLN", "SEK", "NOK", "DKK", "CZK")
        required.forEach { assertNotNull("Missing $it", catalog.find(it)) }
    }

    @Test
    fun `contains the ruble and the rest of BRICS`() {
        val brics = listOf("RUB", "CNY", "INR", "BRL", "ZAR", "EGP", "ETB", "IRR", "AED", "IDR")
        brics.forEach { assertNotNull("Missing BRICS+ currency $it", catalog.find(it)) }
    }

    @Test
    fun `contains every ASEAN member currency`() {
        val asean = listOf("IDR", "MYR", "PHP", "SGD", "THB", "VND", "BND", "KHR", "LAK", "MMK")
        asean.forEach { assertNotNull("Missing ASEAN currency $it", catalog.find(it)) }
    }

    @Test
    fun `codes are unique and known to the JDK`() {
        assertEquals(catalog.all.size, catalog.all.map { it.code }.toSet().size)
        catalog.all.forEach { java.util.Currency.getInstance(it.code) }
    }

    @Test
    fun `country codes are valid two-letter codes`() {
        catalog.all.forEach { currency ->
            assertTrue(
                "Invalid country code '${currency.countryCode}' for ${currency.code}",
                currency.countryCode.length == 2 && currency.countryCode.all { it in 'A'..'Z' },
            )
        }
    }

    @Test
    fun `flags are built from country codes`() {
        assertEquals("🇺🇸", catalog.find("USD")!!.flag)
        assertEquals("🇪🇺", catalog.find("EUR")!!.flag)
        assertEquals("🇷🇺", catalog.find("RUB")!!.flag)
    }

    @Test
    fun `unknown code is not found`() {
        assertNull(catalog.find("XXX"))
    }

    @Test
    fun `zero and three decimal currencies are marked accordingly`() {
        assertEquals(0, catalog.find("JPY")!!.fractionDigits)
        assertEquals(0, catalog.find("KRW")!!.fractionDigits)
        assertEquals(0, catalog.find("VND")!!.fractionDigits)
        assertEquals(3, catalog.find("KWD")!!.fractionDigits)
        assertEquals(3, catalog.find("BHD")!!.fractionDigits)
        assertNull(catalog.find("CHF")!!.symbol)
    }

    @Test
    fun `list order roughly follows economic size, largest first`() {
        val codes = catalog.all.map { it.code }
        assertTrue("USD should lead the list", codes.indexOf("USD") == 0)
        assertTrue("USD should rank above RUB", codes.indexOf("USD") < codes.indexOf("RUB"))
        assertTrue("CNY should rank above RUB", codes.indexOf("CNY") < codes.indexOf("RUB"))
        assertTrue("RUB should rank above smaller economies", codes.indexOf("RUB") < codes.indexOf("ISK"))
    }
}
