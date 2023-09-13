package gg.tater.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.config.deserialize
import gg.tater.backup.interval.IntervalStorageDao
import gg.tater.backup.interval.impl.SqlIntervalStorage
import gg.tater.backup.storage.BackupStorageHandler
import gg.tater.backup.storage.impl.BackBlazeBackupHandler
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

const val EMPTY_MILLIS = -1L

val GSON: Gson = GsonBuilder()
    .setPrettyPrinting()
    .create()
val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy hh:mm:ss")

private lateinit var config: ApplicationConfig
private lateinit var dao: IntervalStorageDao
private lateinit var handler: BackupStorageHandler

suspend fun main(): Unit = runBlocking(Dispatchers.Default) {
    val path: Path = Paths.get("config.json")
    val reader = BufferedReader(withContext(Dispatchers.IO) {
        FileReader(path.toFile())
    })

    config = GSON.fromJson(reader, JsonObject::class.java).deserialize()
    dao = SqlIntervalStorage(config)
    handler = BackBlazeBackupHandler(config)

    val backupPath = Path(config.tempPath)

    withContext(Dispatchers.IO) {
        Files.createDirectories(backupPath)
    }

    launch {
        while (isActive) {
            config.backupEntries.map {
                async {
                    val name = it.key
                    val directories = it.value
                    val stamp: Instant = dao.getLastBackup(name).let { millis ->
                        if (millis == EMPTY_MILLIS) {
                            runBackup(name, directories, true)
                            return@async
                        }

                        Instant.ofEpochMilli(millis)
                    }

                    val after: Boolean = Instant.now().isAfter(stamp.plus(config.intervalHours))
                    if (!after) {
                        println("Not enough time has passed since ${name}'s last backup, ignoring.")
                        return@async
                    }

                    runBackup(name, directories, false)
                }
            }.awaitAll()

            delay(1.minutes)
        }
    }
}

fun getFormattedBackupDate(name: String, directory: String): String {
    val suffix = directory.split("/").let { split -> split[split.lastIndex] }
    val current = LocalDateTime.now().atZone(ZoneId.of("America/New_York"))
    return "${config.tempPath}/$name/$suffix-${DATE_FORMATTER.format(current)}.zip"
}

private suspend fun runBackup(name: String, directories: List<String>, first: Boolean) {
    dao.setLastBackup(name)
    directories.forEach {
        println("${if (first) "Initial" else "Beginning"} backup of directory $it")
        handler.backup(name, it)
    }
}