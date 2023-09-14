package gg.tater.backup.notify.impl

import com.google.gson.JsonObject
import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.notify.BackupNotifyHandler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private const val TOKEN_FIELD = "token"
private const val USER_FIELD = "user"
private const val MESSAGE_FIELD = "message"
private const val PUSHOVER_API_ENDPOINT = "https://api.pushover.net/1/messages.json"

class PushoverNotifyHandler(appConfig: ApplicationConfig) : BackupNotifyHandler {

    private val config: ApplicationConfig = appConfig

    private val client: HttpClient = HttpClient(CIO)

    override suspend fun notify(message: String): Unit = runBlocking(Dispatchers.Default) {
        client.post {
            url(PUSHOVER_API_ENDPOINT)
            contentType(ContentType.Application.Json)
            setBody(
                createPushoverInfo(
                    token = config.pushoverAppToken,
                    user = config.pushoverUserKey,
                    message = message
                )
            )
        }
    }

    private fun createPushoverInfo(token: String, user: String, message: String): String {
        val json = JsonObject()
        json.addProperty(TOKEN_FIELD, token)
        json.addProperty(USER_FIELD, user)
        json.addProperty(MESSAGE_FIELD, message)
        return json.toString()
    }
}