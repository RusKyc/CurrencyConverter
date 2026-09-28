package com.currencyconverter.app.data.remote

import com.currencyconverter.app.data.remote.dto.LatestRatesDto
import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApi {
    @GET("v6/latest/{base}")
    suspend fun latest(@Path("base") base: String): LatestRatesDto
}
