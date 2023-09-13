package gg.tater.backup.storage

interface BackupStorageHandler {

    val id: String

    fun backup(name: String, directory: String)

}