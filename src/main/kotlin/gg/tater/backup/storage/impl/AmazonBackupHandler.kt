package gg.tater.backup.storage.impl

import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.notify.BackupNotifyHandler
import gg.tater.backup.storage.BackupStorageHandler

const val AMAZON_SERVICE_ID = "amazon"

class AmazonBackupHandler(config: ApplicationConfig) : BackupStorageHandler() {

    override val id: String
        get() = AMAZON_SERVICE_ID

    override suspend fun backup(notifier: BackupNotifyHandler, bucketName: String, directory: String) {
        TODO("Not yet implemented")
    }
}