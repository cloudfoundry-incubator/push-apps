package acceptance

import io.damo.aspen.Test
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import pushapps.Migration
import support.buildTestContext
import support.cleanupCf
import support.runPushApps
import support.workingDir
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

class MigrateDatabaseAcceptanceTest : Test({
    val migration = Migration(
        host = "127.0.0.1",
        port = "3338",
        user = "root",
        password = "supersecret",
        schema = "new_db",
        driver = "mysql",
        migrationDir = "$workingDir/src/test/kotlin/support/dbmigrations"
    )

    describe("pushApps interacts with databases by") {
        test("performing the given migrations") {
            val tc = buildTestContext("dewey", "test", emptyList(), emptyList(), emptyList(), listOf(migration))

            startDocker()

            val exitCode = runPushApps(tc.configFilePath)
            Assertions.assertThat(exitCode).isEqualTo(0)

            val conn = connectToMysql("127.0.0.1", 3338, "root", "supersecret")

            assertThat(checkIfDatabaseExists(conn!!, "new_db")).isTrue()

            assertThat(checkIfTableExists(conn, "new_db", "test_table_1")).isTrue()
            assertThat(checkIfTableExists(conn, "new_db", "test_table_2")).isTrue()

            cleanupCf(tc, "dewey", "test")
        }
    }
})

private fun startDocker() {
    val dockerStopCommand = mutableListOf(
        "docker-compose",
        "-f", "$workingDir/src/test/kotlin/support/docker-compose.yml",
        "-p", "java-push-apps",
        "stop"
    )
    val dockerStopProcess = ProcessBuilder(
        dockerStopCommand
    ).inheritIO().start()

    dockerStopProcess.waitFor()

    val dockerRemoveCommand = mutableListOf(
        "docker-compose",
        "-f", "$workingDir/src/test/kotlin/support/docker-compose.yml",
        "-p", "java-push-apps",
        "rm", "-f"
    )
    val dockerRemoveProcess = ProcessBuilder(
        dockerRemoveCommand
    ).inheritIO().start()

    dockerRemoveProcess.waitFor()

    val dockerComposeCommand = mutableListOf(
        "docker-compose",
        "-f", "$workingDir/src/test/kotlin/support/docker-compose.yml",
        "-p", "java-push-apps",
        "up", "-d"
    )

    val dockerComposeProcess = ProcessBuilder(
        dockerComposeCommand
    ).inheritIO().start()

    dockerComposeProcess.waitFor()

    waitForMysql("127.0.0.1", 3338, "root", "supersecret")
}

fun waitForMysql(mysqlHost: String, mysqlPort: Int, mysqlUser: String, mysqlPassword: String) {
    while (true) {
        try {
            connectToMysql(mysqlHost, mysqlPort, mysqlUser, mysqlPassword)
        } catch (ex: Exception) {
            Thread.sleep(1000)
            continue
        }

        break
    }
}

private fun connectToMysql(mysqlHost: String, mysqlPort: Int, mysqlUser: String, mysqlPassword: String): Connection? {
    var conn: Connection? = null
    val connectionProps = Properties()
    connectionProps.put("user", mysqlUser)
    connectionProps.put("password", mysqlPassword)
    try {
        Class.forName("com.mysql.jdbc.Driver").newInstance()
        conn = DriverManager.getConnection(
            "jdbc:" + "mysql" + "://" +
                mysqlHost +
                ":" + mysqlPort + "/" +
                "",
            connectionProps)
    } catch (ex: Exception) {
        throw ex
    }

    return conn
}

private fun checkIfDatabaseExists(conn: Connection, dbName: String): Boolean {
    try {
        val stmt = conn.createStatement()
        var resultset = stmt!!.executeQuery("SHOW DATABASES;")

        if (stmt.execute("SHOW DATABASES;")) {
            resultset = stmt.resultSet
        }

        while (resultset!!.next()) {
            val db = resultset.getString("Database")
            if (db == dbName) {
                return true
            }
        }
    } catch (ex: SQLException) {
        ex.printStackTrace()
        return false
    }

    return false
}

private fun checkIfTableExists(conn: Connection, dbName: String, tableName: String): Boolean {
    try {
        val stmt = conn.createStatement()

        var resultset = stmt!!.executeQuery(
            "SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA='${dbName}';")

        if (stmt.execute(
            "SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA='${dbName}';")) {
            resultset = stmt.resultSet
        }

        while (resultset!!.next()) {
            val table = resultset.getString("TABLE_NAME")
            if (table == tableName) {
                return true
            }
        }
    } catch (ex: SQLException) {
        ex.printStackTrace()
        return false
    }

    return false
}
