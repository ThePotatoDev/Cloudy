package gg.tater.backup.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import gg.tater.backup.interval.IntervalStorageType
import gg.tater.backup.notify.BackupNotifyType
import gg.tater.backup.storage.BackupStorageType
import java.time.Duration

data class ApplicationConfig(
    var backupService: BackupStorageType = BackupStorageType.BACK_BLAZE,
    var intervalService: IntervalStorageType = IntervalStorageType.SQL,
    var notifyService: BackupNotifyType = BackupNotifyType.NONE,
    var intervalHours: Duration = Duration.ofHours(1L),
    var tempPath: String = "",

    var backupEntries: Map<String, List<String>> = emptyMap(),

    var backBlazeKeyId: String = "",
    var backBlazeKey: String = "",

    var pushoverUserKey: String = "",
    var pushoverUserName: String = "",
    var pushoverAppToken: String = "",

    var redisHost: String = "",
    var redisPassword: String = "",
    var redisPort: Int = 3306,

    var sqlDatabase: String = "",
    var sqlPassword: String = "",
    var sqlHost: String = "",
    var sqlUsername: String = ""
)

fun JsonElement.deserialize(): ApplicationConfig {
    val json: JsonObject = this as JsonObject
    val config = ApplicationConfig()

    json.get("settings").asJsonObject.apply {
        config.backupService = get("backup_service").asString.let { BackupStorageType.valueOf(it) }
        config.intervalService = get("interval_service").asString.let { IntervalStorageType.valueOf(it) }
        config.intervalHours = get("interval_hours").asLong.let { Duration.ofHours(it) }
        config.notifyService = get("notify_service").asString.let { BackupNotifyType.valueOf(it) }
        config.tempPath = get("temp_path").asString
    }

    val backupEntries: MutableMap<String, List<String>> = mutableMapOf()

    json.get("backup_entries").asJsonArray.forEach { it ->
        val eachObject: JsonObject = it as JsonObject
        val serverName = eachObject.get("name").asString
        val directories: List<String> = eachObject.get("directories")
            .asJsonArray
            .map { it.asString }
            .toList()

        backupEntries[serverName] = directories
    }

    config.backupEntries = backupEntries

    json.get("backblaze_credentials").asJsonObject.apply {
        config.backBlazeKeyId = get("app_key_id").asString
        config.backBlazeKey = get("app_key").asString
    }

    json.get("pushover_credentials").asJsonObject.apply {
        config.pushoverUserKey = get("user_key").asString
        config.pushoverUserName = get("user_name").asString
        config.pushoverAppToken = get("app_token").asString
    }

    json.get("redis_info").asJsonObject.apply {
        config.redisHost = get("host").asString
        config.redisPassword = get("password").asString
        config.redisPort = get("port").asInt
    }

    json.get("sql_info").asJsonObject.apply {
        config.sqlHost = get("host").asString
        config.sqlDatabase = get("database").asString
        config.sqlUsername = get("username").asString
        config.sqlPassword = get("password").asString
    }

    return config
}