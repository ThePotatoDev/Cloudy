package gg.tater.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.config.deserialize
import gg.tater.backup.interval.BackupIntervalStorageDao
import gg.tater.backup.interval.BackupIntervalStorageType
import gg.tater.backup.interval.impl.RedisIntervalStorage
import gg.tater.backup.interval.impl.SqlIntervalStorage
import gg.tater.backup.notify.BackupNotifyHandler
import gg.tater.backup.notify.BackupNotifyType
import gg.tater.backup.notify.impl.DiscordNotifyHandler
import gg.tater.backup.notify.impl.PushoverNotifyHandler
import gg.tater.backup.storage.BackupStorageHandler
import gg.tater.backup.storage.BackupStorageType
import gg.tater.backup.storage.impl.AmazonBackupHandler
import gg.tater.backup.storage.impl.BackBlazeBackupHandler
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

val GSON: Gson = GsonBuilder()
    .setPrettyPrinting()
    .create()

private lateinit var config: ApplicationConfig
private lateinit var backupHandler: BackupStorageHandler
lateinit var notifier: BackupNotifyHandler

suspend fun main(): Unit = runBlocking(Dispatchers.Default) {
    val path: Path = Paths.get("config.json")
    val reader = BufferedReader(withContext(Dispatchers.IO) {
        FileReader(path.toFile())
    })

    config = GSON.fromJson(reader, JsonObject::class.java).deserialize()
    backupHandler =
        if (config.backupService == BackupStorageType.BACK_BLAZE) BackBlazeBackupHandler(config) else AmazonBackupHandler(
            config
        )

    val dao: BackupIntervalStorageDao =
        if (config.intervalService == BackupIntervalStorageType.SQL) SqlIntervalStorage(config) else RedisIntervalStorage(
            config
        )

    notifier =
        if (config.notifyService == BackupNotifyType.PUSHOVER) PushoverNotifyHandler(config) else DiscordNotifyHandler()

    val backupPath = Path(config.tempPath)

    withContext(Dispatchers.IO) {
        Files.createDirectories(backupPath)
    }

    launch {
        while (isActive) {
            config.backupEntries.map {
                async {
                    val bucketName = it.key
                    val directories = it.value
                    (dao.getLastBackup(bucketName)?.let { millis -> Instant.ofEpochMilli(millis) } ?: Instant.MAX).apply {
                        if (this.equals(Instant.MAX)) {
                            run(notifier, dao, bucketName, directories, true)
                            return@async
                        }

                        val after: Boolean = Instant.now().isAfter(this.plus(config.intervalHours))
                        if (!after) {
                            println("Not enough time has passed since $bucketName's last backup, ignoring.")
                            return@async
                        }

                        run(notifier, dao, bucketName, directories, false)
                    }
                }
            }.awaitAll()

            delay(1.minutes)
        }
    }
}

suspend inline fun String.alert() {
    notifier.notify(this)
    println(this)
}

private suspend fun run(
    notifier: BackupNotifyHandler,
    dao: BackupIntervalStorageDao,
    bucketName: String,
    directories: List<String>,
    first: Boolean
) {
    dao.setLastBackup(bucketName)
    directories.forEach {
        println("Starting ${if (first) "initial" else "routine"} backup of directory $it")
        backupHandler.backup(notifier, bucketName, it)
    }
}