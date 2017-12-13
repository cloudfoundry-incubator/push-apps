package unit

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.cloudfoundry.pushapps.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Mono
import java.sql.Connection
import java.sql.Statement
import javax.sql.DataSource

class DatabaseMigratorTest : Spek({
    describe("#migrate") {
        it("uses a FlywayWrapper to migrate the mysql database") {
            val migration = Migration(
                host = "example.com",
                port = "3338",
                user = "root",
                password = "supersecret",
                schema = "new_db",
                driver = DatabaseDriver.MySql(),
                migrationDir = "some/location",
                repair = false
            )

            val flywayWrapper = mock<FlywayWrapper>()
            val dataSourceFactory = mock<DataSourceFactory>()
            val dataSource = mock<DataSource>()
            val connection = mock<Connection>()
            val statement = mock<Statement>()

            whenever(dataSourceFactory.buildDataSource(any())).thenReturn(dataSource)
            whenever(dataSourceFactory.addDatabaseNameToDataSource(any(), any())).thenReturn(dataSource)
            whenever(dataSource.connection).thenReturn(connection)
            whenever(connection.createStatement()).thenReturn(statement)
            whenever(statement.execute(any())).thenReturn(true)
            whenever(flywayWrapper.migrate(any(), any(), any())).thenReturn(Mono.empty())


            val databaseMigrator = DatabaseMigrator(
                migrations = listOf(migration),
                flywayWrapper = flywayWrapper,
                dataSourceFactory = dataSourceFactory,
                maxInFlight = 1,
                retryCount = 0,
                timeoutInMinutes = 5L
            )

            databaseMigrator.migrate().toIterable().toList()

            verify(flywayWrapper).migrate(
                dataSource = dataSource,
                migrationsLocation = "some/location",
                repair = false
            )
        }

        it("uses a FlywayWrapper to migrate the postgres database") {
            val migration = Migration(
                    host = "example.com",
                    port = "3338",
                    user = "root",
                    password = "supersecret",
                    schema = "new_db",
                    driver = DatabaseDriver.Postgres(),
                    migrationDir = "some/location",
                    repair = false
            )

            val flywayWrapper = mock<FlywayWrapper>()
            val dataSourceFactory = mock<DataSourceFactory>()
            val dataSource = mock<DataSource>()
            val statement = mock<Statement>()

            whenever(dataSourceFactory.buildDataSource(any())).thenReturn(dataSource)
            whenever(dataSourceFactory.addDatabaseNameToDataSource(any(), any())).thenReturn(dataSource)
            whenever(statement.execute(any())).thenReturn(true)
            whenever(flywayWrapper.migrate(any(), any(), any())).thenReturn(Mono.empty())


            val databaseMigrator = DatabaseMigrator(
                    migrations = listOf(migration),
                    flywayWrapper = flywayWrapper,
                    dataSourceFactory = dataSourceFactory,
                    maxInFlight = 1,
                    retryCount = 0,
                    timeoutInMinutes = 5L
            )

            databaseMigrator.migrate().toIterable().toList()

            verify(flywayWrapper).migrate(
                    dataSource = dataSource,
                    migrationsLocation = "some/location",
                    repair = false
            )
        }
    }
})
