package gg.tater.backup.interval.impl

import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.interval.IntervalStorageDao
import kotlinx.coroutines.runBlocking
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import java.time.Instant

const val REDIS_MAP_NAME = "backup_map"

class RedisBackupStorage(appConfig: ApplicationConfig) : IntervalStorageDao {

    private val client: RedissonClient

    init {
        this.client = Config().apply {
            useSingleServer()
                .setAddress("redis://${appConfig.redisHost}:${appConfig.redisPort}")
                .setPassword(appConfig.redisPassword)
        }.let { Redisson.create(it) }
    }

    override suspend fun setLastBackup(input: String): Any {
        TODO("Not yet implemented")
    }

    override suspend fun getLastBackup(input: String): Instant = runBlocking {
        TODO("Not yet implemented")
    }
}