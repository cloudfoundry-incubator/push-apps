package org.cloudfoundry.tools.pushapps

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.io.IoBuilder
import org.mariadb.jdbc.MariaDbPoolDataSource
import javax.sql.DataSource

fun mySqlDataSourceBuilder(dataSource: DataSource?): MySqlDataSourceBuilder {
    if (dataSource !== null) {
        val ds = dataSource as MariaDbPoolDataSource
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
    private val logger = LogManager.getLogger(MySqlDataSourceBuilder::class.java)

    override fun build(): DataSource {
        val dataSource = MariaDbPoolDataSource()

        if (user !== null) dataSource.user = user
        if (host !== null) dataSource.serverName = host
        if (port > 0) dataSource.port = port
        if (databaseName !== null) dataSource.databaseName = databaseName
        if (password !== null) dataSource.setPassword(password)

        dataSource.logWriter = IoBuilder.forLogger(logger).setLevel(Level.DEBUG).buildPrintWriter()
        return dataSource
    }
}
