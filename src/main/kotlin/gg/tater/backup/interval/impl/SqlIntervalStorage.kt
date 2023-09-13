package gg.tater.backup.interval.impl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.interval.IntervalStorageDao
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class SqlIntervalStorage(config: ApplicationConfig) : IntervalStorageDao {

    private object BackupInfo : Table("backup_info") {
        val id: Column<String> = varchar("id", 32).uniqueIndex()
        val timestamp: Column<Long> = long("timestamp").default(Instant.now().toEpochMilli())
        override val primaryKey = PrimaryKey(id)
    }

    init {
        Database.connect(HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://${config.sqlHost}/${config.sqlDatabase}"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            username = config.sqlUsername
            password = config.sqlPassword
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
        }.let { HikariDataSource(it) })

        transaction {
            SchemaUtils.createMissingTablesAndColumns(BackupInfo)
        }
    }

    override suspend fun setLastBackup(input: String) = newSuspendedTransaction {
        BackupInfo.insertIgnore {
            it[id] = input
        }
    }

    override suspend fun getLastBackup(input: String): Long? = newSuspendedTransaction {
        BackupInfo.select { BackupInfo.id eq input }.firstOrNull()?.getOrNull(BackupInfo.timestamp)
    }
}