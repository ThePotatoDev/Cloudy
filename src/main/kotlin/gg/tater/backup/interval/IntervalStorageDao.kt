package gg.tater.backup.interval

interface IntervalStorageDao {

    suspend fun setLastBackup(input: String): Any

    suspend fun getLastBackup(input: String): Long?

}