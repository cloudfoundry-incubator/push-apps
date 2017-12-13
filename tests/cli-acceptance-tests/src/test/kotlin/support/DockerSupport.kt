package support

import org.apache.commons.io.FilenameUtils
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*


class DockerSupport {
    private val acceptanceTestProjectDir = findProjectDir()
    private val acceptanceTestSupport = AcceptanceTestSupport()

    private fun findProjectDir(): String {
        val compiledFileLocation = DockerSupport::class.java.getResource("${DockerSupport::class.simpleName}.class")
        val projectPath = Paths.get(compiledFileLocation.path.toString(), "../../../../../../").toAbsolutePath().toString()
        val normalizedPath: String = FilenameUtils.normalize(projectPath)
        return normalizedPath.trimEnd('/')
    }

    fun startDocker() {
        stopDocker()
        removeStoppedDockerContainers()
        runDockerCompose()

        val dockerHost = acceptanceTestSupport.getEnvOrDefault("INTEGRATION_HOST", "127.0.0.1")

        waitForMysql(dockerHost, "3338", "root", "supersecret")
        waitForPg(dockerHost, "6442", "metrics", "metrics_secret")
    }

    private fun stopDocker() {
        val dockerStopCommand = mutableListOf(
                "docker-compose",
                "-f", "$acceptanceTestProjectDir/src/test/kotlin/support/docker-compose.yml",
                "-p", "java-push-apps",
                "stop"
        )
        val dockerStopProcess = ProcessBuilder(
                dockerStopCommand
        ).inheritIO().start()

        dockerStopProcess.waitFor()
        if (dockerStopProcess.exitValue() != 0) {
            throw Exception("unable to run command: " + dockerStopCommand.joinToString(" "))
        }
    }

    private fun removeStoppedDockerContainers() {
        val dockerRemoveCommand = mutableListOf(
                "docker-compose",
                "-f", "$acceptanceTestProjectDir/src/test/kotlin/support/docker-compose.yml",
                "-p", "java-push-apps",
                "rm", "-f"
        )
        val dockerRemoveProcess = ProcessBuilder(
                dockerRemoveCommand
        ).inheritIO().start()

        dockerRemoveProcess.waitFor()
        if (dockerRemoveProcess.exitValue() != 0) {
            throw Exception("unable to run command: " + dockerRemoveCommand.joinToString(" "))
        }
    }

    private fun runDockerCompose() {
        val dockerComposeCommand = mutableListOf(
                "docker-compose",
                "-f", "$acceptanceTestProjectDir/src/test/kotlin/support/docker-compose.yml",
                "-p", "java-push-apps",
                "up", "-d"
        )

        val dockerComposeProcess = ProcessBuilder(
                dockerComposeCommand
        ).inheritIO().start()

        dockerComposeProcess.waitFor()
        if (dockerComposeProcess.exitValue() != 0) {
            throw Exception("unable to run command: " + dockerComposeCommand.joinToString(" "))
        }
    }

    fun waitForPg(pgHost: String, pgPort: String, pgUser: String, pgPassword: String) {
        var attempts = 0
        while (attempts < 3) {
            try {
                connectToPg(pgHost, pgPort, pgUser, pgPassword)
            } catch (ex: Exception) {
                Thread.sleep(1000)
                attempts++
                continue
            }

            break
        }
    }

    fun connectToPg(pgHost: String, pgPort: String, pgUser: String, pgPassword: String): Connection? {
        val connectionProps = Properties()
        connectionProps.put("user", pgUser)
        connectionProps.put("password", pgPassword)
        Class.forName("org.postgresql.Driver").newInstance()
        return DriverManager.getConnection(
                "jdbc:postgresql://" +
                        pgHost +
                        ":" + pgPort + "/" +
                        "",
                connectionProps)
    }

    fun waitForMysql(mysqlHost: String, mysqlPort: String, mysqlUser: String, mysqlPassword: String) {
        var attempts = 0
        while (attempts < 120) {
            try {
                connectToMysql(mysqlHost, mysqlPort, mysqlUser, mysqlPassword)
            } catch (ex: Exception) {
                Thread.sleep(1000)
                attempts++
                continue
            }

            break
        }
    }

    fun connectToMysql(mysqlHost: String, mysqlPort: String, mysqlUser: String, mysqlPassword: String): Connection? {
        val connectionProps = Properties()
        connectionProps.put("user", mysqlUser)
        connectionProps.put("password", mysqlPassword)
        Class.forName("com.mysql.jdbc.Driver").newInstance()
        return DriverManager.getConnection(
                "jdbc:mysql://" +
                        mysqlHost +
                        ":" + mysqlPort + "/" +
                        "",
                connectionProps)
    }

    fun checkIfDatabaseExists(conn: Connection, dbName: String): Boolean {
        try {
            var query = "SHOW DATABASES;"
            if (conn.metaData.databaseProductName.contains("PostgreSQL")) {
                query = "SELECT datname as Database FROM pg_database WHERE datistemplate = false;"
            }
            val stmt = conn.createStatement()
            var resultset = stmt!!.executeQuery(query)

            if (stmt.execute(query)) {
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
        }

        return false
    }

    fun checkIfTableExists(conn: Connection, dbName: String, tableName: String): Boolean {
        try {
            var query = "SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA='${dbName}';"
            if (conn.metaData.databaseProductName.contains("PostgreSQL")) {
                query = "SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA='public';"
            }

            val stmt = conn.createStatement()

            var resultset = stmt!!.executeQuery(query)

            if (stmt.execute(query)) {
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
        }

        return false
    }

}
