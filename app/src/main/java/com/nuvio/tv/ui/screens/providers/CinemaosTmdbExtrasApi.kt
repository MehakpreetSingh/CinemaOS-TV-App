package com.nuvio.tv.ui.screens.providers

import com.nuvio.tv.data.remote.api.TmdbSeasonSummary
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Named
import javax.inject.Singleton

/**
 * The few TMDB endpoints the Providers and Calendar pages need that the shared [com.nuvio.tv.data.remote.api.TmdbApi]
 * does not expose. Created from the app's `@Named("tmdb")` Retrofit, so it shares the Hilt OkHttpClient
 * (DNS over HTTPS through [com.nuvio.tv.core.network.AppDns], disk cache, headers).
 */
interface CinemaosTmdbExtrasApi {

    /** Every provider TMDB knows for movies (no region = global list with logos). */
    @GET("watch/providers/movie")
    suspend fun movieWatchProviders(
        @Query("api_key") apiKey: String,
        @Query("watch_region") watchRegion: String? = null
    ): Response<CinemaosWatchProviderListResponse>

    @GET("watch/providers/tv")
    suspend fun tvWatchProviders(
        @Query("api_key") apiKey: String,
        @Query("watch_region") watchRegion: String? = null
    ): Response<CinemaosWatchProviderListResponse>

    /** Series details including next / last episode to air, which the shared model omits. */
    @GET("tv/{tv_id}")
    suspend fun tvAiringDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): Response<CinemaosTvAiringDetails>

    @GET("movie/{movie_id}")
    suspend fun movieReleaseDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): Response<CinemaosMovieReleaseDetails>
}

@JsonClass(generateAdapter = true)
data class CinemaosWatchProviderListResponse(
    @Json(name = "results") val results: List<CinemaosWatchProviderEntry>? = null
)

@JsonClass(generateAdapter = true)
data class CinemaosWatchProviderEntry(
    @Json(name = "provider_id") val providerId: Int,
    @Json(name = "provider_name") val providerName: String? = null,
    @Json(name = "logo_path") val logoPath: String? = null
)

@JsonClass(generateAdapter = true)
data class CinemaosEpisodeRef(
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    @Json(name = "air_date") val airDate: String? = null
)

@JsonClass(generateAdapter = true)
data class CinemaosTvAiringDetails(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "in_production") val inProduction: Boolean? = null,
    @Json(name = "last_air_date") val lastAirDate: String? = null,
    @Json(name = "last_episode_to_air") val lastEpisodeToAir: CinemaosEpisodeRef? = null,
    @Json(name = "next_episode_to_air") val nextEpisodeToAir: CinemaosEpisodeRef? = null,
    @Json(name = "seasons") val seasons: List<TmdbSeasonSummary>? = null
)

@JsonClass(generateAdapter = true)
data class CinemaosMovieReleaseDetails(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null
)

@Module
@InstallIn(SingletonComponent::class)
object CinemaosTmdbExtrasModule {
    @Provides
    @Singleton
    fun provideCinemaosTmdbExtrasApi(@Named("tmdb") retrofit: Retrofit): CinemaosTmdbExtrasApi =
        retrofit.create(CinemaosTmdbExtrasApi::class.java)
}

internal fun tmdbImage(path: String?, size: String): String? {
    val clean = path?.takeIf { it.isNotBlank() } ?: return null
    return "https://image.tmdb.org/t/p/$size$clean"
}
