package gg.tater.backup.storage

interface BackupStorageHandler {

    val id: String

    suspend fun backup(name: String, directory: String)

}