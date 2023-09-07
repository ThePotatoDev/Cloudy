package gg.tater.backup

import com.backblaze.b2.client.B2StorageClient
import com.backblaze.b2.client.B2StorageClientFactory
import com.backblaze.b2.client.contentSources.B2ContentSource
import com.backblaze.b2.client.contentSources.B2ContentTypes
import com.backblaze.b2.client.contentSources.B2FileContentSource
import com.backblaze.b2.client.structures.B2FileVersion
import com.backblaze.b2.client.structures.B2UploadFileRequest
import com.backblaze.b2.client.structures.B2UploadListener
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.config.deserialize
import gg.tater.backup.storage.BackupStorageDao
import gg.tater.backup.storage.impl.SqlBackupStorage
import kotlinx.coroutines.*
import org.zeroturnaround.zip.ZipUtil
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

const val AGENT_NAME = "server-backup"

val GSON: Gson = GsonBuilder()
    .setPrettyPrinting()
    .create()
val BACKUP_DURATION: Duration = Duration.ofHours(24L)
val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val LISTENER = B2UploadListener {
    val percent: Double = (100 * (it.bytesSoFar / it.length.toDouble()))
    println(String.format("  progress(%3.2f, %s)", percent, it.toString()))
}

suspend fun main(): Unit = runBlocking (Dispatchers.Default) {
    val path: Path = Paths.get("config.json")
    val reader = BufferedReader(withContext(Dispatchers.IO) {
        FileReader(path.toFile())
    })
    val config: ApplicationConfig = GSON.fromJson(reader, JsonObject::class.java).deserialize()
    val dao: BackupStorageDao = SqlBackupStorage(config)
    val client: B2StorageClient = B2StorageClientFactory.createDefaultFactory()
        .create(config.appKeyId, config.appKey, AGENT_NAME)

    val backupPath = Path(config.tempBackupPath)
    withContext(Dispatchers.IO) {
        Files.createDirectories(backupPath)
    }

    launch {
        while (isActive) {
            config.backupServers.map {
                async {
                    val name = it.key
                    val directories = it.value

                    val stamp = dao.getLastBackup(name)
                    val after: Boolean = Instant.now().isAfter(stamp.plus(BACKUP_DURATION))
                    if (!after) {
                        println("Not enough time has passed since ${name}'s last backup, ignoring.")
                        return@async
                    }

                    dao.setLastBackup(name)

                    val current = LocalDateTime.now()

                    directories.forEach {
                        val split: List<String> = it.split("/")
                        val suffix: String = split[split.lastIndex]
                        File("${config.tempBackupPath}/${name}/${suffix}-${FORMATTER.format(current)}.zip").apply {
                            createNewFile()
                            ZipUtil.pack(Path(it).toFile(), this)
                            println("Zip packed $it for server $name")

                            try {
                                val source: B2ContentSource = B2FileContentSource.build(this)
                                val request: B2UploadFileRequest =
                                    B2UploadFileRequest.builder(config.bucketId, this.name, B2ContentTypes.B2_AUTO, source)
                                        .setCustomField("color", "blue")
                                        .setListener(LISTENER)
                                        .build()

                                val version: B2FileVersion = client.uploadSmallFile(request)
                                println("Uploaded ${version.fileName} to the B2 cloud. (${version.uploadTimestamp})")
                            } finally {
                                delete()
                            }
                        }
                    }
                }
            }.awaitAll()

            delay(1.minutes)
        }
    }
}