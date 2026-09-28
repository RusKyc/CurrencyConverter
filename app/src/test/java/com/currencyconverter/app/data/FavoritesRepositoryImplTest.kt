package com.currencyconverter.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.currencyconverter.app.data.local.AppDatabase
import com.currencyconverter.app.data.repository.FavoritesRepositoryImpl
import com.currencyconverter.app.domain.model.CurrencyPair
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoritesRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: FavoritesRepositoryImpl

    private val usdEur = CurrencyPair("USD", "EUR")
    private val eurGbp = CurrencyPair("EUR", "GBP")
    private val usdJpy = CurrencyPair("USD", "JPY")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FavoritesRepositoryImpl(database.favoritesDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `starts empty`() = runTest {
        assertTrue(repository.favorites.first().isEmpty())
    }

    @Test
    fun `pairs keep insertion order`() = runTest {
        repository.add(usdEur)
        repository.add(eurGbp)
        repository.add(usdJpy)

        assertEquals(listOf(usdEur, eurGbp, usdJpy), repository.favorites.first())
    }

    @Test
    fun `adding the same pair twice does not duplicate it`() = runTest {
        repository.add(usdEur)
        repository.add(usdEur)

        assertEquals(listOf(usdEur), repository.favorites.first())
    }

    @Test
    fun `direction matters`() = runTest {
        repository.add(usdEur)
        repository.add(usdEur.reversed())

        assertEquals(listOf(usdEur, usdEur.reversed()), repository.favorites.first())
    }

    @Test
    fun `remove deletes only the requested pair`() = runTest {
        repository.add(usdEur)
        repository.add(eurGbp)

        repository.remove(usdEur)

        assertEquals(listOf(eurGbp), repository.favorites.first())
    }

    @Test
    fun `reordering is persisted`() = runTest {
        repository.add(usdEur)
        repository.add(eurGbp)
        repository.add(usdJpy)

        repository.setOrder(listOf(usdJpy, usdEur, eurGbp))

        assertEquals(listOf(usdJpy, usdEur, eurGbp), repository.favorites.first())
    }

    @Test
    fun `new pair goes to the end after removals and reordering`() = runTest {
        repository.add(usdEur)
        repository.add(eurGbp)
        repository.setOrder(listOf(eurGbp, usdEur))
        repository.remove(eurGbp)

        repository.add(usdJpy)

        assertEquals(listOf(usdEur, usdJpy), repository.favorites.first())
    }
}
