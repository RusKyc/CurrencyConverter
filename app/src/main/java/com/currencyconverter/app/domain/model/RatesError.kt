package com.currencyconverter.app.domain.model

/** Everything that can go wrong while refreshing rates, independent of the provider used. */
sealed interface RatesError {
    /** No connection, DNS failure, timeout, ... */
    data object Network : RatesError

    /** The service answered with a non-success HTTP status. */
    data class Api(val httpCode: Int?) : RatesError

    /** The service answered, but the payload could not be understood. */
    data object Parsing : RatesError

    data class Unknown(val message: String? = null) : RatesError
}

class RatesException(val error: RatesError, cause: Throwable? = null) : Exception(error.toString(), cause)

sealed interface RefreshResult {
    /** New rates were downloaded and stored. */
    data object Updated : RefreshResult

    /** Cached rates are still fresh enough, nothing was requested. */
    data object UpToDate : RefreshResult

    /** A request was made too recently; nothing was requested. */
    data object Throttled : RefreshResult

    data class Failed(val error: RatesError) : RefreshResult
}
