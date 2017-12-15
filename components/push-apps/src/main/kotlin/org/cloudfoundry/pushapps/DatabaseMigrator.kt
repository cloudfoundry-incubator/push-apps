package org.cloudfoundry.pushapps

import org.apache.logging.log4j.LogManager
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue

class DatabaseMigrator(
    private val migrations: List<Migration>,
    private val flywayWrapper: FlywayWrapper,
    private val dataSourceFactory: DataSourceFactory,
    private val maxInFlight: Int,
    private val retryCount: Int,
    private val timeoutInMinutes: Long
) {
    private val logger = LogManager.getLogger(DatabaseMigrator::class.java)

    fun migrate(): Flux<OperationResult> {
        val schemas = migrations.map(Migration::schema)
        logger.info("Running migrations for the following schemas: ${schemas.joinToString(", ")}")

        val queue = ConcurrentLinkedQueue<Migration>()
        queue.addAll(migrations)

        return Flux.create<OperationResult> { sink ->
            val subscriber = OperationScheduler<Migration>(
                maxInFlight = maxInFlight,
                sink = sink,
                operation = this::migrateDatabase,
                operationIdentifier = Migration::schema,
                operationDescription = this::migrationDescription,
                operationConfigQueue = queue
            )

            val flux = createQueueBackedFlux(queue)
            flux.subscribe(subscriber)
        }
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
            description = description,
            didSucceed = true,
            operationConfig = migration
        )

        return flywayWrapper
            .migrate(newDataSource, migration.migrationDir, migration.repair)
            .timeout(Duration.ofMinutes(timeoutInMinutes))
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