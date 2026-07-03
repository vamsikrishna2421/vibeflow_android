package com.vibeflow.mobile.ai

import com.vibeflow.mobile.auth.SupabaseAuth
import com.vibeflow.mobile.data.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The **managed** Smart Formatting path: instead of calling OpenAI with the user's own
 * key (BYOK), it calls Mynah's Supabase `polish` Edge Function with the signed-in
 * user's token. The server holds the OpenAI key, enforces the free-50 limit / Pro
 * entitlement, and meters usage. The same [SmartFormatter.systemPrompt] styles are
 * reused so output matches the BYOK path exactly.
 */
class ManagedFormatter(private val auth: SupabaseAuth) {

    sealed class Result {
        data class Success(
            val text: String, val remaining: Int, val isPro: Boolean,
            val promptTokens: Int, val completionTokens: Int,
        ) : Result()
        object NeedsSignIn : Result()      // not signed in / refresh failed
        object LimitReached : Result()      // free 50 used up and not Pro
        object DeviceSuperseded : Result()  // a newer sign-in took this phone's slot
        object TooLong : Result()           // input exceeds the cost-guard char cap
        data class Maintenance(val message: String) : Result()  // server paused for maintenance
        data class Error(val message: String) : Result()
    }

    suspend fun format(
        text: String, style: String,
        userName: String = "", userTitle: String = "", appName: String = "",
    ): Result = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.Error("Nothing to format")
        if (text.length > SmartFormatter.MAX_INPUT_CHARS) return@withContext Result.TooLong
        val token = auth.accessToken() ?: return@withContext Result.NeedsSignIn
        val system = SmartFormatter.systemPrompt(style, userName, userTitle, appName)
        val device = auth.deviceId()
        runCatching {
            val conn = (URL("${SupabaseConfig.FUNCTIONS_URL}/polish").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 45_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }
            // Send `style` + personalization so the SERVER builds the prompt (prompt improvements
            // ship without an app update). `system` stays as a fallback for older server versions.
            val payload = JSONObject().put("text", text)
                .put("style", style)
                .put("userName", userName).put("userTitle", userTitle).put("appName", appName)
                .put("system", system)
                .put("device_id", device).put("platform", "mobile").toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            when (code) {
                in 200..299 -> {
                    val j = JSONObject(body)
                    Result.Success(
                        text = j.getString("text"),
                        remaining = j.optInt("remaining", -1),
                        isPro = j.optBoolean("isPro", false),
                        promptTokens = j.optInt("promptTokens", 0),
                        completionTokens = j.optInt("completionTokens", 0),
                    )
                }
                401 -> Result.NeedsSignIn
                402 -> Result.LimitReached
                409 -> Result.DeviceSuperseded
                413 -> Result.TooLong
                503 -> {
                    val j = runCatching { JSONObject(body) }.getOrNull()
                    if (j?.optString("error") == "maintenance") Result.Maintenance(j.optString("message"))
                    else Result.Error("polish 503")
                }
                else -> Result.Error("polish $code: ${body.take(160)}")
            }
        }.getOrElse { Result.Error(it.message ?: "network error") }
    }
}
