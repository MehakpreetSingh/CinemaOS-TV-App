package com.nuvio.tv.domain.repository

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import kotlinx.coroutines.flow.Flow

interface AddonRepository {
    /** The user's installed addons. Never includes built-in addons such as Sports. */
    fun getInstalledAddons(): Flow<List<Addon>>

    /**
     * Installed addons plus the app's built-in addons (currently the Sports addon), for
     * meta and stream resolution only. Do not use this to build catalog rows: built-in
     * catalogs are rendered only by their own screens.
     */
    fun getPlaybackAddons(): Flow<List<Addon>> = getInstalledAddons()

    /** The built-in Sports addon's manifest, or null until it has been fetched once. */
    fun getSportsAddon(): Flow<Addon?> = kotlinx.coroutines.flow.flowOf(null)
    /** One-time append of newly added default addons to existing installs. */
    suspend fun seedDefaultAddonsOnce() {}
    suspend fun fetchAddon(baseUrl: String): NetworkResult<Addon>
    suspend fun addAddon(url: String)
    suspend fun removeAddon(url: String)
    suspend fun setAddonOrder(urls: List<String>)
    suspend fun setAddonEnabled(url: String, enabled: Boolean)
}
