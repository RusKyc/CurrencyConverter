package com.currencyconverter.app.domain

import com.currencyconverter.app.domain.format.AmountInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AmountInputTest {

    @Test
    fun `sanitize keeps digits and one separator`() {
        assertEquals("12.5", AmountInput.sanitize("12.5"))
        assertEquals("12,5", AmountInput.sanitize("12,5"))
        assertEquals("12.57", AmountInput.sanitize("12.5.7"))
        assertEquals("1.25", AmountInput.sanitize("1.2,5"))
    }

    @Test
    fun `sanitize drops everything else`() {
        assertEquals("100", AmountInput.sanitize("1a0 0-"))
        assertEquals("", AmountInput.sanitize("abc"))
        assertEquals("5", AmountInput.sanitize("+5"))
    }

    @Test
    fun `sanitize limits the size of the number`() {
        assertEquals("123456789012", AmountInput.sanitize("1234567890123456"))
        assertEquals("1.12345678", AmountInput.sanitize("1.123456789"))
    }

    @Test
    fun `sanitize is idempotent`() {
        val once = AmountInput.sanitize("9876.54321")
        assertEquals(once, AmountInput.sanitize(once))
    }

    @Test
    fun `parse understands both separators`() {
        assertEquals(BigDecimal("12.5"), AmountInput.parse("12.5"))
        assertEquals(BigDecimal("12.5"), AmountInput.parse("12,5"))
        assertEquals(BigDecimal("0.5"), AmountInput.parse(".5"))
        assertEquals(BigDecimal("5"), AmountInput.parse("5."))
    }

    @Test
    fun `parse returns null for empty or digitless text`() {
        assertNull(AmountInput.parse(""))
        assertNull(AmountInput.parse("   "))
        assertNull(AmountInput.parse("."))
        assertNull(AmountInput.parse(","))
    }

    @Test
    fun `parse keeps the exact decimal value`() {
        assertEquals(BigDecimal("0.1"), AmountInput.parse("0.1"))
        assertEquals("0.30000000", AmountInput.parse("0.30000000")!!.toPlainString())
    }
}
