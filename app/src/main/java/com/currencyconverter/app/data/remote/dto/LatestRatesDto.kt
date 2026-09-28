package com.currencyconverter.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * ExchangeRate-API `/v6/latest/{base}` payload. Rates are kept as [JsonPrimitive] so the original
 * decimal text (e.g. `84.192029`) reaches `BigDecimal` untouched instead of being squeezed
 * through a `Double`. A non-"success" [result] carries no [rates], see the provider's docs at
 * https://www.exchangerate-api.com/docs/free .
 */
@Serializable
data class LatestRatesDto(
    val result: String,
    @SerialName("base_code") val baseCode: String? = null,
    @SerialName("time_last_update_utc") val lastUpdateUtc: String? = null,
    val rates: Map<String, JsonPrimitive>? = null,
)
