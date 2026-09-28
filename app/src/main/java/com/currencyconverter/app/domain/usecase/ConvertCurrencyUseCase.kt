package com.currencyconverter.app.domain.usecase

import com.currencyconverter.app.domain.model.ExchangeRate
import com.currencyconverter.app.domain.model.ExchangeRates
import java.math.BigDecimal
import javax.inject.Inject

data class Conversion(
    val rate: ExchangeRate,
    /** Exact, unrounded result. Rounding is a formatting concern. */
    val result: BigDecimal,
)

class ConvertCurrencyUseCase @Inject constructor() {

    /** Converts [amount] of [from] into [to], or `null` if the snapshot has no rate for the pair. */
    operator fun invoke(
        amount: BigDecimal,
        from: String,
        to: String,
        rates: ExchangeRates,
    ): Conversion? {
        val rate = rates.rate(from, to) ?: return null
        return Conversion(rate = rate, result = rate.convert(amount))
    }
}
