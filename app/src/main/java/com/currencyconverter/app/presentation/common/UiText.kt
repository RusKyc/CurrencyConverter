package com.currencyconverter.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.currencyconverter.app.R
import com.currencyconverter.app.domain.model.DecimalPlaces
import com.currencyconverter.app.domain.model.RatesError
import com.currencyconverter.app.domain.model.RefreshInterval
import com.currencyconverter.app.domain.model.ThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RatesError.userMessage(): String = when (this) {
    RatesError.Network -> stringResource(R.string.error_network)
    is RatesError.Api ->
        if (httpCode != null) stringResource(R.string.error_api_code, httpCode) else stringResource(R.string.error_api)
    RatesError.Parsing -> stringResource(R.string.error_parsing)
    is RatesError.Unknown -> stringResource(R.string.error_unknown)
}

@Composable
fun ThemeMode.label(): String = when (this) {
    ThemeMode.System -> stringResource(R.string.theme_system)
    ThemeMode.Light -> stringResource(R.string.theme_light)
    ThemeMode.Dark -> stringResource(R.string.theme_dark)
}

@Composable
fun RefreshInterval.label(): String = when (this) {
    RefreshInterval.Manual -> stringResource(R.string.interval_manual)
    RefreshInterval.Hourly -> stringResource(R.string.interval_1h)
    RefreshInterval.Every3Hours -> stringResource(R.string.interval_3h)
    RefreshInterval.Every6Hours -> stringResource(R.string.interval_6h)
    RefreshInterval.Every12Hours -> stringResource(R.string.interval_12h)
    RefreshInterval.Daily -> stringResource(R.string.interval_24h)
}

@Composable
fun DecimalPlaces.label(): String = when (this) {
    DecimalPlaces.Auto -> stringResource(R.string.decimals_auto)
    is DecimalPlaces.Fixed -> count.toString()
}

@Composable
fun UpdatedAge.label(): String {
    val locale = LocalLocale.current.platformLocale
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return when (this) {
        UpdatedAge.JustNow -> stringResource(R.string.age_just_now)
        is UpdatedAge.Minutes -> pluralStringResource(R.plurals.age_minutes, count, count)
        is UpdatedAge.Hours -> pluralStringResource(R.plurals.age_hours, count, count)
        is UpdatedAge.Today -> stringResource(R.string.age_today_at, timeFormatter.format(time))
        is UpdatedAge.Yesterday -> stringResource(R.string.age_yesterday_at, timeFormatter.format(time))
        is UpdatedAge.Earlier -> stringResource(
            R.string.age_date_at,
            dateFormatter.format(dateTime),
            timeFormatter.format(dateTime),
        )
    }
}

/** "Updated 5 min ago", "Offline · Last updated today at 14:32", ... */
@Composable
fun updateStatusText(updatedAt: Instant, now: Instant, freshness: DataFreshness, zone: ZoneId = ZoneId.systemDefault()): String =
    when (freshness) {
        DataFreshness.Live -> stringResource(R.string.status_updated, relativeAge(updatedAt, now, zone).label())
        DataFreshness.Offline -> stringResource(R.string.status_offline, absoluteAge(updatedAt, now, zone).label())
        DataFreshness.CachedAfterError -> stringResource(R.string.status_cached, absoluteAge(updatedAt, now, zone).label())
    }

/** Where the displayed rates come from. */
enum class DataFreshness {
    /** Downloaded recently and the last refresh (if any) succeeded. */
    Live,

    /** The device has no internet connection; showing what is stored. */
    Offline,

    /** Online, but the last refresh failed; showing what is stored. */
    CachedAfterError,
}
