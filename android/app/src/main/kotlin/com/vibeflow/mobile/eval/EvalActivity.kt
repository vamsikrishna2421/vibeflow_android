package com.vibeflow.mobile.eval

import android.app.Activity
import android.os.Bundle
import android.util.Base64
import android.util.Log
import com.vibeflow.mobile.VibeFlowApp
import com.vibeflow.mobile.data.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * DEBUG-ONLY eval harness for tuning the L3 "polish" prompt against the real managed nano model.
 * It reads raw dictation cases from assets/eval_cases.json, sends each (text + a system prompt
 * supplied at trigger time) through the managed /polish proxy using the signed-in user's token,
 * and logs every result as a one-line `VFEVAL RES {json}` to logcat. A local scorer then checks
 * assertions. The prompt is passed in at trigger time so tuning needs NO rebuild.
 *
 * Trigger (prompt is base64 so newlines/quotes survive the shell):
 *   adb shell am start -n com.vibeflow.mobile/.eval.EvalActivity \
 *       --es prompt_b64 "<base64 system prompt>" --es run_tag v2 [--ei limit 104]
 * Read results:
 *   adb logcat -d -s VFEVAL:D
 *
 * NOTE: exported for adb triggering — REMOVE this Activity (and its manifest entry) before any
 * public release. It is harmless on the developer's own test devices but must not ship.
 */
class EvalActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b64 = intent.getStringExtra("prompt_b64").orEmpty()
        val system = if (b64.isNotBlank())
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) else ""
        val limit = intent.getIntExtra("limit", 1000)
        val tag = intent.getStringExtra("run_tag").orEmpty().ifBlank { "run" }
        // When `style` is set, send it (and no system) so the SERVER builds the prompt — this
        // exercises the server-side-prompt path. Otherwise send the b64 `system` (arbitrary-prompt test).
        val style = intent.getStringExtra("style").orEmpty()
        Log.d(TAG, "START tag=$tag style='$style' system_len=${system.length} limit=$limit")
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { runEval(system, style, limit, tag) }
                .onFailure { Log.e(TAG, "FATAL ${it.javaClass.simpleName}: ${it.message}") }
            Log.d(TAG, "DONE tag=$tag")
            finish()
        }
    }

    private suspend fun runEval(system: String, style: String, limit: Int, tag: String) {
        val auth = VibeFlowApp.supabaseAuth()
        val token = auth.accessToken()
        if (token == null) { Log.e(TAG, "NO_TOKEN — sign in first"); return }
        val device = auth.deviceId()
        val casesJson = assets.open("eval_cases.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(casesJson)
        val n = minOf(arr.length(), limit)
        Log.d(TAG, "RUNNING $n cases tag=$tag")
        val sem = Semaphore(4)   // bounded concurrency — gentle on the proxy + rate limiter
        coroutineScope {
            (0 until n).map { i ->
                async {
                    sem.withPermit {
                        val c = arr.getJSONObject(i)
                        val id = c.optInt("id", i + 1)
                        val raw = c.getString("raw")
                        val polished = polish(token, device, raw, system, style)
                        val out = JSONObject()
                            .put("tag", tag).put("id", id).put("raw", raw).put("out", polished)
                        Log.d(TAG, "RES $out")
                    }
                }
            }.awaitAll()
        }
    }

    /** One managed /polish call. With [style] set, the SERVER builds the prompt; else send [system]. */
    private fun polish(token: String, device: String, text: String, system: String, style: String): String = try {
        val conn = (URL("${SupabaseConfig.FUNCTIONS_URL}/polish").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 45_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        val payload = JSONObject().put("text", text).put("device_id", device).put("platform", "eval").apply {
            if (style.isNotBlank()) put("style", style) else put("system", system)
        }.toString()
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code in 200..299) JSONObject(body).optString("text") else "ERR_$code ${body.take(120)}"
    } catch (e: Exception) { "EXC ${e.javaClass.simpleName}: ${e.message}" }

    private companion object { const val TAG = "VFEVAL" }
}
