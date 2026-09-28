package com.currencyconverter.app.di

import com.currencyconverter.app.data.local.RatesDao
import com.currencyconverter.app.data.remote.RatesRemoteDataSource
import com.currencyconverter.app.data.repository.AndroidNetworkMonitor
import com.currencyconverter.app.data.repository.FavoritesRepositoryImpl
import com.currencyconverter.app.data.repository.RatesRepositoryImpl
import com.currencyconverter.app.data.repository.SettingsRepositoryImpl
import com.currencyconverter.app.domain.repository.FavoritesRepository
import com.currencyconverter.app.domain.repository.NetworkMonitor
import com.currencyconverter.app.domain.repository.RatesRepository
import com.currencyconverter.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    companion object {
        /** Singleton: it owns the mutex and the throttling state shared by UI and background work. */
        @Provides
        @Singleton
        fun provideRatesRepository(
            dao: RatesDao,
            remote: RatesRemoteDataSource,
            clock: Clock,
        ): RatesRepository = RatesRepositoryImpl(dao, remote, clock)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {
    @Binds
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor
}
