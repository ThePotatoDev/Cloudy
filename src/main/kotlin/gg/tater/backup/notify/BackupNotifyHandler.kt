package gg.tater.backup.notify

interface BackupNotifyHandler {

    suspend fun notify(message: String)

}