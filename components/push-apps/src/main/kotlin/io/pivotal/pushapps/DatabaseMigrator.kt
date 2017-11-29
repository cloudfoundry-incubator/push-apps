package io.pivotal.pushapps

import org.apache.logging.log4j.LogManager
import reactor.core.publisher.Flux
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.CompletableFuture
import javax.sql.DataSource

class DatabaseMigrator(
    private val migrations: List<Migration>,
    private val flywayWrapper: FlywayWrapper,
    private val dataSourceFactory: DataSourceFactory
) {
    private val logger = LogManager.getLogger(DatabaseMigrator::class.java)

    //TODO add retries
    fun migrate(): List<OperationResult> {
        val migrateDatabasesFlux: Flux<OperationResult> = Flux.create { sink ->
            val migrateDatabaseFutures = migrations.map { migration ->
                val migrateDatabaseFuture = CompletableFuture.runAsync {
                    val dataSource = dataSourceFactory.buildDataSource(migration)
                    migrateDatabase(dataSource, migration)
                }

                getOperationResult(migrateDatabaseFuture, migration.schema, false)
                    .thenApply { sink.next(it) }
            }

            CompletableFuture.allOf(*migrateDatabaseFutures.toTypedArray())
                .thenApply { sink.complete() }
        }

        return migrateDatabasesFlux.toIterable().toList()
    }

    private fun migrateDatabase(dataSource: DataSource, migration: Migration) {
        if (migration.driver is DatabaseDriver.MySql) createDatabaseIfAbsent(dataSource.connection, migration.schema)
        val newDataSource = dataSourceFactory.addDatabaseNameToDataSource(dataSource, migration)
        flywayWrapper.migrate(newDataSource, migration.migrationDir)
    }

    private fun createDatabaseIfAbsent(conn: Connection, dbName: String) {
        try {
            val stmt = conn.createStatement()
            stmt.execute("CREATE DATABASE IF NOT EXISTS $dbName;")
        } catch (ex: SQLException) {
            logger.error("Unable to create database $dbName. Caught exception: ${ex.message}")
        }
    }
}
