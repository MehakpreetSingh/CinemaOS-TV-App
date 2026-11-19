package com.nuvio.tv.core.cinemaos.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.FormBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

object CinemaOsConfig {
    const val SITE_BASE_URL = "https://cinemaos.tech"

    /** Public Firebase web client key (same value as the website's lib/firebase.ts); not a secret. */
    const val FIREBASE_WEB_API_KEY = "AIzaSyAW0OmHCatRci3Qksp0ApnGEw0O3YdATlo"
}

class CinemaOsAuthException(val code: String) : Exception(code)

/** Firebase Auth REST sign-in/refresh for the CinemaOS account (email + password). */
@Singleton
class CinemaOsAccountManager @Inject constructor(
    private val store: CinemaOsAccountStore,
    private val httpClient: OkHttpClient
) {
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<CinemaOsSession?> = _session.asStateFlow()
    private val refreshMutex = Mutex()
    private val json = "application/json".toMediaType()

    val isSignedIn: Boolean get() = _session.value != null

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val verificationUrlComplete: String,
        val expiresInSec: Int,
        val intervalSec: Int
    )

    sealed interface PollResult {
        object Pending : PollResult
        object SlowDown : PollResult
        object Expired : PollResult
        object Denied : PollResult
        data class Approved(val customToken: String) : PollResult
    }

    suspend fun startDeviceLogin(): Result<DeviceCode> = runCatching {
        val res = postJson(
            "${CinemaOsConfig.SITE_BASE_URL}/api/device/start",
            JSONObject().put("deviceName", "CinemaOS TV")
        )
        DeviceCode(
            deviceCode = res.getString("deviceCode"),
            userCode = res.getString("userCode"),
            verificationUrl = res.optString("verificationUrl", "${CinemaOsConfig.SITE_BASE_URL}/tv-login"),
            verificationUrlComplete = res.getString("verificationUrlComplete"),
            expiresInSec = res.optInt("expiresIn", 600),
            intervalSec = res.optInt("interval", 5).coerceAtLeast(1)
        )
    }

    suspend fun pollDeviceLogin(deviceCode: String): Result<PollResult> = runCatching {
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${CinemaOsConfig.SITE_BASE_URL}/api/device/poll")
                .post(JSONObject().put("deviceCode", deviceCode).toString().toRequestBody(json))
                .build()
            httpClient.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val o = runCatching { JSONObject(text) }.getOrNull()
                when (o?.optString("status")) {
                    "approved" -> PollResult.Approved(o.getString("customToken"))
                    "pending" -> PollResult.Pending
                    "slow_down" -> PollResult.SlowDown
                    "expired" -> PollResult.Expired
                    "denied" -> PollResult.Denied
                    else -> when (r.code) {
                        410 -> PollResult.Expired
                        429 -> PollResult.SlowDown
                        403 -> PollResult.Denied
                        else -> throw CinemaOsAuthException("HTTP_${r.code}")
                    }
                }
            }
        }
    }

    suspend fun completeSignIn(customToken: String): Result<CinemaOsSession> = runCatching {
        val key = CinemaOsConfig.FIREBASE_WEB_API_KEY
        val res = postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=$key",
            JSONObject().put("token", customToken).put("returnSecureToken", true)
        )
        val idToken = res.getString("idToken")
        val lookup = runCatching {
            postJson(
                "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=$key",
                JSONObject().put("idToken", idToken)
            ).optJSONArray("users")?.optJSONObject(0)
        }.getOrNull()
        val label = lookup?.optString("email")?.takeIf { it.isNotBlank() }
            ?: lookup?.optString("displayName")?.takeIf { it.isNotBlank() }
            ?: "CinemaOS account"
        val uid = lookup?.optString("localId")?.takeIf { it.isNotBlank() }
            ?: res.optString("localId").takeIf { it.isNotBlank() }
            ?: throw CinemaOsAuthException("NO_UID")
        val session = CinemaOsSession(
            email = label,
            displayName = lookup?.optString("displayName")?.takeIf(String::isNotBlank),
            uid = uid,
            refreshToken = res.getString("refreshToken"),
            idToken = idToken,
            idTokenExpiresAtMs = expiry(res.optString("expiresIn"))
        )
        store.save(session)
        _session.value = session
        session
    }

    fun signOut() {
        store.clear()
        _session.value = null
    }

    /** Returns a valid Firebase ID token (refreshing when near expiry), or null when signed out/failed. */
    suspend fun getIdToken(forceRefresh: Boolean = false): String? = refreshMutex.withLock {
        val current = _session.value ?: return null
        if (!forceRefresh && current.idToken.isNotBlank() && System.currentTimeMillis() < current.idTokenExpiresAtMs - 60_000L) {
            return current.idToken
        }
        runCatching {
            val res = withContext(Dispatchers.IO) {
                val form = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", current.refreshToken)
                    .build()
                val req = Request.Builder()
                    .url("https://securetoken.googleapis.com/v1/token?key=${CinemaOsConfig.FIREBASE_WEB_API_KEY}")
                    .post(form)
                    .build()
                httpClient.newCall(req).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw CinemaOsAuthException(errorCode(text, r.code))
                    JSONObject(text)
                }
            }
            val updated = current.copy(
                idToken = res.getString("id_token"),
                refreshToken = res.optString("refresh_token", current.refreshToken),
                idTokenExpiresAtMs = expiry(res.optString("expires_in"))
            )
            store.save(updated)
            _session.value = updated
            updated.idToken
        }.getOrElse { error ->
            // A revoked/invalid refresh token means the session is dead; network errors keep it.
            if (error is CinemaOsAuthException &&
                (error.code.contains("TOKEN_EXPIRED") || error.code.contains("USER_DISABLED") ||
                    error.code.contains("USER_NOT_FOUND") || error.code.contains("INVALID_REFRESH_TOKEN"))
            ) {
                signOut()
            }
            null
        }
    }

    private suspend fun postJson(url: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).post(body.toString().toRequestBody(json)).build()
        httpClient.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw CinemaOsAuthException(errorCode(text, r.code))
            JSONObject(text)
        }
    }

    private fun errorCode(text: String, http: Int): String =
        runCatching { JSONObject(text).getJSONObject("error").getString("message") }
            .getOrDefault("HTTP_$http")

    private fun expiry(expiresInSeconds: String): Long =
        System.currentTimeMillis() + (expiresInSeconds.toLongOrNull() ?: 3600L) * 1000L
}
