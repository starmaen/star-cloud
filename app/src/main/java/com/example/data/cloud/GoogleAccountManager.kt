package com.example.data.cloud

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles the real Google account sign-in flow and provides OAuth access
 * tokens for the Drive REST API. The user signs in with a genuine Google
 * account picker; all uploads then go directly to that user's Drive.
 */
class GoogleAccountManager(private val context: Context) {

    private val driveScope = Scope(DRIVE_SCOPE)

    private val client: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(driveScope)
                .build()
        )
    }

    fun signInIntent(): Intent = client.signInIntent

    fun lastAccount(): GoogleSignInAccount? =
        runCatching { GoogleSignIn.getLastSignedInAccount(context) }.getOrNull()

    fun isSignedIn(): Boolean = lastAccount() != null

    fun accountEmail(): String? = lastAccount()?.email

    fun accountDisplayName(): String? = lastAccount()?.displayName

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { Tasks.await(client.signOut()) }
        Unit
    }

    suspend fun revokeAccess() = withContext(Dispatchers.IO) {
        runCatching { Tasks.await(client.revokeAccess()) }
        Unit
    }

    /**
     * Returns an OAuth2 access token for the Drive scope. Known Google accounts
     * on the device return the token without prompting because the scope was
     * already granted during sign-in.
     */
    suspend fun accessToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val account = lastAccount() ?: return@withContext null
        val googleAccount = account.account ?: return@withContext null
        runCatching {
            if (forceRefresh) {
                GoogleAuthUtil.clearToken(context, "oauth2:$DRIVE_SCOPE")
            }
            GoogleAuthUtil.getToken(context, googleAccount, "oauth2:$DRIVE_SCOPE")
        }.getOrNull()
    }

    companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
