package io.pivotal.pushapps

import org.apache.logging.log4j.LogManager
import reactor.core.publisher.Mono
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.ConcurrentLinkedQueue

class DatabaseMigrator(
    private val migrations: List<Migration>,
    private val flywayWrapper: FlywayWrapper,
    private val dataSourceFactory: DataSourceFactory,
    private val maxInFlight: Int,
    private val retryCount: Int
) {
    private val logger = LogManager.getLogger(DatabaseMigrator::class.java)

    fun migrate(): List<OperationResult> {
        val schemas = migrations.map(Migration::schema)
        logger.info("Running migrations for the following schemas: ${schemas.joinToString(", ")}")

        val queue = ConcurrentLinkedQueue<Migration>()
        queue.addAll(migrations)

        val subscriber = OperationScheduler<Migration>(
            maxInFlight = maxInFlight,
            operation = this::migrateDatabase,
            operationIdentifier = Migration::schema,
            operationDescription = this::migrationDescription,
            operationConfigQueue = queue,
            retries = retryCount
        )

        val flux = createQueueBackedFlux(queue)
        flux.subscribe(subscriber)

        return subscriber.results.get()
    }

    private fun migrationDescription(migration: Migration): String {
        return "Migrating ${migration.driver.name} schema ${migration.schema}."
    }

    private fun migrateDatabase(migration: Migration): Mono<OperationResult> {
        val dataSource = dataSourceFactory.buildDataSource(migration)
        if (migration.driver is DatabaseDriver.MySql) createDatabaseIfAbsent(dataSource.connection, migration.schema)
        val newDataSource = dataSourceFactory.addDatabaseNameToDataSource(dataSource, migration)
        val description = migrationDescription(migration)
        val operationResult = OperationResult(
            name = description,
            didSucceed = true
        )

        return flywayWrapper
            .migrate(newDataSource, migration.migrationDir, migration.repair)
            .transform(logAsyncOperation(logger, description))
            .then(Mono.just(operationResult))
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
