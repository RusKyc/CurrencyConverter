package com.currencyconverter.app.data.remote

import com.currencyconverter.app.data.remote.dto.LatestRatesDto
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.domain.model.RatesException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class ExchangeRateApiRemoteDataSource @Inject constructor(
    private val api: ExchangeRateApi,
) : RatesRemoteDataSource {

    override suspend fun fetchLatest(base: String): RemoteRates = try {
        api.latest(base).toRemoteRates(requestedBase = base)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RatesException) {
        throw e
    } catch (e: HttpException) {
        throw RatesException(RatesError.Api(e.code()), e)
    } catch (e: IOException) {
        throw RatesException(RatesError.Network, e)
    } catch (e: SerializationException) {
        throw RatesException(RatesError.Parsing, e)
    } catch (e: IllegalArgumentException) {
        // Includes NumberFormatException from malformed decimals.
        throw RatesException(RatesError.Parsing, e)
    } catch (e: NullPointerException) {
        // Retrofit reports an empty 2xx body for a non-null return type this way.
        throw RatesException(RatesError.Parsing, e)
    } catch (e: Exception) {
        throw RatesException(RatesError.Unknown(e.message), e)
    }

    private fun LatestRatesDto.toRemoteRates(requestedBase: String): RemoteRates {
        if (result != "success") throw RatesException(RatesError.Api(null))
        val quotesRaw = rates
        if (quotesRaw.isNullOrEmpty()) throw RatesException(RatesError.Parsing)
        val quotes = quotesRaw.entries.associate { (code, value) ->
            val rate = BigDecimal(value.content)
            if (rate.signum() <= 0) throw RatesException(RatesError.Parsing)
            code.uppercase(Locale.ROOT) to rate
        }
        return RemoteRates(
            base = (baseCode ?: requestedBase).uppercase(Locale.ROOT),
            date = lastUpdateUtc?.let(::parseUpdateDate),
            quotes = quotes,
        )
    }

    private fun parseUpdateDate(text: String) = runCatching {
        OffsetDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate()
    }.getOrNull()
}
