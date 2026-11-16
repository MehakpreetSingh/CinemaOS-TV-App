package com.nuvio.tv.ui.components

/** Splits anime vs movie/series providers by name so the UI never mixes them. */
object ProviderContentFilter {
    private val animeProviders = listOf("anikoto", "animeworld", "kickassanime", "animesalt", "megaplay")
    private val movieProviders = listOf("movy", "hdhub", "wing", "moviebox")

    fun isAnimeProvider(name: String): Boolean {
        val n = name.lowercase()
        return animeProviders.any { n.contains(it) }
    }

    fun isMovieProvider(name: String): Boolean {
        val n = name.lowercase()
        return movieProviders.any { n.contains(it) }
    }

    fun isAnimeContent(type: String?, id: String?, genres: String? = null): Boolean {
        val i = id?.lowercase().orEmpty()
        return type.equals("anime", ignoreCase = true) ||
            i.startsWith("kitsu:") || i.startsWith("mal:") || i.startsWith("anilist:") ||
            genres?.contains("anime", ignoreCase = true) == true
    }

    fun appliesTo(name: String, anime: Boolean): Boolean =
        if (anime) !isMovieProvider(name) else !isAnimeProvider(name)

    fun filterNames(names: List<String>, anime: Boolean): List<String> =
        names.filter { appliesTo(it, anime) }

    /** Known providers in sequential search order; unknown ones keep their relative order after. */
    fun sortIndex(name: String, anime: Boolean): Int {
        val n = name.lowercase()
        val list = if (anime) animeProviders else movieProviders
        val idx = list.indexOfFirst { n.contains(it) }
        return if (idx >= 0) idx else list.size
    }
}
