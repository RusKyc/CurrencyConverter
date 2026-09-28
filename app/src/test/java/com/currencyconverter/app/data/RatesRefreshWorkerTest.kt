package com.currencyconverter.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.currencyconverter.app.data.work.RatesRefreshWorker
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.domain.model.RefreshResult
import com.currencyconverter.app.domain.usecase.RefreshRatesUseCase
import com.currencyconverter.app.testing.FakeRatesRepository
import com.currencyconverter.app.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RatesRefreshWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private suspend fun run(result: RefreshResult, attempt: Int = 0): ListenableWorker.Result {
        val repository = FakeRatesRepository().apply { nextResult = result }
        val useCase = RefreshRatesUseCase(repository, FakeSettingsRepository())
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = RatesRefreshWorker(appContext, workerParameters, useCase)
        }
        return TestListenableWorkerBuilder<RatesRefreshWorker>(context)
            .setWorkerFactory(factory)
            .setRunAttemptCount(attempt)
            .build()
            .doWork()
    }

    @Test
    fun `successful outcomes finish the work`() = runTest {
        assertEquals(ListenableWorker.Result.success(), run(RefreshResult.Updated))
        assertEquals(ListenableWorker.Result.success(), run(RefreshResult.UpToDate))
        assertEquals(ListenableWorker.Result.success(), run(RefreshResult.Throttled))
    }

    @Test
    fun `transient failures are retried`() = runTest {
        assertEquals(ListenableWorker.Result.retry(), run(RefreshResult.Failed(RatesError.Network)))
        assertEquals(ListenableWorker.Result.retry(), run(RefreshResult.Failed(RatesError.Api(503))))
        assertEquals(ListenableWorker.Result.retry(), run(RefreshResult.Failed(RatesError.Api(429))))
    }

    @Test
    fun `retries stop after a few attempts so a broken service is not hammered`() = runTest {
        assertEquals(ListenableWorker.Result.success(), run(RefreshResult.Failed(RatesError.Network), attempt = 3))
    }

    @Test
    fun `permanent failures are not retried`() = runTest {
        assertEquals(ListenableWorker.Result.success(), run(RefreshResult.Failed(RatesError.Parsing)))
        assertEquals(ListenableWorker.Result.success(), run(RefreshResult.Failed(RatesError.Api(404))))
    }
}
