package gg.tater.backup.storage.impl

import gg.tater.backup.storage.BackupStorageHandler

const val AMAZON_SERVICE_ID = "amazon"

class AmazonBackupHandler: BackupStorageHandler {

    override val id: String
        get() = AMAZON_SERVICE_ID

    override fun backup(name: String, directory: String) {
        TODO("Not yet implemented")
    }
}