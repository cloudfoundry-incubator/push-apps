package io.pivotal.pushapps

import org.postgresql.ds.PGSimpleDataSource

class PostgresDataSourceBuilder() {
    var user: String? = null
    var host: String? = null
    var databaseName: String? = null
    var password: String? = null
    var port: Int = 0

    companion object {
        @JvmStatic
        fun postgresDataSourceBuilder(): PostgresDataSourceBuilder {
            return PostgresDataSourceBuilder()
        }
    }

    fun build(): PGSimpleDataSource {
        val dataSource = PGSimpleDataSource()

        if (user !== null) dataSource.user = user
        if (host !== null) dataSource.serverName = host
        if (port > 0) dataSource.portNumber = port
        if (databaseName !== null) dataSource.databaseName = databaseName
        if (password !== null) dataSource.password = password

        return dataSource
    }
}
