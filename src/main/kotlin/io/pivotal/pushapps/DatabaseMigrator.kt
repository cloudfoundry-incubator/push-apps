package io.pivotal.pushapps

import org.flywaydb.core.Flyway
import reactor.core.publisher.Flux
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.CompletableFuture

class DatabaseMigrator(
    private val migrations: Array<Migration>
) {

    //TODO add retries
    fun migrate(): List<OperationResult> {
        val migrateDatabasesFlux: Flux<OperationResult> = Flux.create { sink ->
            val migrateDatabaseFutures = migrations.map { migration ->
                val migrateDatabaseFuture = migrateDatabase(migration)

                getOperationResult(migrateDatabaseFuture, migration.schema, false)
                    .thenApply { sink.next(it) }
            }

            CompletableFuture.allOf(*migrateDatabaseFutures
                .toTypedArray())
                .thenApply { sink.complete() }
        }

        return migrateDatabasesFlux.toIterable().toList()
    }

    fun migrateDatabase(migration: Migration): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            if (migration.driver == "mysql") {
                migrateMysqlDatabase(migration)
            }

            runFlyway(migration)
        }
    }

    private fun migrateMysqlDatabase(migration: Migration) {
        val conn = connectToMysql(migration.host, migration.port, migration.user, migration.password)
        if (conn === null) {
            throw Exception("Unable to connect to mysql on ${migration.host}:${migration.port}")
        }

        createDatabase(conn, migration.schema)
    }

    private fun connectToMysql(mysqlHost: String, mysqlPort: String, mysqlUser: String, mysqlPassword: String): Connection? {
        val connectionProps = Properties()
        connectionProps.put("user", mysqlUser)
        connectionProps.put("password", mysqlPassword)
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance()
            val conn = DriverManager.getConnection(
                "jdbc:" + "mysql" + "://" +
                    mysqlHost +
                    ":" + mysqlPort + "/" +
                    "",
                connectionProps)

            return conn
        } catch (ex: Exception) {
            throw Exception("Unable to connect to mysql on $mysqlHost:$mysqlPort due to ${ex.message}")
        }
    }

    private fun createDatabase(conn: Connection, dbName: String) {
        try {
            val stmt = conn.createStatement()

            stmt.execute("CREATE DATABASE IF NOT EXISTS " + dbName + ";")
        } catch (ex: Exception) {
            throw ex
        }
    }

    private fun runFlyway(migration: Migration) {
        val flyway = Flyway()
        val url = "jdbc:${migration.driver}://${migration.host}:${migration.port}/${migration.schema}"

        flyway.setDataSource(url, migration.user, migration.password)
        flyway.setLocations("filesystem:" + migration.migrationDir)

        flyway.migrate()
    }


}
