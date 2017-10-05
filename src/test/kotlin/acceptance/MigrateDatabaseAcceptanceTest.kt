package acceptance

import io.damo.aspen.Test
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import pushapps.Migration
import support.*

class MigrateDatabaseAcceptanceTest : Test({
    val migration = Migration(
        host = getEnvOrDefault("INTEGRATION_HOST", "127.0.0.1"),
        port = "3338",
        user = "root",
        password = "supersecret",
        schema = "new_db",
        driver = "mysql",
        migrationDir = "$workingDir/src/test/kotlin/support/dbmigrations"
    )

    describe("pushApps interacts with databases by") {
        test("performing the given migrations") {
            val tc = buildTestContext(
                space = "test",
                migrations = listOf(migration)
            )

            startDocker()

            val exitCode = runPushApps(tc.configFilePath)
            Assertions.assertThat(exitCode).isEqualTo(0)

            val conn = connectToMysql(migration.host, migration.port, migration.user, migration.password)

            assertThat(checkIfDatabaseExists(conn!!, "new_db")).isTrue()

            assertThat(checkIfTableExists(conn, "new_db", "test_table_1")).isTrue()
            assertThat(checkIfTableExists(conn, "new_db", "test_table_2")).isTrue()

            cleanupCf(tc, tc.organization, "test")
        }
    }
})

