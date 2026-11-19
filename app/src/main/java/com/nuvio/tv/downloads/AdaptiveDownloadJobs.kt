package com.nuvio.tv.downloads

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.PersistableBundle
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/** JobScheduler owns lifecycle/retry; private records preserve headers and progress across restarts. */
internal object AdaptiveDownloadJobs {
    private fun prefs(context: Context) = context.getSharedPreferences("adaptive_downloads", Context.MODE_PRIVATE)
    private fun records(context: Context) = JSONArray(prefs(context).getString("items", "[]"))
    private fun save(context: Context, records: JSONArray) {
        check(prefs(context).edit().putString("items", records.toString()).commit()) { "Cannot save download state" }
    }
    @Synchronized fun enqueue(context: Context, title: String, url: String, headers: Map<String, String>, kind: String): Long {
        val jobId = UUID.randomUUID().hashCode().and(0x3fffffff) + 1000
        val id = -jobId.toLong()
        val record = JSONObject().put("id", id).put("title", title).put("url", url).put("kind", kind)
            .put("headers", JSONObject(headers)).put("file", "${UUID.randomUUID()}.${if(kind == "hls") "m3u8" else "mpd"}")
            .put("state", DownloadManager.STATUS_PENDING).put("bytes", 0).put("total", -1)
        save(context, records(context).put(record))
        val job = JobInfo.Builder(jobId, ComponentName(context, AdaptiveDownloadJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setExtras(PersistableBundle().apply { putLong("id", id) })
        if (Build.VERSION.SDK_INT >= 34) job.setUserInitiated(true)
        try {
            check(context.getSystemService(JobScheduler::class.java).schedule(job.build()) == JobScheduler.RESULT_SUCCESS) {
                "Android could not schedule the download"
            }
        } catch (e: Exception) { remove(context, id); throw e }
        return id
    }
    @Synchronized fun get(context: Context, id: Long): JSONObject? {
        val list = records(context)
        return (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { it.getLong("id") == id }
    }
    fun file(context: Context, record: JSONObject) = File(requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)), record.getString("file"))
    @Synchronized fun update(context: Context, id: Long, state: Int, bytes: Long = -1, total: Long? = null, error: String? = null) {
        val list = records(context)
        for (i in 0 until list.length()) list.getJSONObject(i).takeIf { it.getLong("id") == id }?.apply {
            put("state", state)
            if (state == DownloadManager.STATUS_RUNNING && bytes < 0) put("attempts", optInt("attempts", 0) + 1)
            if (bytes >= 0) put("bytes", bytes)
            if (total != null) put("total", total)
            put("error", error)
        }
        save(context, list)
    }
    @Synchronized fun list(context: Context): List<OfflineDownload> {
        val list = records(context)
        return (0 until list.length()).reversed().map { i ->
            val r = list.getJSONObject(i)
            val file = file(context, r)
            OfflineDownload(r.getLong("id"), r.getString("title"), file, r.optLong("bytes"), r.optLong("total", -1),
                r.getInt("state"), AdaptiveStreamDownloads.isMarker(file.toURI().toString()), r.optString("error").takeIf { it.isNotBlank() })
        }
    }
    @Synchronized fun remove(context: Context, id: Long) {
        context.getSystemService(JobScheduler::class.java).cancel((-id).toInt())
        val old = records(context)
        val kept = JSONArray()
        for (i in 0 until old.length()) {
            val r = old.getJSONObject(i)
            if (r.getLong("id") == id) {
                // Running job's cancellation cleanup owns its cache; release after it has stopped.
                AdaptiveDownloadJobService.cancel(id)
                val file = file(context, r)
                cleanupScope.launch {
                    AdaptiveDownloadJobService.awaitStopped(id)
                    AdaptiveStreamDownloads.remove(file)
                    file.delete(); File(file.parentFile, file.name + ".part").delete()
                }
            } else kept.put(r)
        }
        save(context, kept)
    }
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class AdaptiveDownloadJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onStartJob(params: JobParameters): Boolean {
        val id = params.extras.getLong("id")
        val record = AdaptiveDownloadJobs.get(this, id) ?: return false
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("offline_downloads", "Offline downloads", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 34) setNotification(params, (-id).toInt(),
            Notification.Builder(this, "offline_downloads").setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(record.getString("title")).setContentText("Downloading for offline playback").setOngoing(true).build(),
            JOB_END_NOTIFICATION_POLICY_REMOVE)
        val stopped = java.util.concurrent.atomic.AtomicBoolean(false)
        stoppedJobs[id] = stopped
        val task = scope.launch(start = CoroutineStart.LAZY) {
            var retry = false
            try {
                AdaptiveDownloadJobs.update(this@AdaptiveDownloadJobService, id, DownloadManager.STATUS_RUNNING)
                val jsonHeaders = record.getJSONObject("headers")
                val headers = jsonHeaders.keys().asSequence().associateWith { jsonHeaders.getString(it) }
                val destination = AdaptiveDownloadJobs.file(this@AdaptiveDownloadJobService, record)
                var last = 0L
                val partial = AdaptiveStreamDownloads.download(this@AdaptiveDownloadJobService, record.getString("url"), headers,
                    record.getString("kind"), destination) { bytes, total ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - last > 1000 || bytes == total) {
                        last = now
                        AdaptiveDownloadJobs.update(this@AdaptiveDownloadJobService, id, DownloadManager.STATUS_RUNNING, bytes, total)
                    }
                }
                ensureActive()
                check(partial.renameTo(destination)) { "Cannot finalize offline video" }
                val bytes = AdaptiveStreamDownloads.bytes(this@AdaptiveDownloadJobService, destination)
                AdaptiveDownloadJobs.update(this@AdaptiveDownloadJobService, id, DownloadManager.STATUS_SUCCESSFUL, bytes, bytes)
            } catch (cancelled: CancellationException) {
                AdaptiveDownloadJobs.update(this@AdaptiveDownloadJobService, id, DownloadManager.STATUS_PAUSED)
                throw cancelled
            } catch (e: Exception) {
                val attempts = AdaptiveDownloadJobs.get(this@AdaptiveDownloadJobService, id)?.optInt("attempts", 0) ?: 3
                retry = e is java.io.IOException && attempts < 3
                AdaptiveDownloadJobs.update(this@AdaptiveDownloadJobService, id,
                    if (retry) DownloadManager.STATUS_PAUSED else DownloadManager.STATUS_FAILED, error = e.message)
            } finally {
                tasks.remove(id, currentCoroutineContext().job)
                stoppedJobs.remove(id, stopped)
                if (!stopped.get()) withContext(NonCancellable + Dispatchers.Main) { jobFinished(params, retry) }
            }
        }
        tasks[id] = task
        task.start()
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        val id = params.extras.getLong("id")
        stoppedJobs[id]?.set(true)
        cancel(id)
        return true
    }
    override fun onDestroy() { stoppedJobs.values.forEach { it.set(true) }; scope.cancel(); super.onDestroy() }
    companion object {
        private val stoppedJobs = java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicBoolean>()
        private val tasks = java.util.concurrent.ConcurrentHashMap<Long, Job>()
        internal fun cancel(id: Long) { tasks[id]?.cancel() }
        internal suspend fun awaitStopped(id: Long) { tasks[id]?.join() }
    }
}
