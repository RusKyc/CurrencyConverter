package com.currencyconverter.app.domain.usecase

import com.currencyconverter.app.domain.model.Currency
import java.util.Locale
import javax.inject.Inject

class SearchCurrenciesUseCase @Inject constructor() {

    /** Matches by ISO code, localised name or symbol. Code matches come first. */
    operator fun invoke(query: String, currencies: List<Currency>): List<Currency> {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) return currencies
        return currencies
            .mapNotNull { currency ->
                val code = currency.code.lowercase(Locale.ROOT)
                val name = currency.displayName.lowercase(Locale.getDefault())
                val rank = when {
                    code == needle -> 0
                    code.startsWith(needle) -> 1
                    name.startsWith(needle) -> 2
                    name.contains(needle) || currency.symbol?.lowercase(Locale.ROOT) == needle -> 3
                    else -> null
                }
                rank?.let { it to currency }
            }
            .sortedBy { it.first }
            .map { it.second }
    }
}
