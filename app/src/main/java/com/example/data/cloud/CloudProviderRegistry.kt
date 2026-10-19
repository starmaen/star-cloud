package com.example.data.cloud

import android.content.Context
import com.example.data.model.CloudProviderType

class CloudProviderRegistry(private val context: Context) {
    private val googleDrive = GoogleDriveProvider(context)
    private val localSandbox = LocalMockCloudProvider(context)

    fun getProvider(type: CloudProviderType): CloudStorageProvider {
        return when (type) {
            CloudProviderType.GOOGLE_DRIVE -> googleDrive
            CloudProviderType.LOCAL_SECURE_STORAGE -> localSandbox
            CloudProviderType.DROPBOX, CloudProviderType.ONE_DRIVE -> googleDrive
        }
    }

    fun getAllProviders(): List<CloudProviderType> = listOf(
        CloudProviderType.GOOGLE_DRIVE,
        CloudProviderType.LOCAL_SECURE_STORAGE,
        CloudProviderType.DROPBOX,
        CloudProviderType.ONE_DRIVE
    )
}
