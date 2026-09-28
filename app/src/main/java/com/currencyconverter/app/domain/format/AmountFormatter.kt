package com.currencyconverter.app.domain.format

import com.currencyconverter.app.domain.model.Currency
import com.currencyconverter.app.domain.model.DecimalPlaces
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject

/**
 * Turns exact [BigDecimal] values into text. Rounding happens here and only here, at the very
 * end of the pipeline, so 100 USD -> 86.42 EUR instead of 86.4199999997 EUR.
 */
class AmountFormatter @Inject constructor() {

    fun fractionDigits(currency: Currency, places: DecimalPlaces): Int = when (places) {
        DecimalPlaces.Auto -> currency.fractionDigits
        is DecimalPlaces.Fixed -> places.count
    }

    /** Display text, e.g. `1,234.50`. */
    fun formatAmount(
        value: BigDecimal,
        currency: Currency,
        places: DecimalPlaces,
        locale: Locale = Locale.getDefault(),
    ): String = format(value, fractionDigits(currency, places), grouping = true, locale = locale)

    /** Text for an input field: no grouping separators, so the user can keep editing it. */
    fun formatEditable(
        value: BigDecimal,
        currency: Currency,
        places: DecimalPlaces,
        locale: Locale = Locale.getDefault(),
    ): String = format(value, fractionDigits(currency, places), grouping = false, locale = locale)

    /**
     * Exchange rate with about five significant digits: 0.8726, 157.89, 0.006333.
     */
    fun formatRate(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        if (value.signum() <= 0) return format(BigDecimal.ZERO, 4, grouping = true, locale = locale)
        val exponent = value.precision() - value.scale() - 1
        val digits = if (exponent >= 0) maxOf(2, 4 - exponent) else minOf(4 - (exponent + 1), MAX_RATE_DIGITS)
        return format(value, digits, grouping = true, locale = locale)
    }

    private fun format(value: BigDecimal, digits: Int, grouping: Boolean, locale: Locale): String {
        val formatter = DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale)).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = grouping
        }
        return formatter.format(value)
    }

    private companion object {
        const val MAX_RATE_DIGITS = 10
    }
}
