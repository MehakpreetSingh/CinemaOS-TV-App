package com.nuvio.tv.core.tmdb

import android.util.Log
import com.nuvio.tv.data.repository.ImdbRatingCache
import com.nuvio.tv.data.repository.ImdbRatingRepository
import com.nuvio.tv.data.repository.MDBListRepository
import com.nuvio.tv.domain.model.MDBListSettings
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.TmdbSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MetaPreviewEnricher"
private const val ENRICHMENT_CONCURRENCY = 4

@Singleton
class MetaPreviewEnricher @Inject constructor(
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    private val mdbListRepository: MDBListRepository,
    private val imdbRatingRepository: ImdbRatingRepository
) {
    suspend fun enrich(
        items: List<MetaPreview>,
        settings: TmdbSettings,
        mdbSettings: MDBListSettings? = null
    ): List<MetaPreview> {
        if (items.isEmpty()) return items
        val mdbEnabled = mdbSettings != null &&
            mdbListRepository.isAvailable(mdbSettings) &&
            mdbSettings.showOnHero

        return coroutineScope {
            val semaphore = Semaphore(ENRICHMENT_CONCURRENCY)
            items.map { item ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            enrichItem(item, settings, mdbSettings.takeIf { mdbEnabled })
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Enrichment failed for ${item.id}: ${e.message}")
                            item
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun enrichItem(
        item: MetaPreview,
        settings: TmdbSettings,
        mdbSettings: MDBListSettings?
    ): MetaPreview = coroutineScope {
        // Resolved while the TMDB id is looked up, so the Cinemeta call below can start right after.
        var resolvedImdbId: String? = ImdbRatingCache.normalizeId(item.imdbId) ?: ImdbRatingCache.normalizeId(item.id)
        val tmdbDeferred = async {
            val tmdbId = tmdbService.ensureTmdbId(item.id, item.apiType) ?: return@async null
            tmdbId.toIntOrNull()?.let { numericId ->
                runCatching { tmdbService.tmdbToImdb(numericId, item.apiType) }.getOrNull()
                    ?.let { if (resolvedImdbId == null) resolvedImdbId = ImdbRatingCache.normalizeId(it) }
            }
            tmdbMetadataService.fetchEnrichment(
                tmdbId = tmdbId,
                contentType = item.type,
                language = settings.language
            )
        }
        val mdbDeferred = mdbSettings?.let {
            async {
                runCatching {
                    mdbListRepository.getRatingsForMeta(
                        meta = item.toRatingsMeta(),
                        fallbackItemId = item.id,
                        fallbackItemType = item.apiType
                    )
                }.getOrNull()
            }
        }

        val enrichment = tmdbDeferred.await()
        val mdbResult = mdbDeferred?.await()

        var enriched = item
        if (mdbResult != null && mdbSettings != null) {
            enriched = enriched.copy(
                mdbListRatings = mdbResult.ratings,
                mdbListRatingOrder = mdbSettings.ratingOrder,
                imdbRating = mdbResult.ratings.imdb?.toFloat() ?: enriched.imdbRating
            )
        }

        // Real IMDb score for the hero (Cinemeta, memory-cached, deduplicated). Only the hero
        // path reaches this; catalog cards read the cache and never start a request.
        val imdbId = resolvedImdbId
        if (imdbId != null && mdbResult?.ratings?.imdb == null) {
            val realImdb = runCatching { imdbRatingRepository.getRating(imdbId, item.apiType) }.getOrNull()
            if (realImdb != null) enriched = enriched.copy(imdbRating = realImdb)
        }
        // Carry the TMDB score separately so the hero can label it when IMDb has none.
        enrichment?.rating?.toFloat()?.takeIf { it > 0f }?.let { tmdb ->
            enriched = enriched.copy(tmdbRating = tmdb)
        }

        if (enrichment == null) return@coroutineScope enriched

        if (settings.useArtwork) {
            enriched = enriched.copy(
                background = enrichment.backdrop ?: enriched.background,
                logo = enrichment.logo ?: enriched.logo,
                poster = enrichment.poster ?: enriched.poster
            )
        }

        if (settings.useBasicInfo) {
            enriched = enriched.copy(
                name = enrichment.localizedTitle ?: enriched.name,
                description = enrichment.description ?: enriched.description,
                genres = if (enrichment.genres.isNotEmpty()) enrichment.genres else enriched.genres,
                imdbRating = enriched.imdbRating
            )
        }

        if (settings.useDetails) {
            enriched = enriched.copy(
                runtime = enrichment.runtimeMinutes?.toString() ?: enriched.runtime,
                status = enrichment.status ?: enriched.status,
                ageRating = enrichment.ageRating ?: enriched.ageRating,
                country = enrichment.countries?.joinToString(", ") ?: enriched.country,
                language = enrichment.language ?: enriched.language
            )
        }

        enriched
    }

    private fun MetaPreview.toRatingsMeta(): Meta = Meta(
        id = id, type = type, name = name,
        poster = poster, posterShape = posterShape,
        background = background, logo = logo,
        description = description, releaseInfo = releaseInfo,
        imdbRating = imdbRating, genres = genres,
        runtime = runtime, director = director,
        cast = emptyList(), videos = emptyList(),
        country = country, awards = null,
        language = language, links = links
    )
}
