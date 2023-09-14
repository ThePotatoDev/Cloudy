package gg.tater.backup.storage

import gg.tater.backup.notify.BackupNotifyHandler
import java.time.LocalDateTime
import java.time.ZoneId

abstract class BackupStorageHandler {

    abstract val id: String

    abstract suspend fun backup(notifier: BackupNotifyHandler, bucketName: String, directory: String)

    protected fun getFormattedUploadName(directory: String): String {
        val name: String = directory.split("/").let { it[it.lastIndex] }
        val date: String = LocalDateTime.now().atZone(ZoneId.of("America/New_York"))
            .let { "${it.month.value}-${it.dayOfMonth}-${it.year}" }
        return "$name-$date.zip"
    }

}