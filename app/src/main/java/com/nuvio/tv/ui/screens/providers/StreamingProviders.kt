package com.nuvio.tv.ui.screens.providers

import androidx.compose.runtime.Immutable
import com.nuvio.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One streaming service. [id] is the raw TMDB `with_watch_providers` value: a single id ("8") or an
 * OR list ("9|119") when TMDB lists the same service under several ids.
 */
@Immutable
data class StreamingProvider(val id: String, val name: String) {
    val ids: List<Int> get() = id.split('|').mapNotNull { it.trim().toIntOrNull() }
}

/**
 * Copy of the Discover "Streaming on" list (ui/screens/search/DiscoverFilters.kt,
 * `DiscoverFilterValues.providers`), kept here so the Providers row and page do not depend on the
 * Discover screen. Brand names are not translated.
 */
object CinemaosStreamingProviders {
    val all: List<StreamingProvider> = listOf(
        StreamingProvider("8", "Netflix"),
        StreamingProvider("9|119", "Prime Video"),
        StreamingProvider("337", "Disney+"),
        StreamingProvider("1899", "Max"),
        StreamingProvider("350", "Apple TV+"),
        StreamingProvider("15", "Hulu"),
        StreamingProvider("531", "Paramount+"),
        StreamingProvider("283", "Crunchyroll"),
        StreamingProvider("2336|122", "JioHotstar"),
        StreamingProvider("232", "Zee5"),
        StreamingProvider("237", "SonyLiv")
    )

    fun byId(id: String): StreamingProvider? = all.firstOrNull { it.id == id }

    /** Region used for `watch_region`: the device country, else US. */
    fun watchRegion(): String = Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase() ?: "US"
}

/** Provider logos from TMDB's global provider lists, fetched once per process. */
@Singleton
class ProviderLogoRepository @Inject constructor(
    private val api: CinemaosTmdbExtrasApi
) {
    private val mutex = Mutex()
    @Volatile private var cache: Map<String, String>? = null

    /** Provider [StreamingProvider.id] -> logo URL (w185). Missing entries simply show the name. */
    suspend fun logos(): Map<String, String> {
        cache?.let { return it }
        return mutex.withLock {
            cache?.let { return@withLock it }
            val loaded = load()
            if (loaded.isNotEmpty()) cache = loaded
            loaded
        }
    }

    private suspend fun load(): Map<String, String> = withContext(Dispatchers.IO) {
        val key = BuildConfig.TMDB_API_KEY
        if (key.isBlank()) return@withContext emptyMap()
        val entries = coroutineScope {
            val movies = async { runCatching { api.movieWatchProviders(key).body()?.results }.getOrNull().orEmpty() }
            val tv = async { runCatching { api.tvWatchProviders(key).body()?.results }.getOrNull().orEmpty() }
            movies.await() + tv.await()
        }
        val logoById = HashMap<Int, String>()
        entries.forEach { entry ->
            val url = tmdbImage(entry.logoPath, "w185") ?: return@forEach
            logoById.putIfAbsent(entry.providerId, url)
        }
        CinemaosStreamingProviders.all.mapNotNull { provider ->
            provider.ids.firstNotNullOfOrNull { logoById[it] }?.let { provider.id to it }
        }.toMap()
    }
}
