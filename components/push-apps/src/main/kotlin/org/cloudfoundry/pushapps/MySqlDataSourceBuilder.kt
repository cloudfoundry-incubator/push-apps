package org.cloudfoundry.pushapps

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource
import javax.sql.DataSource

fun mySqlDataSourceBuilder(dataSource: DataSource?): MySqlDataSourceBuilder {
    if (dataSource !== null) {
        val ds = dataSource as MysqlConnectionPoolDataSource
        val builder = MySqlDataSourceBuilder()

        builder.user = ds.user
        builder.host = ds.serverName
        builder.databaseName = ds.databaseName
        builder.port = ds.port

        return builder
    }

    return MySqlDataSourceBuilder()
}

class MySqlDataSourceBuilder : DataSourceBuilder {
    override var user: String? = null
    override var host: String? = null
    override var databaseName: String? = null
    override var password: String? = null
    override var port: Int = 0

    override fun build(): DataSource {
        val dataSource = MysqlConnectionPoolDataSource()

        if (user !== null) dataSource.user = user
        if (host !== null) dataSource.serverName = host
        if (port > 0) dataSource.port = port
        if (databaseName !== null) dataSource.databaseName = databaseName

        if (password !== null) dataSource.setPassword(password)

        return dataSource
    }
}
