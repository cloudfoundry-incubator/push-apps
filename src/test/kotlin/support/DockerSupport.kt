package support

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*


fun startDocker() {
    stopDocker()
    removeStoppedDockerContainers()
    runDockerCompose()

    val dockerHost = getEnvOrDefault("INTEGRATION_HOST", "127.0.0.1")

    waitForMysql(dockerHost, 3338, "root", "supersecret")
}

private fun stopDocker() {
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
    if (dockerStopProcess.exitValue() != 0) {
        throw Exception("unable to run command: " + dockerStopCommand.joinToString(" "))
    }
}

private fun removeStoppedDockerContainers() {
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
    if (dockerRemoveProcess.exitValue() != 0) {
        throw Exception("unable to run command: " + dockerRemoveCommand.joinToString(" "))
    }
}

private fun runDockerCompose() {
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
    if (dockerComposeProcess.exitValue() != 0) {
        throw Exception("unable to run command: " + dockerComposeCommand.joinToString(" "))
    }
}

fun waitForMysql(mysqlHost: String, mysqlPort: Int, mysqlUser: String, mysqlPassword: String) {
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

fun connectToMysql(mysqlHost: String, mysqlPort: Int, mysqlUser: String, mysqlPassword: String): Connection? {
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

fun checkIfDatabaseExists(conn: Connection, dbName: String): Boolean {
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

fun checkIfTableExists(conn: Connection, dbName: String, tableName: String): Boolean {
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