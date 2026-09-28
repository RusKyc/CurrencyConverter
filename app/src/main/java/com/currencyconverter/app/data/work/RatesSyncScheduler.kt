package com.currencyconverter.app.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.currencyconverter.app.domain.model.RefreshInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the background refresh in line with the update-frequency setting. */
@Singleton
class RatesSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun apply(interval: RefreshInterval) {
        val workManager = WorkManager.getInstance(context)
        if (interval == RefreshInterval.Manual) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<RatesRefreshWorker>(interval.hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "rates_periodic_refresh"
    }
}
