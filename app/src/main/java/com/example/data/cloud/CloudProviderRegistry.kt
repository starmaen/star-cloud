package com.example.data.cloud

import android.content.Context
import android.net.Uri
import com.example.data.local.SyncPreferences
import com.example.data.model.CloudProviderType

class CloudProviderRegistry(
    private val context: Context,
    private val preferences: SyncPreferences,
    private val googleAccountManager: GoogleAccountManager
) {
    private val localProvider = LocalCloudProvider(context)

    fun getActiveProvider(): CloudStorageProvider {
        if (!preferences.isCloudEnabled()) return localProvider
        if (preferences.isGoogleDriveMode()) {
            return if (googleAccountManager.isSignedIn()) {
                GoogleDriveProvider(context, googleAccountManager)
            } else {
                localProvider
            }
        }
        val uri = destinationUri() ?: return localProvider
        if (!hasWritePermission(uri)) return localProvider
        return SafCloudProvider(context, uri, detectType(uri))
    }

    fun getProvider(type: CloudProviderType): CloudStorageProvider {
        return if (type == CloudProviderType.LOCAL_SECURE_STORAGE) {
            localProvider
        } else {
            getActiveProvider()
        }
    }

    fun getLocalProvider(): CloudStorageProvider = localProvider

    fun googleAccount(): GoogleAccountManager = googleAccountManager

    fun isGoogleDriveMode(): Boolean = preferences.isGoogleDriveMode()

    fun destinationUri(): Uri? = preferences.getDestinationUri()?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun hasWritePermission(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
    }

    fun destinationReady(): Boolean {
        if (preferences.isGoogleDriveMode()) {
            return googleAccountManager.isSignedIn()
        }
        val uri = destinationUri() ?: return false
        return hasWritePermission(uri)
    }

    fun destinationType(): CloudProviderType {
        if (preferences.isGoogleDriveMode()) return CloudProviderType.GOOGLE_DRIVE
        val uri = destinationUri() ?: return CloudProviderType.LOCAL_SECURE_STORAGE
        return detectType(uri)
    }

    private fun detectType(uri: Uri): CloudProviderType {
        val authority = uri.authority ?: return CloudProviderType.LOCAL_SECURE_STORAGE
        return when {
            authority.contains("google.android.apps.docs", ignoreCase = true) -> CloudProviderType.GOOGLE_DRIVE
            authority.contains("microsoft", ignoreCase = true) -> CloudProviderType.ONE_DRIVE
            authority.contains("onedrive", ignoreCase = true) -> CloudProviderType.ONE_DRIVE
            authority.contains("dropbox", ignoreCase = true) -> CloudProviderType.DROPBOX
            else -> CloudProviderType.SAF_FOLDER
        }
    }
}
