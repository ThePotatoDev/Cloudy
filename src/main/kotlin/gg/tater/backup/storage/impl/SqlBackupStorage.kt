package gg.tater.backup.storage.impl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.storage.BackupStorageDao
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class SqlBackupStorage(appConfig: ApplicationConfig) : BackupStorageDao {

    private object BackupInfo : Table("backup_info") {
        val id: Column<String> = varchar("id", 32).uniqueIndex()
        val timestamp: Column<Long> = long("timestamp").default(Instant.now().toEpochMilli())
        override val primaryKey = PrimaryKey(id)
    }

    init {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://${appConfig.sqlHost}/${appConfig.sqlDatabase}"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            username = appConfig.sqlUsername
            password = appConfig.sqlPassword
            maximumPoolSize = 4
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("characterEncoding", "utf8")
            addDataSourceProperty("useUnicode", "true")
            addDataSourceProperty("useSSL", "false")
            addDataSourceProperty("useJDBCCompliantTimezoneShift", "true")
            addDataSourceProperty("useLegacyDatetimeCode", "false")
            addDataSourceProperty("serverTimezone", TimeZone.getDefault().id)
        }

        val datasource = HikariDataSource(config)
        Database.connect(datasource)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(BackupInfo)
        }
    }

    override suspend fun setLastBackup(input: String) = newSuspendedTransaction {
        BackupInfo.insertIgnore {
            it[id] = input
        }
    }

    override suspend fun getLastBackup(input: String): Instant = newSuspendedTransaction {
        Instant.ofEpochMilli(BackupInfo.select { BackupInfo.id eq input }.firstOrNull()?.getOrNull(BackupInfo.timestamp).let {
            setLastBackup(input)
            System.currentTimeMillis()
        })
    }
}