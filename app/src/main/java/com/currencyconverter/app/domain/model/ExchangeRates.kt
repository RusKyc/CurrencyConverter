package com.currencyconverter.app.domain.model

import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate

/** Precision used for every intermediate financial calculation (34 significant digits). */
val CalculationContext: MathContext = MathContext.DECIMAL128

/** Rate for exactly one unit of [from] expressed in [to]. */
data class ExchangeRate(
    val from: String,
    val to: String,
    val value: BigDecimal,
) {
    /** The rate in the opposite direction: 1 [to] = 1 / [value] [from]. */
    fun inverse(): ExchangeRate {
        require(value.signum() != 0) { "Cannot invert a zero rate" }
        return ExchangeRate(from = to, to = from, value = BigDecimal.ONE.divide(value, CalculationContext))
    }

    fun convert(amount: BigDecimal): BigDecimal = amount.multiply(value, CalculationContext)
}

/**
 * A snapshot of rates as delivered by the provider: every value is the price of one unit of
 * [base] in the given currency. Any pair can be derived from a single snapshot by triangulation,
 * so the app stores one row per currency instead of an N x N matrix.
 */
data class ExchangeRates(
    val base: String,
    private val quotes: Map<String, BigDecimal>,
    val providerDate: LocalDate?,
    val fetchedAt: Instant,
) {
    /** Quotes including the base currency itself (always 1). */
    val rates: Map<String, BigDecimal> = quotes + (base to BigDecimal.ONE)

    val currencies: Set<String> get() = rates.keys

    /** Rate for one unit of [from] in [to] or `null` if the snapshot lacks either currency. */
    fun rate(from: String, to: String): ExchangeRate? {
        if (from == to) return ExchangeRate(from, to, BigDecimal.ONE)
        val fromQuote = rates[from]?.takeIf { it.signum() > 0 } ?: return null
        val toQuote = rates[to]?.takeIf { it.signum() > 0 } ?: return null
        return ExchangeRate(from, to, toQuote.divide(fromQuote, CalculationContext))
    }
}
