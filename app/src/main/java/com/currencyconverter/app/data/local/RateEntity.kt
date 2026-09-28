package com.currencyconverter.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per currency. Snapshot metadata (base, provider date, download time) is repeated on
 * every row on purpose: the whole snapshot is then read by a single query and can never be
 * observed half-updated. The rate is stored as text to keep the exact decimal value.
 */
@Entity(tableName = "exchange_rates")
data class RateEntity(
    @PrimaryKey val code: String,
    val rate: String,
    val baseCode: String,
    val providerDate: String?,
    val fetchedAtMillis: Long,
)
