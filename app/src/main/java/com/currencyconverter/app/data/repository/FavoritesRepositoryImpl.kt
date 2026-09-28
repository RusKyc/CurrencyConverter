package com.currencyconverter.app.data.repository

import com.currencyconverter.app.data.local.FavoritesDao
import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FavoritesRepositoryImpl @Inject constructor(
    private val dao: FavoritesDao,
) : FavoritesRepository {

    override val favorites: Flow<List<CurrencyPair>> = dao.observeAll().map { rows ->
        rows.map { CurrencyPair(from = it.base, to = it.quote) }
    }

    override suspend fun add(pair: CurrencyPair) = dao.append(pair.from, pair.to)

    override suspend fun remove(pair: CurrencyPair) = dao.delete(pair.from, pair.to)

    override suspend fun setOrder(pairs: List<CurrencyPair>) =
        dao.applyOrder(pairs.map { it.from to it.to })
}
