package gg.tater.backup.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class ApplicationConfig(
    val backupServers: Map<String, List<String>>,
    val appKeyId: String,
    val appKey: String,
    val bucketId: String,
    val tempBackupPath: String,
    val sqlDatabase: String,
    val sqlPassword: String,
    val sqlHost: String,
    val sqlUsername: String
)

fun JsonElement.deserialize(): ApplicationConfig {
    val json: JsonObject = this as JsonObject
    val backupServers: MutableMap<String, List<String>> = mutableMapOf()
    val appKeyId: String = json.get("app_key_id").asString
    val appKey: String = json.get("app_key").asString
    val bucketId: String = json.get("bucket_id").asString
    val tempBackupPath: String = json.get("temp_backup_path").asString

    json.get("backup_servers").asJsonArray.forEach { it ->
        val eachObject: JsonObject = it as JsonObject
        val serverName = eachObject.get("server_name").asString
        val directories: List<String> = eachObject.get("directories")
            .asJsonArray
            .map { it.asString }
            .toList()

        backupServers[serverName] = directories
    }

    var host: String
    var database: String
    var username: String
    var password: String

    json.get("database_info").asJsonObject.apply {
        host = this.get("host").asString
        database = this.get("database").asString
        username = this.get("username").asString
        password = this.get("password").asString
    }

    return ApplicationConfig(
        backupServers, appKeyId, appKey, bucketId, tempBackupPath, database, password, host, username
    )
}