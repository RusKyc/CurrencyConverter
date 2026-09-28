package com.currencyconverter.app.domain.format

import java.math.BigDecimal

/** Parsing and sanitising of the text the user types into an amount field. */
object AmountInput {

    private const val MAX_INTEGER_DIGITS = 12
    private const val MAX_FRACTION_DIGITS = 8

    /**
     * Keeps digits and at most one decimal separator ('.' or ','), and limits the length so the
     * value can never overflow anything. Everything else is dropped.
     */
    fun sanitize(raw: String): String {
        val result = StringBuilder()
        var separatorSeen = false
        var integerDigits = 0
        var fractionDigits = 0
        for (char in raw) {
            when {
                char.isAsciiDigit() -> {
                    if (separatorSeen) {
                        if (fractionDigits < MAX_FRACTION_DIGITS) {
                            result.append(char)
                            fractionDigits++
                        }
                    } else if (integerDigits < MAX_INTEGER_DIGITS) {
                        result.append(char)
                        integerDigits++
                    }
                }
                (char == '.' || char == ',') && !separatorSeen -> {
                    separatorSeen = true
                    result.append(char)
                }
            }
        }
        return result.toString()
    }

    /** Exact decimal value of [text] or `null` when it is empty or has no digits. */
    fun parse(text: String): BigDecimal? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.none { it.isAsciiDigit() }) return null
        return normalized.toBigDecimalOrNull()
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
