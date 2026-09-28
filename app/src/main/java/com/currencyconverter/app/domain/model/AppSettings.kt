package com.currencyconverter.app.domain.model

import java.time.Duration

enum class ThemeMode { System, Light, Dark }

enum class RefreshInterval(val hours: Int) {
    Manual(0),
    Hourly(1),
    Every3Hours(3),
    Every6Hours(6),
    Every12Hours(12),
    Daily(24);

    /** `null` means rates are never refreshed automatically. */
    val duration: Duration? get() = if (hours == 0) null else Duration.ofHours(hours.toLong())
}

/** How many digits after the decimal separator are shown for converted amounts. */
sealed interface DecimalPlaces {
    /** Use the natural number of digits of the target currency (2 for EUR, 0 for JPY). */
    data object Auto : DecimalPlaces

    data class Fixed(val count: Int) : DecimalPlaces {
        init {
            require(count in MIN..MAX) { "Decimal places must be in $MIN..$MAX" }
        }
    }

    companion object {
        const val MIN = 0
        const val MAX = 6
    }
}

data class AppSettings(
    val defaultBase: String = "USD",
    val themeMode: ThemeMode = ThemeMode.System,
    val refreshInterval: RefreshInterval = RefreshInterval.Every6Hours,
    val decimalPlaces: DecimalPlaces = DecimalPlaces.Auto,
)
