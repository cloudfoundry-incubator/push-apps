package io.pivotal.pushapps

import org.postgresql.ds.PGSimpleDataSource
import javax.sql.DataSource

fun postgresDataSourceBuilder(dataSource: DataSource?): PostgresDataSourceBuilder {
    if (dataSource !== null) {
        val ds = dataSource as PGSimpleDataSource
        val builder = PostgresDataSourceBuilder()

        builder.user = ds.user
        builder.host = ds.serverName
        builder.databaseName = ds.databaseName
        builder.port = ds.portNumber

        return builder
    }

    return PostgresDataSourceBuilder()
}

class PostgresDataSourceBuilder: DataSourceBuilder {
    override var user: String? = null
    override var host: String? = null
    override var databaseName: String? = null
    override var password: String? = null
    override var port: Int = 0

    override fun build(): PGSimpleDataSource {
        val dataSource = PGSimpleDataSource()

        if (user !== null) dataSource.user = user
        if (host !== null) dataSource.serverName = host
        if (port > 0) dataSource.portNumber = port
        if (databaseName !== null) dataSource.databaseName = databaseName
        if (password !== null) dataSource.password = password

        return dataSource
    }
}
