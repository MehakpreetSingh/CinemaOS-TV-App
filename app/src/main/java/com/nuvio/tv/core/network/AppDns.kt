package com.nuvio.tv.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** "Secure DNS (DNS over HTTPS)" setting. [storageKey] is what is persisted. */
enum class SecureDnsMode(val storageKey: String) {
    /** Cloudflare DoH, then Google DoH, then the system resolver. */
    AUTOMATIC("automatic"),
    CLOUDFLARE("cloudflare"),
    GOOGLE("google"),

    /** System DNS only (the pre-DoH behaviour). */
    OFF("off");

    companion object {
        val DEFAULT = AUTOMATIC

        fun fromStorage(value: String?): SecureDnsMode =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}

/**
 * The one [Dns] every app OkHttpClient uses (catalogs, addons, native scrapers, plugins, images,
 * playback). Resolves through DNS over HTTPS so ISP DNS blocks / hijacks of source hosts do not
 * break stream search, and falls back to [Dns.SYSTEM] on any DoH failure or empty answer, so it can
 * never resolve less than the system resolver did. IPv4 addresses are kept first ([IPv4FirstDns]).
 *
 * The mode is persisted in SharedPreferences (not DataStore) because OkHttp calls [lookup]
 * synchronously on its own threads; the value is read at lookup time from an in-memory copy.
 */
object AppDns : Dns {
    private const val TAG = "AppDns"
    private const val PREFS_NAME = "cinemaos_network_settings"
    private const val KEY_SECURE_DNS_MODE = "secure_dns_mode"

    private const val CACHE_TTL_MS = 5 * 60_000L
    private const val CACHE_MAX_ENTRIES = 512

    /** A provider that failed at the transport level (blocked / unreachable) is skipped this long. */
    private const val PROVIDER_COOLDOWN_MS = 2 * 60_000L

    private enum class Provider(val url: String, val bootstrap: List<String>) {
        CLOUDFLARE("https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
        GOOGLE("https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4"))
    }

    private class CacheEntry(val expiresAt: Long, val addresses: List<InetAddress>)

    @Volatile
    private var prefs: SharedPreferences? = null
    private val _mode = MutableStateFlow(SecureDnsMode.DEFAULT)

    /** Current setting, for the settings UI. */
    val mode: StateFlow<SecureDnsMode> = _mode.asStateFlow()

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val providerDownUntil = ConcurrentHashMap<Provider, Long>()

    /** Small dedicated client for the DoH queries themselves; it must never use [AppDns]. */
    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private val resolvers = ConcurrentHashMap<Provider, DnsOverHttps>()

    private val ordered = IPv4FirstDns(
        delegate = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = resolve(hostname)
        }
    )

    /** Loads the persisted mode. Called from Application.onCreate before anything does network IO. */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _mode.value = SecureDnsMode.fromStorage(p.getString(KEY_SECURE_DNS_MODE, null))
            prefs = p
        }
    }

    fun setMode(mode: SecureDnsMode) {
        prefs?.edit()?.putString(KEY_SECURE_DNS_MODE, mode.storageKey)?.apply()
        _mode.value = mode
        cache.clear()
        providerDownUntil.clear()
    }

    override fun lookup(hostname: String): List<InetAddress> = ordered.lookup(hostname)

    private fun resolve(hostname: String): List<InetAddress> {
        val mode = _mode.value
        if (mode == SecureDnsMode.OFF || !isPublicHostname(hostname)) {
            return Dns.SYSTEM.lookup(hostname)
        }
        val host = hostname.lowercase()
        val now = System.currentTimeMillis()
        val cacheKey = "${mode.storageKey}|$host"
        cache[cacheKey]?.let { entry ->
            if (entry.expiresAt > now) return entry.addresses
            cache.remove(cacheKey)
        }

        for (provider in providersFor(mode)) {
            val downUntil = providerDownUntil[provider]
            if (downUntil != null && downUntil > now) continue
            try {
                val addresses = resolverFor(provider).lookup(host)
                if (addresses.isNotEmpty()) {
                    if (cache.size >= CACHE_MAX_ENTRIES) cache.clear()
                    cache[cacheKey] = CacheEntry(now + CACHE_TTL_MS, addresses)
                    return addresses
                }
            } catch (e: UnknownHostException) {
                // No cause: the DoH server answered (NXDOMAIN / no records). A cause means the DoH
                // request itself failed (blocked / timed out), so rest that provider for a while.
                if (e.cause != null) markDown(provider, e)
            } catch (e: Exception) {
                markDown(provider, e)
            }
        }
        // Any DoH failure or empty answer: the system resolver, exactly as before DoH existed.
        return Dns.SYSTEM.lookup(hostname)
    }

    private fun markDown(provider: Provider, error: Throwable) {
        providerDownUntil[provider] = System.currentTimeMillis() + PROVIDER_COOLDOWN_MS
        Log.w(TAG, "DoH ${provider.name} unavailable, using fallback: ${error.cause?.message ?: error.message}")
    }

    private fun providersFor(mode: SecureDnsMode): List<Provider> = when (mode) {
        SecureDnsMode.AUTOMATIC -> listOf(Provider.CLOUDFLARE, Provider.GOOGLE)
        SecureDnsMode.CLOUDFLARE -> listOf(Provider.CLOUDFLARE)
        SecureDnsMode.GOOGLE -> listOf(Provider.GOOGLE)
        SecureDnsMode.OFF -> emptyList()
    }

    private fun resolverFor(provider: Provider): DnsOverHttps = resolvers.getOrPut(provider) {
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(provider.url.toHttpUrl())
            .bootstrapDnsHosts(provider.bootstrap.map { InetAddress.getByName(it) })
            .includeIPv6(true)
            // Private / LAN names are filtered out before DoH (isPublicHostname); skipping OkHttp's
            // own public-suffix check avoids loading that database on the lookup path.
            .resolvePrivateAddresses(true)
            .build()
    }

    /**
     * IP literals, localhost and LAN-only names (single label, .local, .lan, ...) are not public DNS
     * names: they always go to the system resolver (mDNS, router DNS, hosts file).
     */
    private fun isPublicHostname(hostname: String): Boolean {
        val host = hostname.trim().trimEnd('.').lowercase()
        if (host.isEmpty() || !host.contains('.')) return false
        if (host.contains(':')) return false // IPv6 literal
        if (host.all { it.isDigit() || it == '.' }) return false // IPv4 literal
        if (host == "localhost" || host.endsWith(".localhost")) return false
        return LAN_SUFFIXES.none { host.endsWith(it) }
    }

    private val LAN_SUFFIXES = listOf(
        ".local", ".lan", ".home", ".internal", ".localdomain", ".home.arpa", ".in-addr.arpa", ".ip6.arpa", ".test"
    )
}
