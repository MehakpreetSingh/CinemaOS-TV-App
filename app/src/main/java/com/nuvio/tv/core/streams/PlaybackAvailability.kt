package com.nuvio.tv.core.streams

import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.ScraperInfo
import com.nuvio.tv.domain.model.Video

internal fun Addon.supportsStreamResource(type: String, videoId: String): Boolean =
    supportsResource("stream", type, videoId)

internal fun Addon.supportsResource(name: String, type: String, id: String): Boolean =
    resources.any { resource ->
        resource.name == name &&
            (resource.types.isEmpty() || resource.types.contains(type)) &&
            run {
                val prefixes = resource.idPrefixes?.takeIf { it.isNotEmpty() }
                    ?: idPrefixes.takeIf { it.isNotEmpty() }
                prefixes == null || prefixes.any { id.startsWith(it) }
            }
    }

internal data class PlaybackAvailability(
    val addons: List<Addon> = emptyList(),
    val scrapers: List<ScraperInfo> = emptyList(),
    val isLoaded: Boolean = false,
    private val cachedMeta: (String, String) -> Meta? = { _, _ -> null },
    private val serverStreams: (String, String) -> Boolean = { _, _ -> false }
) {
    fun canStream(
        type: String,
        videoId: String,
        contentId: String = videoId,
        video: Video? = null
    ): Boolean = video?.takeIf { it.id == videoId }?.streams?.isNotEmpty() == true ||
        cachedMeta(type, contentId)?.videos?.any { it.id == videoId && it.streams.isNotEmpty() } == true ||
        addons.any { it.enabled && it.supportsStreamResource(type, videoId) } ||
        scrapers.any { it.enabled && it.supportsType(type) } ||
        // CinemaOS built-in scrapers are always available (seeded on launch), so the gate must
        // not depend on whether the plugin list has been fetched yet.
        (AppFeaturePolicy.pluginsEnabled && type.lowercase() in BUILT_IN_SOURCE_TYPES) ||
        serverStreams(type, videoId)
}

private val BUILT_IN_SOURCE_TYPES = setOf("movie", "series", "tv", "show", "anime", "other")
