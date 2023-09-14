package gg.tater.backup.storage.impl

import com.backblaze.b2.client.B2StorageClient
import com.backblaze.b2.client.B2StorageClientFactory
import com.backblaze.b2.client.contentSources.B2ContentTypes
import com.backblaze.b2.client.contentSources.B2FileContentSource
import com.backblaze.b2.client.structures.B2BucketTypes
import com.backblaze.b2.client.structures.B2FileSseForRequest
import com.backblaze.b2.client.structures.B2UploadFileRequest
import gg.tater.backup.alert
import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.notify.BackupNotifyHandler
import gg.tater.backup.storage.BackupStorageHandler
import org.zeroturnaround.zip.ZipUtil
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.io.path.Path

private const val AGENT_NAME = "cloud-backup"
private const val BLAZE_SERVICE_ID = "backblaze"

class BackBlazeBackupHandler(config: ApplicationConfig) : BackupStorageHandler() {

    private var service: ExecutorService = Executors.newFixedThreadPool(10)

    private var client: B2StorageClient = B2StorageClientFactory.createDefaultFactory()
        .create(config.backBlazeKeyId, config.backBlazeKey, AGENT_NAME)

    init {
        config.backupEntries.keys.filter { client.getBucketOrNullByName(it) == null }
            .forEach { client.createBucket(it, B2BucketTypes.ALL_PRIVATE) }
    }

    override val id: String get() = BLAZE_SERVICE_ID

    override suspend fun backup(notifier: BackupNotifyHandler, bucketName: String, directory: String) {
        // Create directory with bucket name
        File(bucketName).apply {
            createNewFile()
            "Generating zip archive for $directory targeting bucket $bucketName".alert()
            ZipUtil.pack(Path(directory).toFile(), this)
            getFormattedUploadName(directory).let { formattedName ->
                try {
                    B2FileContentSource.build(this).apply {
                        val bucketId: String = client.getBucketOrNullByName(bucketName).bucketId

                        B2UploadFileRequest.builder(bucketId, formattedName, B2ContentTypes.B2_AUTO, this)
                            .setServerSideEncryption(B2FileSseForRequest.createSseB2Aes256())
                            .build()
                            .apply {
                                "Beginning upload of $formattedName to the B2 cloud.".alert().let {
                                    client.uploadLargeFile(this, service)
                                        .apply { "Successfully uploaded $formattedName to the B2 cloud. (${uploadTimestamp})".alert() }
                                }
                            }
                    }
                } finally {
                    delete()
                }
            }
        }
    }
}