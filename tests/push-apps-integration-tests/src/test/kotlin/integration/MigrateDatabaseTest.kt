package integration

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions
import org.cloudfoundry.tools.pushapps.config.DatabaseDriver
import org.cloudfoundry.tools.pushapps.config.Migration
import org.cloudfoundry.tools.pushapps.PushApps
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.sql.Connection
import java.sql.Statement

class MigrateDatabaseTest : Spek({
    describe("pushApps interacts with databases by") {
        val migration = Migration(
                host = "example.com",
                port = "3338",
                user = "root",
                password = "supersecret",
                schema = "new_db",
                driver = DatabaseDriver.MySql(),
                migrationDir = "$workingDir/src/test/kotlin/support/dbmigrations",
                repair = false,
                placeholders = emptyMap()
        )

        it("performing the given migrations") {
            val tc = buildTestContext(
                migrations = listOf(migration), organization = "foo_bar_org"
            )

            val mockConnection = mock<Connection>()
            val mockStatement = mock<Statement>()

            whenever(tc.dataSource.connection).thenReturn(mockConnection)
            whenever(mockConnection.createStatement()).thenReturn(mockStatement)

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder,
                tc.flyway,
                tc.dataSourceFactory
            )

            val result = pushApps.pushApps()
            Assertions.assertThat(result).isTrue()

            verify(mockStatement).execute("CREATE DATABASE IF NOT EXISTS new_db;")

            verify(tc.flyway).migrate(
                dataSource = tc.dataSource,
                migrationsLocation = migration.migrationDir,
                repair = false,
                placeholders = emptyMap()
            )
        }
        it("does not create the database if postgres") {
            val postgresMigration = Migration(
                    host = "example.com",
                    port = "3338",
                    user = "root",
                    password = "supersecret",
                    schema = "new_db",
                    driver = DatabaseDriver.Postgres(),
                    migrationDir = "$workingDir/src/test/kotlin/support/dbmigrations",
                    repair = false,
                    placeholders = emptyMap()
            )

            val tc = buildTestContext(
                migrations = listOf(postgresMigration), organization = "foo_bar_org"
            )

            val mockConnection = mock<Connection>()

            whenever(tc.dataSource.connection).thenReturn(mockConnection)

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder,
                tc.flyway,
                tc.dataSourceFactory
            )

            pushApps.pushApps()
            verifyNoMoreInteractions(mockConnection)
        }
    }
})

