package com.currencyconverter.app.domain.usecase

import com.currencyconverter.app.domain.model.RefreshResult
import com.currencyconverter.app.domain.repository.RatesRepository
import com.currencyconverter.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Refreshes rates honouring the user's update-frequency setting.
 * `force = true` is used for explicit user actions (button, pull-to-refresh).
 */
class RefreshRatesUseCase @Inject constructor(
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(force: Boolean = false): RefreshResult {
        val maxAge = settingsRepository.settings.first().refreshInterval.duration
        return ratesRepository.refresh(force = force, maxAge = maxAge)
    }
}
