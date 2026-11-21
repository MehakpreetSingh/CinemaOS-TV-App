package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.R
import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.data.repository.ImdbRatingCache
import com.nuvio.tv.domain.model.MetaPreview
import java.util.Locale

/** Where a displayed 0-10 score comes from. */
enum class RatingSource { IMDB, TMDB }

/** A score together with the source it has to be labelled with. */
@Immutable
data class DisplayRating(val value: Float, val source: RatingSource) {
    val text: String get() = String.format(Locale.US, "%.1f", value)
    val isImdb: Boolean get() = source == RatingSource.IMDB
}

/**
 * Picks what to show for a title: a real IMDb score (fetched into [ImdbRatingCache] by the hero or
 * the details screen, or supplied by the catalog) wins; otherwise the TMDB score, labelled TMDB.
 * Reads the cache only, so it is safe to call from any card.
 */
fun resolveDisplayRating(imdbId: String?, imdbRating: Float?, tmdbRating: Float?): DisplayRating? {
    ImdbRatingCache.peek(imdbId)?.takeIf { it > 0f }?.let { return DisplayRating(it, RatingSource.IMDB) }
    imdbRating?.takeIf { it > 0f }?.let { return DisplayRating(it, RatingSource.IMDB) }
    tmdbRating?.takeIf { it > 0f }?.let { return DisplayRating(it, RatingSource.TMDB) }
    return null
}

fun MetaPreview.displayRating(): DisplayRating? =
    resolveDisplayRating(imdbId ?: id, imdbRating, tmdbRating)

@Composable
fun ImdbRatingSourceLabel(
    logoModifier: Modifier,
    textStyle: TextStyle,
    textColor: Color,
    textModifier: Modifier = Modifier,
    source: RatingSource = RatingSource.IMDB
) {
    val isTmdb = source == RatingSource.TMDB
    val label = stringResource(if (isTmdb) R.string.cd_tmdb else R.string.cd_imdb)
    if (isTmdb || AppFeaturePolicy.imdbRatingLogoEnabled) {
        val context = LocalContext.current
        val model = remember(context, isTmdb) {
            ImageRequest.Builder(context)
                .data(if (isTmdb) R.raw.mdblist_tmdb else R.raw.imdb_logo_2016)
                .build()
        }
        AsyncImage(
            model = model,
            contentDescription = label,
            modifier = logoModifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Text(
            text = label,
            modifier = textModifier,
            style = textStyle,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
