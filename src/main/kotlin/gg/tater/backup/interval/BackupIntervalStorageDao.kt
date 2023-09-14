package gg.tater.backup.interval

interface BackupIntervalStorageDao {

    suspend fun setLastBackup(input: String): Any

    suspend fun getLastBackup(input: String): Long?

}