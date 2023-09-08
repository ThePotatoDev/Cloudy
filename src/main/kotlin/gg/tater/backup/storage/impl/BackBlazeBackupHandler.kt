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

    override suspend fun backup(name: String, directory: String) {
        val bucket: B2Bucket = client.getBucketOrNullByName(name)

        File(getFormattedBackupDate(name, directory)).apply {
            createNewFile()
            ZipUtil.pack(Path(directory).toFile(), this)
            println("Zip packed $name for server $name")

            try {
                val source: B2ContentSource = B2FileContentSource.build(this)
                val request: B2UploadFileRequest =
                    B2UploadFileRequest.builder(bucket.bucketId, name, B2ContentTypes.B2_AUTO, source)
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