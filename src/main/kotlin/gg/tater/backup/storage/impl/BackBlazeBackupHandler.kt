package gg.tater.backup.storage.impl

import com.backblaze.b2.client.B2StorageClient
import com.backblaze.b2.client.B2StorageClientFactory
import com.backblaze.b2.client.contentSources.B2ContentSource
import com.backblaze.b2.client.contentSources.B2ContentTypes
import com.backblaze.b2.client.contentSources.B2FileContentSource
import com.backblaze.b2.client.structures.*
import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.getFormattedBackupDate
import gg.tater.backup.storage.BackupStorageHandler
import org.zeroturnaround.zip.ZipUtil
import java.io.File
import kotlin.io.path.Path

const val AGENT_NAME = "cloud-backup"
const val BLAZE_SERVICE_ID = "backblaze"

val BLAZE_LISTENER = B2UploadListener {
    val percent: Double = (100 * (it.bytesSoFar / it.length.toDouble()))
    println(String.format("  progress(%3.2f, %s)", percent, it.toString()))
}

class BackBlazeBackupHandler(config: ApplicationConfig) : BackupStorageHandler {

    private var client: B2StorageClient = B2StorageClientFactory.createDefaultFactory()
        .create(config.backBlazeKeyId, config.backBlazeKey, AGENT_NAME)

    init {
        config.backupEntries.keys.filter { client.getBucketOrNullByName(it) == null }
            .forEach { client.createBucket(it, B2BucketTypes.ALL_PRIVATE) }
    }

    override val id: String
        get() = BLAZE_SERVICE_ID

    override fun backup(name: String, directory: String) {
        val bucketId: String = client.getBucketOrNullByName(name).bucketId
        val formattedName: String = getFormattedBackupDate(name, directory)

        File(formattedName).apply {
            createNewFile()
            ZipUtil.pack(Path(directory).toFile(), this)
            println("Zip packed $directory for bucket $name")

            val objectName: String = formattedName.split("/").let { it[it.lastIndex] }

            try {
                val source: B2ContentSource = B2FileContentSource.build(this)
                val request: B2UploadFileRequest =
                    B2UploadFileRequest.builder(bucketId, objectName, B2ContentTypes.B2_AUTO, source)
                        .setListener(BLAZE_LISTENER)
                        .setServerSideEncryption(B2FileSseForRequest.createSseB2Aes256())
                        .build()

                val version: B2FileVersion = client.uploadSmallFile(request)
                println("Uploaded ${version.fileName} to the B2 cloud. (${version.uploadTimestamp})")
            } finally {
                delete()
            }
        }
    }
}