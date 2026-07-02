package com.vibeflow.mobile.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibeflow.mobile.data.KeystoreCrypto
import com.vibeflow.mobile.data.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Whether a managed-tier user is signed in, and who. */
data class AuthState(val signedIn: Boolean, val email: String)

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "vibeflow_session")

/**
 * Holds the Supabase session for the managed tier. Sign-in exchanges a Google ID
 * token for a Supabase access+refresh token; tokens are stored **encrypted** (reusing
 * [KeystoreCrypto]) and the access token is transparently refreshed before it expires.
 *
 * Networking is plain [HttpURLConnection] (matching the rest of the app) rather than a
 * heavyweight SDK — a keyboard should stay lean.
 */
class SupabaseAuth(context: Context) {

    private val store = context.applicationContext.sessionStore

    private object K {
        val access = stringPreferencesKey("sb_access")     // encrypted
        val refresh = stringPreferencesKey("sb_refresh")   // encrypted
        val expiresAt = longPreferencesKey("sb_expires_at")
        val email = stringPreferencesKey("sb_email")
        val userId = stringPreferencesKey("sb_user_id")
        val deviceId = stringPreferencesKey("device_id")
    }

    /** The signed-in user's Supabase id (the `sub`), or null when signed out. */
    suspend fun userId(): String? {
        store.data.first()[K.userId]?.ifBlank { null }?.let { return it }
        // Fallback for sessions created before we stored the id: decode `sub` from the JWT.
        val token = accessToken() ?: return null
        return runCatching {
            val payload = token.split(".")[1]
            val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
            JSONObject(json).optString("sub").ifBlank { null }
        }.getOrNull()
    }

    /** A stable random id for this install — used for the 1-phone device-binding. */
    suspend fun deviceId(): String {
        store.data.first()[K.deviceId]?.let { if (it.isNotBlank()) return it }
        val id = java.util.UUID.randomUUID().toString()
        store.edit { it[K.deviceId] = id }
        return id
    }

    /** Reactive auth state for the UI. Signed-in iff a refresh token is stored. */
    val state: Flow<AuthState> = store.data.map {
        AuthState(signedIn = !it[K.refresh].isNullOrBlank(), email = it[K.email].orEmpty())
    }

    suspend fun isSignedIn(): Boolean = !store.data.first()[K.refresh].isNullOrBlank()

    /** Exchange a Google ID token for a Supabase session, then claim this device's slot. */
    suspend fun signInWithGoogle(idToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = postJson(
                "${SupabaseConfig.AUTH_URL}/token?grant_type=id_token",
                JSONObject().put("provider", "google").put("id_token", idToken).toString(),
                bearer = null,
            )
            persist(resp)
            // Best-effort: claim the mobile slot so this becomes the one active phone.
            runCatching {
                postJson(
                    "${SupabaseConfig.FUNCTIONS_URL}/claim-device",
                    JSONObject().put("device_id", deviceId()).put("platform", "mobile").toString(),
                    bearer = resp.getString("access_token"),
                )
            }
            Unit
        }
    }

    /** Re-claim this device's mobile slot (best-effort; e.g. on app resume). */
    suspend fun claimDevice() = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext
        runCatching {
            postJson(
                "${SupabaseConfig.FUNCTIONS_URL}/claim-device",
                JSONObject().put("device_id", deviceId()).put("platform", "mobile").toString(),
                bearer = token,
            )
        }
        Unit
    }

    /**
     * A valid access token, refreshing if it's expired/near-expiry. Returns null if the
     * user isn't signed in or the refresh failed (caller should prompt sign-in).
     */
    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        val prefs = store.data.first()
        val refresh = prefs[K.refresh]?.let { KeystoreCrypto.decrypt(it) }
        if (refresh.isNullOrBlank()) return@withContext null
        val access = prefs[K.access]?.let { KeystoreCrypto.decrypt(it) }
        val expiresAt = prefs[K.expiresAt] ?: 0L
        if (!access.isNullOrBlank() && System.currentTimeMillis() < expiresAt - 60_000) return@withContext access
        runCatching {
            val resp = postJson(
                "${SupabaseConfig.AUTH_URL}/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", refresh).toString(),
                bearer = null,
            )
            persist(resp)
            resp.getString("access_token")
        }.getOrNull()
    }

    suspend fun signOut() {
        // best-effort server-side revoke, then clear local
        runCatching {
            withContext(Dispatchers.IO) {
                val token = accessToken()
                if (token != null) postRaw("${SupabaseConfig.AUTH_URL}/logout", bearer = token)
            }
        }
        // Clear the session but KEEP the device id — it's a stable per-install identifier the
        // device-scoped free pool relies on, so it must survive sign-out/sign-in.
        val device = store.data.first()[K.deviceId]
        store.edit { it.clear(); if (device != null) it[K.deviceId] = device }
    }

    private suspend fun persist(resp: JSONObject) {
        val access = resp.getString("access_token")
        val refresh = resp.getString("refresh_token")
        val expiresIn = resp.optLong("expires_in", 3600L)
        val email = resp.optJSONObject("user")?.optString("email").orEmpty()
        val uid = resp.optJSONObject("user")?.optString("id").orEmpty()
        store.edit { p ->
            KeystoreCrypto.encrypt(access)?.let { p[K.access] = it }
            KeystoreCrypto.encrypt(refresh)?.let { p[K.refresh] = it }
            p[K.expiresAt] = System.currentTimeMillis() + expiresIn * 1000
            if (email.isNotBlank()) p[K.email] = email
            if (uid.isNotBlank()) p[K.userId] = uid
        }
    }

    /** Profile (name + title) synced to Supabase so it survives reinstall. RLS = own row only. */
    suspend fun pullProfile(): Pair<String, String>? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext null
        val uid = userId() ?: return@withContext null
        runCatching {
            val arr = getArray("${SupabaseConfig.URL}/rest/v1/user_profiles?id=eq.$uid&select=display_name,title", token)
            val o = arr.optJSONObject(0) ?: return@runCatching null
            Pair(o.optString("display_name", ""), o.optString("title", ""))
        }.getOrNull()
    }

    suspend fun pushProfile(name: String, title: String) = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext
        val uid = userId() ?: return@withContext
        runCatching {
            val conn = open("${SupabaseConfig.URL}/rest/v1/user_profiles", token).apply {
                setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                doOutput = true
            }
            val body = JSONObject().put("id", uid).put("display_name", name).put("title", title).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
        }
    }

    /** The signed-in user's current free-tier state (read-only; consumes no quota). */
    data class Quota(val isPro: Boolean, val remaining: Int, val weekly: Boolean)

    /**
     * Reads the user's own profile + the public limits config (both RLS-readable) and computes
     * the remaining polishes in the active bucket (welcome → then weekly). Mirrors the server's
     * `reserve_polish` logic but writes nothing.
     */
    suspend fun fetchQuota(): Quota? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext null
        runCatching {
            val prof = getArray("${SupabaseConfig.URL}/rest/v1/profiles?select=is_pro,free_used,week_used,week_start", token)
            val cfg = getArray("${SupabaseConfig.URL}/rest/v1/app_status?select=welcome_limit,weekly_limit", token)
            val p = prof.optJSONObject(0) ?: return@runCatching null
            val c = cfg.optJSONObject(0)
            val welcomeLimit = c?.optInt("welcome_limit", 50) ?: 50
            val weeklyLimit = c?.optInt("weekly_limit", 20) ?: 20
            when {
                p.optBoolean("is_pro", false) -> Quota(true, -1, false)
                p.optInt("free_used", 0) < welcomeLimit ->
                    Quota(false, (welcomeLimit - p.optInt("free_used", 0)).coerceAtLeast(0), false)
                else -> {
                    val ws = p.optString("week_start", "")
                    val rolled = ws.isBlank() || olderThan7Days(ws)
                    Quota(false, if (rolled) weeklyLimit else (weeklyLimit - p.optInt("week_used", 0)).coerceAtLeast(0), true)
                }
            }
        }.getOrNull()
    }

    private fun olderThan7Days(iso: String): Boolean = runCatching {
        val t = java.time.OffsetDateTime.parse(iso).toInstant()
        java.time.Duration.between(t, java.time.Instant.now()).toDays() >= 7
    }.getOrDefault(true)

    private fun getArray(urlStr: String, bearer: String): org.json.JSONArray {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw RuntimeException("rest HTTP $code: ${text.take(200)}")
        return org.json.JSONArray(text)
    }

    private fun postJson(urlStr: String, body: String, bearer: String?): JSONObject {
        val conn = open(urlStr, bearer)
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw RuntimeException("auth HTTP $code: ${text.take(300)}")
        return JSONObject(text)
    }

    private fun postRaw(urlStr: String, bearer: String?) {
        val conn = open(urlStr, bearer)
        conn.doOutput = true
        conn.outputStream.use { it.write(ByteArray(0)) }
        conn.responseCode
    }

    private fun open(urlStr: String, bearer: String?): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            setRequestProperty("Authorization", "Bearer ${bearer ?: SupabaseConfig.ANON_KEY}")
        }
}
