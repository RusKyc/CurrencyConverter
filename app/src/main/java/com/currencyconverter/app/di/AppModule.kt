package com.currencyconverter.app.di

import com.currencyconverter.app.BuildConfig
import com.currencyconverter.app.domain.model.AppInfo
import com.currencyconverter.app.domain.time.SystemTimeSource
import com.currencyconverter.app.domain.time.TimeSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideAppInfo(): AppInfo = AppInfo(
        versionName = BuildConfig.VERSION_NAME,
        ratesProviderName = BuildConfig.RATES_PROVIDER_NAME,
        ratesProviderUrl = BuildConfig.RATES_PROVIDER_URL,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    abstract fun bindTimeSource(impl: SystemTimeSource): TimeSource
}
