package com.nuvio.tv.domain.model

/**
 * The built-in live sports addon (Stremio protocol). It is always available for meta/stream
 * resolution through [com.nuvio.tv.domain.repository.AddonRepository.getPlaybackAddons], is never
 * part of the user's installed list (so it cannot be removed, never shows in addon settings, and
 * its catalogs never reach Home, Discover, Search or collections), and its catalogs are only
 * rendered by the Sports screen.
 */
object SportsAddon {
    /** The one place the addon's URL lives. */
    const val MANIFEST_URL = "https://nuviosports.xyz/manifest.json"

    /** [MANIFEST_URL] in the canonical form the addon repository keys manifests by. */
    const val BASE_URL = "https://nuviosports.xyz"

    const val ADDON_ID = "community.nuvio.live-sports"
    const val ID_PREFIX = "nuvio_sport_"

    /** Neutral label shown wherever the app would otherwise show the addon's own name. */
    const val DISPLAY_NAME = "CinemaOS Live"

    const val CATALOG_TYPE = "tv"
    const val CATALOG_LIVE = "nuvio_sports_live"
    const val CATALOG_UPCOMING = "nuvio_sports_upcoming"
    const val CATALOG_NETWORKS = "nuvio_sports_networks"
    const val CATALOG_REPLAYS = "nuvio_sports_replays"
    const val SPORT_CATALOG_PREFIX = "nuvio_sports_"

    private val host: String = BASE_URL.substringAfter("://").lowercase()

    fun isSportsAddon(addon: Addon): Boolean =
        addon.id.equals(ADDON_ID, ignoreCase = true) || isSportsAddonUrl(addon.baseUrl)

    fun isSportsAddonUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val withoutScheme = url.trim().substringAfter("://").lowercase()
        val urlHost = withoutScheme.substringBefore('/').substringBefore('?')
        return urlHost == host
    }

    fun isSportsItemId(id: String?): Boolean = id?.startsWith(ID_PREFIX, ignoreCase = true) == true

    // Item ids alone cannot tell a live event from a replay (replays use several id shapes), so
    // the Sports screen records which catalog each item came from. The player reads this to
    // decide live mode before the stream reports anything; unknown ids fall back to the player.
    private val liveItemIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val replayItemIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun itemKey(id: String): String = id.trim().substringBefore(':').lowercase()

    /** True for catalogs whose items are VOD replays rather than live events or channels. */
    fun isReplayCatalog(catalogId: String): Boolean = catalogId.startsWith(CATALOG_REPLAYS)

    /** Records the items of [catalogId] as live (live/upcoming/per-sport/24-7) or replay. */
    fun rememberCatalogItems(catalogId: String, itemIds: kotlin.collections.Collection<String>) {
        val target = if (isReplayCatalog(catalogId)) replayItemIds else liveItemIds
        itemIds.forEach { id -> if (isSportsItemId(id)) target += itemKey(id) }
    }

    /** True when [id] was listed by a live/upcoming/per-sport/24-7 catalog and never by a replay one. */
    fun isLiveSportsItem(id: String?): Boolean {
        if (id == null || !isSportsItemId(id)) return false
        val key = itemKey(id)
        return key in liveItemIds && key !in replayItemIds
    }

    /**
     * Drops an image URL that points at the addon's own branding (its logo), so the UI falls back
     * to the plain title instead of showing the provider's logo. Match/channel artwork served from
     * the same host (`/img`, `/img/match`, `/posters/...`) is kept.
     */
    fun sanitizeImage(url: String?): String? {
        if (url.isNullOrBlank()) return url
        if (!isSportsAddonUrl(url)) return url
        val path = url.trim().substringAfter("://").substringAfter('/', "").substringBefore('?').lowercase()
        val isBrandLogo = path.substringAfterLast('/').startsWith("logo") ||
            path.contains("/logo/") || path.startsWith("logo") || path.contains("favicon") || path.contains("icon.")
        return if (isBrandLogo) null else url
    }

    /** Kept for call sites written before [sanitizeImage] covered every image field. */
    fun sanitizeMetaLogo(logo: String?): String? = sanitizeImage(logo)

    private val brandRegex = Regex("""(?i)\bnuvio(?:\s*live)?(?:\s*sports?)?\b""")
    private val leftoverSeparators = Regex("""^[\s\-|:•·,]+|[\s\-|:•·,]+$""")
    private val repeatedSpaces = Regex("""[ \t]{2,}""")

    /** Removes the provider's name ("Nuvio Live Sports", "Nuvio Sports", "Nuvio") from [text]. */
    fun scrubText(text: String?): String? {
        if (text.isNullOrEmpty() || !text.contains("nuvio", ignoreCase = true)) return text
        return text.lines().joinToString("\n") { line ->
            if (!line.contains("nuvio", ignoreCase = true)) line
            else line.replace(brandRegex, "").replace(repeatedSpaces, " ").replace(leftoverSeparators, "")
        }.trim().ifBlank { null }
    }

    /** [scrubText] for a required field: never returns blank. */
    fun scrubRequired(text: String, fallback: String = DISPLAY_NAME): String =
        scrubText(text)?.takeIf { it.isNotBlank() } ?: fallback

    fun scrub(preview: MetaPreview): MetaPreview = preview.copy(
        name = scrubRequired(preview.name),
        description = scrubText(preview.description),
        poster = sanitizeImage(preview.poster),
        background = sanitizeImage(preview.background),
        logo = sanitizeImage(preview.logo),
        landscapePoster = sanitizeImage(preview.landscapePoster),
        rawPosterUrl = sanitizeImage(preview.rawPosterUrl)
    )

    fun scrub(meta: Meta): Meta = meta.copy(
        name = scrubRequired(meta.name),
        description = scrubText(meta.description),
        poster = sanitizeImage(meta.poster),
        background = sanitizeImage(meta.background),
        logo = sanitizeImage(meta.logo),
        landscapePoster = sanitizeImage(meta.landscapePoster),
        rawPosterUrl = sanitizeImage(meta.rawPosterUrl),
        videos = meta.videos.map { video ->
            video.copy(
                title = scrubRequired(video.title, fallback = video.title),
                overview = scrubText(video.overview),
                thumbnail = sanitizeImage(video.thumbnail),
                seasonPoster = sanitizeImage(video.seasonPoster),
                streams = video.streams.filterNot { isNonMediaStream(it) }.map { scrub(it) }
            )
        }
    )

    fun scrub(stream: Stream): Stream = stream.copy(
        name = scrubText(stream.name),
        title = scrubText(stream.title),
        description = scrubText(stream.description),
        addonName = DISPLAY_NAME,
        addonLogo = null
    )

    private fun hostOf(url: String?): String? =
        url?.trim()?.takeIf { it.contains("://") }?.substringAfter("://")?.substringBefore('/')
            ?.substringBefore('?')?.substringBefore(':')?.lowercase()

    private fun isDonationHost(host: String?): Boolean =
        host != null && (host == "ko-fi.com" || host.endsWith(".ko-fi.com"))

    /**
     * True for this addon's non-media entries ("Support Us", Ko-fi / donation links, external
     * links to the addon's own site) that must not appear in the streams list.
     */
    fun isNonMediaStream(stream: Stream): Boolean {
        val label = listOfNotNull(stream.name, stream.title, stream.description).joinToString(" ")
        if (label.contains("support us", ignoreCase = true) || label.contains("donat", ignoreCase = true)) return true
        val urlHost = hostOf(stream.url)
        val externalHost = hostOf(stream.externalUrl)
        if (isDonationHost(urlHost) || isDonationHost(externalHost)) return true
        val urlPath = stream.url?.substringAfter("://")?.substringAfter('/', "")?.lowercase().orEmpty()
        if (urlHost == host && (urlPath.startsWith("donate") || urlPath.startsWith("support"))) return true
        val hasMedia = !stream.url.isNullOrBlank() || !stream.infoHash.isNullOrBlank() || !stream.ytId.isNullOrBlank()
        return !hasMedia && externalHost == host
    }

    /** Presents the built-in addon without its own name or logo anywhere in the UI. */
    fun brandNeutral(addon: Addon): Addon =
        addon.copy(name = DISPLAY_NAME, displayName = DISPLAY_NAME, logo = null, background = null, enabled = true)
}
