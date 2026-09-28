package com.currencyconverter.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RateEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ratesDao(): RatesDao

    abstract fun favoritesDao(): FavoritesDao

    companion object {
        const val NAME = "currency_converter.db"
    }
}
