package com.nuvio.tv.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.io.File

private const val TAG = "UpdateInstall"

/**
 * Receives the outcome of an update install session. A confirmation request is shown only for installs the user
 * started; a background install that still needs confirmation is dropped and the downloaded APK waits for the banner.
 * A failed session is retried without dex metadata, then falls back to the system installer screen. A session the
 * user declined, or that was abandoned on purpose, is left alone so the user is not asked again.
 */
class UpdateInstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val background = intent.getBooleanExtra(EXTRA_BACKGROUND, false)
        val withDexMetadata = intent.getBooleanExtra(EXTRA_WITH_DEX_METADATA, false)
        val apkFile = intent.getStringExtra(EXTRA_APK_PATH)?.let(::File)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "Session $sessionId status=$status background=$background dm=$withDexMetadata message=$message")

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (background) {
                    runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
                } else {
                    confirmationIntent(intent)?.let { confirm ->
                        runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure {
                                Log.w(TAG, "Install confirmation screen unavailable", it)
                                runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
                                retryAfterFailure(context, apkFile, background = false, withDexMetadata = false)
                            }
                    }
                }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateRelaunchReceiver.cancelReopen(context)
            else -> {
                UpdateRelaunchReceiver.cancelReopen(context)
                retryAfterFailure(context, apkFile, background, withDexMetadata)
            }
