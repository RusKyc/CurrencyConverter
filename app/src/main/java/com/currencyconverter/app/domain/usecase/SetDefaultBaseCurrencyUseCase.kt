package com.currencyconverter.app.domain.usecase

import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Changes the default base currency and makes the converter start from it right away,
 * keeping the two sides of the converter different.
 */
class SetDefaultBaseCurrencyUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(code: String) {
        settingsRepository.setDefaultBase(code)
        val pair: CurrencyPair = settingsRepository.converterPair.first()
        settingsRepository.setConverterPair(pair.withFrom(code))
    }
}
