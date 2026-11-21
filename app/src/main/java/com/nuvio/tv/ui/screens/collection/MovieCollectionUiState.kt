package com.nuvio.tv.ui.screens.collection

import androidx.compose.runtime.Immutable
import com.nuvio.tv.domain.model.MetaPreview

/** A TMDB movie collection (franchise) with its movies in release order. */
@Immutable
data class MovieCollectionData(
    val name: String,
    val overview: String?,
    val backdrop: String?,
    val poster: String?,
    val movies: List<MetaPreview>
)

sealed interface MovieCollectionUiState {
    data object Loading : MovieCollectionUiState
    data class Success(val collection: MovieCollectionData) : MovieCollectionUiState
    data class Error(val message: String) : MovieCollectionUiState
}
