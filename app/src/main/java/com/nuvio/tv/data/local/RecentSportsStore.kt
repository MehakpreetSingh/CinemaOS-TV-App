package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentSportsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_sports_store",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** A live Sports item the user played, kept so it can be reopened from the Sports screen. */
data class RecentSportsItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val logo: String?,
    val addonBaseUrl: String?,
    val timestamp: Long
)

/** The last [MAX_ITEMS] live Sports items played, most recent first, de-duplicated by id. */
@Singleton
class RecentSportsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val itemsKey = stringPreferencesKey("recent_items_json")

    val items: Flow<List<RecentSportsItem>> = context.recentSportsDataStore.data.map { decode(it[itemsKey]) }

    suspend fun record(item: RecentSportsItem) {
        context.recentSportsDataStore.edit { prefs ->
            val current = decode(prefs[itemsKey])
            val updated = (listOf(item) + current.filterNot { it.id.equals(item.id, ignoreCase = true) })
                .take(MAX_ITEMS)
            prefs[itemsKey] = encode(updated)
        }
    }

    suspend fun remove(id: String) {
        context.recentSportsDataStore.edit { prefs ->
            prefs[itemsKey] = encode(decode(prefs[itemsKey]).filterNot { it.id.equals(id, ignoreCase = true) })
        }
    }

    suspend fun clear() {
        context.recentSportsDataStore.edit { it.remove(itemsKey) }
    }

    private fun encode(list: List<RecentSportsItem>): String = JSONArray().also { array ->
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("type", it.type)
                    .put("name", it.name)
                    .put("poster", it.poster ?: JSONObject.NULL)
                    .put("logo", it.logo ?: JSONObject.NULL)
                    .put("addon", it.addonBaseUrl ?: JSONObject.NULL)
                    .put("ts", it.timestamp)
            )
        }
    }.toString()

    private fun decode(raw: String?): List<RecentSportsItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RecentSportsItem(
                    id = id,
                    type = obj.optString("type").ifBlank { "tv" },
                    name = obj.optString("name"),
                    poster = obj.optString("poster").takeIf { it.isNotBlank() && it != "null" },
                    logo = obj.optString("logo").takeIf { it.isNotBlank() && it != "null" },
                    addonBaseUrl = obj.optString("addon").takeIf { it.isNotBlank() && it != "null" },
                    timestamp = obj.optLong("ts")
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val MAX_ITEMS = 20
    }
}
