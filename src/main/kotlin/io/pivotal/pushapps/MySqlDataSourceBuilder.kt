package io.pivotal.pushapps

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource

class MySqlDataSourceBuilder() {
    var user: String? = null
    var host: String? = null
    var databaseName: String? = null
    var password: String? = null
    var port: Int = 0

    companion object {
        @JvmStatic
        fun mySqlDataSourceBuilder(): MySqlDataSourceBuilder {
            return MySqlDataSourceBuilder()
        }
    }

    fun build(): MysqlConnectionPoolDataSource {
        val dataSource = MysqlConnectionPoolDataSource()

        if (user !== null) dataSource.user = user
        if (host !== null) dataSource.serverName = host
        if (port > 0) dataSource.port = port
        if (databaseName !== null) dataSource.databaseName = databaseName

        if (password !== null) dataSource.setPassword(password)

        return dataSource
    }
}
