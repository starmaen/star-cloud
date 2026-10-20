package com.example.core.dest

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * تسجيل الدخول لـ Google Drive مباشرة. يحتاج تسجيل التطبيق في Google Cloud Console
 * (عميل OAuth من نوع Android = اسم الحزمة + بصمة SHA-1). بدونه يعطي DEVELOPER_ERROR.
 * الطريقة الأسهل للمستخدم العادي هي «مجلد» ثم اختيار Google Drive من المنتقي.
 */
class GoogleAccount(private val context: Context) {
    private val scope = Scope("https://www.googleapis.com/auth/drive.file")

    private val client: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(scope).build()
        )
    }

    fun signInIntent(): Intent = client.signInIntent
    fun last(): GoogleSignInAccount? = runCatching { GoogleSignIn.getLastSignedInAccount(context) }.getOrNull()
    fun email(): String? = last()?.email

    suspend fun signOut() = withContext(Dispatchers.IO) { runCatching { Tasks.await(client.signOut()) }; Unit }

    suspend fun token(refresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val acc = last()?.account ?: return@withContext null
        runCatching {
            val s = "oauth2:https://www.googleapis.com/auth/drive.file"
            if (refresh) runCatching { GoogleAuthUtil.clearToken(context, GoogleAuthUtil.getToken(context, acc, s)) }
            GoogleAuthUtil.getToken(context, acc, s)
        }.getOrNull()
    }

    /** بصمة SHA-1 لتوقيع التطبيق الحالي — هي المطلوبة في Google Cloud Console. */
    @Suppress("DEPRECATION")
    fun signingSha1(): String? = runCatching {
        val pm = context.packageManager
        val sig = (if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }) ?: return null
        MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString(":") { "%02X".format(it) }
    }.getOrNull()
}
