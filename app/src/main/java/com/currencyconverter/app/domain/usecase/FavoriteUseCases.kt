package com.currencyconverter.app.domain.usecase

import com.currencyconverter.app.domain.model.CurrencyPair
import com.currencyconverter.app.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {
    /** Adds the pair to favorites, or removes it when it is already there. */
    suspend operator fun invoke(pair: CurrencyPair) {
        if (pair in favoritesRepository.favorites.first()) {
            favoritesRepository.remove(pair)
        } else {
            favoritesRepository.add(pair)
        }
    }
}

class MoveFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {
    enum class Direction { Up, Down }

    /** Moves [pair] one step; does nothing at the edges or for unknown pairs. */
    suspend operator fun invoke(pair: CurrencyPair, direction: Direction) {
        val current = favoritesRepository.favorites.first()
        val index = current.indexOf(pair)
        if (index < 0) return
        val target = if (direction == Direction.Up) index - 1 else index + 1
        if (target !in current.indices) return
        val reordered = current.toMutableList().apply {
            this[index] = this[target].also { this[target] = this[index] }
        }
        favoritesRepository.setOrder(reordered)
    }
}

class AddFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {
    /** Adds the pair unless both sides are the same currency; existing pairs are ignored. */
    suspend operator fun invoke(pair: CurrencyPair) {
        if (pair.from != pair.to) favoritesRepository.add(pair)
    }
}
