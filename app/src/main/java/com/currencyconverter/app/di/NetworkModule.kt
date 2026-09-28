package com.currencyconverter.app.di

import com.currencyconverter.app.BuildConfig
import com.currencyconverter.app.data.remote.ApiKeyInterceptor
import com.currencyconverter.app.data.remote.ExchangeRateApi
import com.currencyconverter.app.data.remote.ExchangeRateApiRemoteDataSource
import com.currencyconverter.app.data.remote.RatesRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(ApiKeyInterceptor(BuildConfig.RATES_API_KEY_HEADER, BuildConfig.RATES_API_KEY))
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.RATES_BASE_URL.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideExchangeRateApi(retrofit: Retrofit): ExchangeRateApi = retrofit.create(ExchangeRateApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteModule {
    /** Point this binding at another implementation to switch the rates provider. */
    @Binds
    abstract fun bindRatesRemoteDataSource(impl: ExchangeRateApiRemoteDataSource): RatesRemoteDataSource
}
