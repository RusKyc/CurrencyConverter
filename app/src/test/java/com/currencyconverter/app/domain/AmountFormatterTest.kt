package com.currencyconverter.app.domain

import com.currencyconverter.app.domain.currency.CurrencyCatalog
import com.currencyconverter.app.domain.format.AmountFormatter
import com.currencyconverter.app.domain.model.DecimalPlaces
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class AmountFormatterTest {

    private val formatter = AmountFormatter()
    private val catalog = CurrencyCatalog()
    private val eur = catalog.find("EUR")!!
    private val jpy = catalog.find("JPY")!!

    @Test
    fun `auto uses two decimals for regular currencies`() {
        assertEquals("86.42", formatter.formatAmount(BigDecimal("86.4199999997"), eur, DecimalPlaces.Auto, Locale.US))
    }

    @Test
    fun `auto uses zero decimals for yen`() {
        assertEquals("15,789", formatter.formatAmount(BigDecimal("15788.6"), jpy, DecimalPlaces.Auto, Locale.US))
    }

    @Test
    fun `fixed decimals override the currency default`() {
        assertEquals("86.4200", formatter.formatAmount(BigDecimal("86.42"), eur, DecimalPlaces.Fixed(4), Locale.US))
        assertEquals("86", formatter.formatAmount(BigDecimal("86.42"), eur, DecimalPlaces.Fixed(0), Locale.US))
    }

    @Test
    fun `rounding is half up on the exact decimal value`() {
        assertEquals("0.13", formatter.formatAmount(BigDecimal("0.125"), eur, DecimalPlaces.Auto, Locale.US))
        assertEquals("1.01", formatter.formatAmount(BigDecimal("1.005"), eur, DecimalPlaces.Auto, Locale.US))
    }

    @Test
    fun `grouping is applied for display but not for editable text`() {
        assertEquals("1,234,567.50", formatter.formatAmount(BigDecimal("1234567.5"), eur, DecimalPlaces.Auto, Locale.US))
        assertEquals("1234567.50", formatter.formatEditable(BigDecimal("1234567.5"), eur, DecimalPlaces.Auto, Locale.US))
    }

    @Test
    fun `decimal separator follows the locale`() {
        assertEquals("86,42", formatter.formatEditable(BigDecimal("86.42"), eur, DecimalPlaces.Auto, Locale.GERMANY))
    }

    @Test
    fun `very large amounts keep every digit`() {
        assertEquals(
            "999999999999.99",
            formatter.formatEditable(BigDecimal("999999999999.99"), eur, DecimalPlaces.Auto, Locale.US),
        )
    }

    @Test
    fun `rate uses about five significant digits`() {
        assertEquals("0.8726", formatter.formatRate(BigDecimal("0.87259999999999999999"), Locale.US))
        assertEquals("157.89", formatter.formatRate(BigDecimal("157.8899999"), Locale.US))
        assertEquals("6.6976", formatter.formatRate(BigDecimal("6.6976"), Locale.US))
        assertEquals("21.238", formatter.formatRate(BigDecimal("21.238"), Locale.US))
    }

    @Test
    fun `small rates gain digits so they do not collapse to zero`() {
        // 1 JPY in USD
        assertEquals("0.006333", formatter.formatRate(BigDecimal("0.0063334"), Locale.US))
    }

    @Test
    fun `large rates never lose the integer part`() {
        assertEquals("15,789.00", formatter.formatRate(BigDecimal("15789"), Locale.US))
    }

    @Test
    fun `zero and negative rates are shown as zero`() {
        assertEquals("0.0000", formatter.formatRate(BigDecimal.ZERO, Locale.US))
        assertEquals("0.0000", formatter.formatRate(BigDecimal("-1"), Locale.US))
    }
}
