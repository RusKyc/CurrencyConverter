package com.currencyconverter.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RatesDao {

    @Query("SELECT * FROM exchange_rates")
    abstract fun observeAll(): Flow<List<RateEntity>>

    @Query("SELECT fetchedAtMillis FROM exchange_rates LIMIT 1")
    abstract suspend fun getFetchedAtMillis(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(rates: List<RateEntity>)

    @Query("DELETE FROM exchange_rates")
    protected abstract suspend fun clear()

    /** Atomically swaps the stored snapshot: observers see either the old or the new one. */
    @Transaction
    open suspend fun replaceAll(rates: List<RateEntity>) {
        clear()
        insertAll(rates)
    }
}
