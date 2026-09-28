package com.currencyconverter.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FavoritesDao {

    @Query("SELECT * FROM favorite_pairs ORDER BY position ASC, id ASC")
    abstract fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT MAX(position) FROM favorite_pairs")
    protected abstract suspend fun maxPosition(): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(entity: FavoriteEntity): Long

    @Query("DELETE FROM favorite_pairs WHERE base = :base AND quote = :quote")
    abstract suspend fun delete(base: String, quote: String)

    @Query("UPDATE favorite_pairs SET position = :position WHERE base = :base AND quote = :quote")
    protected abstract suspend fun updatePosition(base: String, quote: String, position: Int)

    /** Appends a pair at the end of the list; a pair that already exists is left untouched. */
    @Transaction
    open suspend fun append(base: String, quote: String) {
        val next = (maxPosition() ?: -1) + 1
        insert(FavoriteEntity(base = base, quote = quote, position = next))
    }

    @Transaction
    open suspend fun applyOrder(pairs: List<Pair<String, String>>) {
        pairs.forEachIndexed { index, (base, quote) -> updatePosition(base, quote, index) }
    }
}
