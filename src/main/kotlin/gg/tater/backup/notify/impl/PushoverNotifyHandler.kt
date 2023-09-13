package gg.tater.backup.notify.impl

import de.svenkubiak.jpushover.JPushover
import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.notify.BackupNotifyHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PushoverNotifyHandler(appConfig: ApplicationConfig) : BackupNotifyHandler {

    private var config: ApplicationConfig = appConfig

    override suspend fun notify(message: String): Unit = runBlocking(Dispatchers.Default) {
        launch {
            JPushover.messageAPI()
                .withToken(config.pushoverAppToken)
                .withUser(config.pushoverUserName)
                .withMessage(message)
                .push()
        }
    }
}