package com.currencyconverter.app.domain.model

/** Ordered pair of ISO currency codes: amounts are converted from [from] to [to]. */
data class CurrencyPair(val from: String, val to: String) {

    fun reversed(): CurrencyPair = CurrencyPair(from = to, to = from)

    /** Selects [code] as the source currency. If it is already the target, the sides are swapped. */
    fun withFrom(code: String): CurrencyPair =
        if (code == to) CurrencyPair(from = to, to = from) else copy(from = code)

    /** Selects [code] as the target currency. If it is already the source, the sides are swapped. */
    fun withTo(code: String): CurrencyPair =
        if (code == from) CurrencyPair(from = to, to = from) else copy(to = code)
}
