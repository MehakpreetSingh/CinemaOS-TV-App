package com.nuvio.tv.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class OfflineDownload(
    val id: Long,
    val title: String,
    val file: File,
    val downloaded: Long,
    val total: Long,
    val state: Int,
    val validVideo: Boolean,
    val error: String? = null,
) {
    val playable: Boolean get() = state == DownloadManager.STATUS_SUCCESSFUL && validVideo && file.isFile
}

/** Android owns the transfer, including network retries and continuing after the app exits. */
internal object OfflineDownloads {
    private const val STORE = "cinemaos_offline_downloads"
    private val verifiedFiles = mutableSetOf<Long>()
    private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    private fun records(context: Context) = JSONArray(prefs(context).getString("items", "[]") ?: "[]")
    private fun manager(context: Context) = requireNotNull(context.getSystemService(DownloadManager::class.java)) {
        "Downloads are unavailable on this device"
    }

    @Synchronized
    fun enqueue(context: Context, title: String, url: String, filename: String?, headers: Map<String, String>, streamType: String? = null): Long {
        val uri = Uri.parse(url)
        require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Choose an HTTP or HTTPS video source" }
        val adaptive = AdaptiveStreamDownloads.detect(url, headers, streamType)
        if (adaptive != null) return AdaptiveDownloadJobs.enqueue(context, title, url, headers, adaptive)
        val extension = requireNotNull(DirectDownloadPolicy.extension(url, filename, streamType)) {
            "Choose a direct MP4, MKV or WebM source. Playlists and torrents cannot be downloaded here."
        }
        val directory = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)) {
            "Download storage is unavailable"
        }
        val file = File(directory, "${UUID.randomUUID()}.$extension")
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("CinemaOS offline video")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
        headers.forEach { (name, value) ->
            if (!name.equals("Range", true) && name.isNotBlank() && '\r' !in name && '\n' !in name &&
                '\r' !in value && '\n' !in value) request.addRequestHeader(name, value)
        }
        val downloads = manager(context)
        val id = downloads.enqueue(request)
        try {
            val items = records(context).put(JSONObject().put("id", id).put("title", title).put("file", file.name))
            check(prefs(context).edit().putString("items", items.toString()).commit()) { "Could not save the download" }
        } catch (error: Exception) {
            downloads.remove(id)
            throw error
        }
        return id
    }

    @Synchronized
    fun list(context: Context): List<OfflineDownload> {
        val items = records(context)
        if (items.length() == 0) return AdaptiveDownloadJobs.list(context)
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return emptyList()
        val stored = (0 until items.length()).map { items.getJSONObject(it) }
        val states = mutableMapOf<Long, Triple<Int, Long, Long>>()
        manager(context).query(DownloadManager.Query().setFilterById(*stored.map { it.getLong("id") }.toLongArray()))?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val statusColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val bytesColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            while (cursor.moveToNext()) states[cursor.getLong(idColumn)] = Triple(
                cursor.getInt(statusColumn), cursor.getLong(bytesColumn), cursor.getLong(totalColumn),
            )
        }
        return AdaptiveDownloadJobs.list(context) + stored.reversed().map { record ->
            val id = record.getLong("id")
            val file = File(directory, record.getString("file"))
            val state = states[id] ?: Triple(DownloadManager.STATUS_FAILED, 0L, 0L)
            val valid = state.first == DownloadManager.STATUS_SUCCESSFUL && file.isFile &&
                (id in verifiedFiles || runCatching {
                    file.inputStream().use { input ->
                        val header = ByteArray(16)
                        val count = input.read(header)
                        count > 0 && DirectDownloadPolicy.isVideoHeader(header.copyOf(count))
                    }
                }.getOrDefault(false).also { if (it) verifiedFiles.add(id) })
            OfflineDownload(id, record.getString("title"), file, state.second, state.third, state.first, valid)
        }
    }

    @Synchronized
    fun remove(context: Context, id: Long) {
        if (id < 0) { AdaptiveDownloadJobs.remove(context, id); return }
        manager(context).remove(id)
        val old = records(context)
        val kept = JSONArray()
        for (i in 0 until old.length()) if (old.getJSONObject(i).getLong("id") != id) kept.put(old.getJSONObject(i))
        check(prefs(context).edit().putString("items", kept.toString()).commit()) { "Could not remove the download" }
        verifiedFiles.remove(id)
    }
}
