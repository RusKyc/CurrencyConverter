package com.currencyconverter.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the provider API key (from BuildConfig, i.e. local.properties) to every request.
 * Does nothing for keyless providers such as ExchangeRate-API.
 */
class ApiKeyInterceptor(
    private val headerName: String,
    private val apiKey: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (apiKey.isBlank()) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(headerName, apiKey).build())
    }
}
