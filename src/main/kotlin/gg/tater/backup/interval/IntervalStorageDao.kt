package gg.tater.backup.interval

import java.time.Instant

interface IntervalStorageDao {

    suspend fun setLastBackup(input: String) : Any

    suspend fun getLastBackup(input: String): Instant

}