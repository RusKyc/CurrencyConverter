package com.currencyconverter.app.domain.model

import java.util.Locale

/**
 * A currency known to the app.
 *
 * Only the ISO code, the country used for the flag, the symbol and the natural number of
 * fraction digits are stored here. The human readable name is resolved from the JDK so it is
 * localised automatically. New currencies are added in [com.currencyconverter.app.domain.currency.CurrencyCatalog].
 */
data class Currency(
    val code: String,
    val countryCode: String,
    val symbol: String? = null,
    val fractionDigits: Int = 2,
) {
    /** Flag built from regional indicator symbols: no images, no network, no extra resources. */
    val flag: String = countryCode.toFlagEmoji()

    val displayName: String
        get() = runCatching {
            java.util.Currency.getInstance(code).getDisplayName(Locale.getDefault())
        }.getOrDefault(code)
}

private fun String.toFlagEmoji(): String {
    if (length != 2 || any { it !in 'A'..'Z' }) return ""
    val base = 0x1F1E6 - 'A'.code
    return buildString {
        this@toFlagEmoji.forEach { appendCodePoint(base + it.code) }
    }
}
