package com.vibeflow.mobile.data

/**
 * VibeFlow's managed Smart Formatting backend (Supabase). The URL + anon key are
 * publishable (the anon key is RLS-gated and safe to ship); real authority comes
 * from the signed-in user's JWT. The OpenAI key lives ONLY as a server secret.
 */
object SupabaseConfig {
    const val URL = "https://emvstripgwywhcuxgjeg.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVtdnN0cmlwZ3d5d2hjdXhnamVnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODI1NjAwMDAsImV4cCI6MjA5ODEzNjAwMH0.qPj7pOZJwMg7HUUxOQa5blU9GUXxS0_S_K4I_MN-Svw"

    /** Google "Web" OAuth client id — used as the Credential Manager serverClientId. */
    const val GOOGLE_WEB_CLIENT_ID =
        "13706149885-v3c1dle2e7nmg1utp1m5d5ds2lfcah84.apps.googleusercontent.com"

    val AUTH_URL get() = "$URL/auth/v1"
    val FUNCTIONS_URL get() = "$URL/functions/v1"
}
