package com.currencyconverter.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_pairs",
    indices = [Index(value = ["base", "quote"], unique = true)],
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val base: String,
    val quote: String,
    val position: Int,
)
