package unit

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.pivotal.pushapps.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.sql.Connection
import java.sql.Statement
import javax.sql.DataSource

class DatabaseMigratorTest : Spek({
    describe("#migrate") {
        it("uses a FlywayWrapper to migrate the database") {
            val migration = Migration(
                host = "example.com",
                port = "3338",
                user = "root",
                password = "supersecret",
                schema = "new_db",
                driver = DatabaseDriver.MySql(),
                migrationDir = "some/location"
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


            val databaseMigrator = DatabaseMigrator(
                migrations = listOf(migration),
                flywayWrapper = flywayWrapper,
                dataSourceFactory = dataSourceFactory
            )

            databaseMigrator.migrate()

            verify(flywayWrapper).migrate(dataSource = dataSource, migrationsLocation = "filesystem:some/location")
        }
    }
})
