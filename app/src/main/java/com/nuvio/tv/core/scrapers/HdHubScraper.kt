package com.nuvio.tv.core.scrapers

import com.nuvio.tv.domain.model.LocalScraperResult
import org.json.JSONObject

/**
 * Native port of plugins/cinemaos/cinemaos-hdhub.js: queries the HDHub Stremio addon /stream/ endpoint (keyed by
 * IMDb id) directly, drops "Download Only" mirrors, then Range-probes (head + tail) every candidate in parallel and
 * keeps only the playable ones, 1080p first, then 720p, then 4K, then the rest.
 */
class HdHubScraper(private val http: CinemaHttp) : CinemaScraper {
    override val id = "cinemaos-hdhub"
    override val name = "CinemaOS · HdHub"
    override val description = "HDHub direct MKV / MP4 links"
    override val budgetMs = 20_000L

    private class Cand(
        val url: String,
        val n: Int,
        val quality: String,
        val label: String,
        val size: String,
        val host: String,
        val headers: Map<String, String>
    )

    override suspend fun getStreams(req: CinemaScrapeRequest): List<LocalScraperResult> {
        if (!Regex("^\\d+$").matches(req.tmdbId)) return emptyList()
        val type = if (req.isMovie) "movie" else "tv"
        val imdb = http.tmdbBrief(type, req.tmdbId).imdbId
        if (imdb.isBlank()) return emptyList()
        val path = if (req.isMovie) "movie/$imdb" else "series/$imdb%3A${req.season}%3A${req.episode}"
        val res = http.fetch("$BASE/stream/$path.json", timeoutMs = 8_000, maxBytes = 2 * 1024 * 1024) ?: return emptyList()
        if (!res.ok) return emptyList()
        val streams = runCatching { JSONObject(res.text).optJSONArray("streams") }.getOrNull() ?: return emptyList()

        val all = ArrayList<Cand>()
        for (i in 0 until streams.length()) {
            val st = streams.optJSONObject(i) ?: continue
            val url = st.str("url") ?: continue
            if (st.str("externalUrl") != null) continue
            val desc = st.str("description") ?: st.str("title") ?: ""
            val lines = desc.split('\n')
            val n = qualityOf(st.str("name").orEmpty() + " " + desc)
            val size = Regex("💾\\s*([\\d.]+\\s*[GMK]B)").find(desc)?.groupValues?.get(1) ?: ""
            val first = lines.firstOrNull().orEmpty()
            val hm = Regex("^\\[([^\\]]+)\\]").find(first)?.groupValues?.get(1)
            val host = (hm ?: lines.last().split('|')[0].ifBlank { "HDHub" }).trim()
            val file = first.replace(Regex("^(\\[[^\\]]*\\]\\s*)+"), "").ifBlank { st.str("name") ?: "" }.trim()
            val bh = st.optJSONObject("behaviorHints")?.optJSONObject("proxyHeaders")?.optJSONObject("request")
            val headers = HashMap<String, String>()
            bh?.keys()?.forEach { k -> bh.str(k)?.let { headers[k] = it } }
            all += Cand(url, n, if (n > 0) (if (n >= 2160) "4K" else "${n}p") else "HD", file, size, host, headers)
        }
        val cands = all
            .filter { !Regex("download only", RegexOption.IGNORE_CASE).containsMatchIn(it.host + " " + it.label) }
            .sortedBy { rank(it.n) }
            .take(MAX_CANDIDATES)

        // All candidates probed concurrently; stop once enough are verified.
        val ok = parallelUntil<Cand, Cand>(
            items = cands,
            timeoutMs = 9_000,
            enough = { it.size >= 8 }
        ) { c -> if (http.isVideo(c.url, c.headers, timeoutMs = 7_000)) c else null }

        return ok.sortedBy { rank(it.n) }.map { x ->
            LocalScraperResult(
                name = x.quality + (if (x.size.isNotEmpty()) " · ${x.size}" else ""),
                title = "HDHub · ${x.host} · ${x.label}",
                url = x.url,
                quality = x.quality,
                size = x.size.ifEmpty { null },
                provider = "HDHub",
                type = if (Regex("\\.mp4(\\?|&|$)", RegexOption.IGNORE_CASE).containsMatchIn(x.url)) "mp4" else "mkv",
                headers = x.headers
            )
        }
    }

    private fun rank(q: Int) = when {
        q == 1080 -> 0
        q == 720 -> 1
        q >= 2160 -> 2
        else -> 3
    }

    private fun qualityOf(text: String): Int {
        if (Regex("2160p|\\b4k\\b|uhd", RegexOption.IGNORE_CASE).containsMatchIn(text)) return 2160
        return Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private companion object {
        const val BASE = "https://hdhub.thevolecitor.qzz.io/eyJ0b3Jib3giOiJ1bnNldCIsInF1YWxpdGllcyI6IjIxNjBwLDEwODBwLDcyMHAiLCJzb3J0IjoiZGVzYyJ9"
        const val MAX_CANDIDATES = 24
    }
}
