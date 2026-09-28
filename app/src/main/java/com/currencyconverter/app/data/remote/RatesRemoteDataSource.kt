package com.currencyconverter.app.data.remote

import java.math.BigDecimal
import java.time.LocalDate

/** Provider-independent shape of a downloaded snapshot. */
data class RemoteRates(
    val base: String,
    val date: LocalDate?,
    val quotes: Map<String, BigDecimal>,
)

/**
 * Seam for swapping the exchange-rate provider. Implementations must translate every failure
 * into a [com.currencyconverter.app.domain.model.RatesException].
 */
interface RatesRemoteDataSource {
    suspend fun fetchLatest(base: String): RemoteRates
}
