package gg.tater.backup.storage

import java.time.Instant

interface BackupStorageDao {

    suspend fun setLastBackup(input: String) : Any

    suspend fun getLastBackup(input: String): Instant

}