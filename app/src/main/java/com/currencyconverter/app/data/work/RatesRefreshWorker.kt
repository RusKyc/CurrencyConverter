package com.currencyconverter.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.domain.model.RefreshResult
import com.currencyconverter.app.domain.usecase.RefreshRatesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RatesRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refreshRates: RefreshRatesUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val result = refreshRates(force = false)) {
        RefreshResult.Updated, RefreshResult.UpToDate, RefreshResult.Throttled -> Result.success()
        is RefreshResult.Failed -> if (result.error.isTransient() && runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private fun RatesError.isTransient(): Boolean = when (this) {
        RatesError.Network -> true
        is RatesError.Api -> httpCode == null || httpCode >= 500 || httpCode == 429
        RatesError.Parsing, is RatesError.Unknown -> false
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
