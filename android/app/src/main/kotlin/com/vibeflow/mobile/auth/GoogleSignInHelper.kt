package com.vibeflow.mobile.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.vibeflow.mobile.data.SupabaseConfig

/**
 * Native "Sign in with Google" via Credential Manager. Returns the Google **ID token**,
 * which [SupabaseAuth] exchanges for a Supabase session. Must be called with an
 * **Activity** context (it shows the account-picker UI).
 */
object GoogleSignInHelper {

    suspend fun getIdToken(activityContext: Context): Result<String> = runCatching {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(SupabaseConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)   // show all accounts, not just previously-used
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(cred.data).idToken
        } else {
            error("Unexpected credential type: ${cred.type}")
        }
    }
}
