package org.cloudfoundry.tools.pushapps

import org.cloudfoundry.tools.pushapps.config.DatabaseDriver
import org.cloudfoundry.tools.pushapps.config.Migration
import javax.sql.DataSource

typealias GetDataSourceBuilder = (dataSource: DataSource?) -> DataSourceBuilder

class DataSourceFactory(
    private inline val mySqlBuilder: GetDataSourceBuilder = { mySqlDataSourceBuilder(it) },
    private inline val postgresBuilder: GetDataSourceBuilder = { postgresDataSourceBuilder(it) }
) {
    fun buildDataSource(migration: Migration): DataSource {
        return when (migration.driver) {
            is DatabaseDriver.MySql -> buildMysqlDataSource(migration)
            is DatabaseDriver.Postgres -> buildPostgresDataSource(migration)
        }
    }

    fun addDatabaseNameToDataSource(dataSource: DataSource, migration: Migration): DataSource {
        val builder = when(migration.driver) {
            is DatabaseDriver.MySql -> mySqlBuilder(dataSource)
            is DatabaseDriver.Postgres -> postgresBuilder(dataSource)
        }

        return builder.apply {
            this.password = migration.password
            this.databaseName = migration.schema
        }.build()
    }

    private fun buildMysqlDataSource(migration: Migration): DataSource {
        return mySqlBuilder(null).apply {
            user = migration.user
            host = migration.host
            port = migration.port.toInt()
            password = migration.password
        }.build()
    }

    private fun buildPostgresDataSource(migration: Migration): DataSource {
        return postgresBuilder(null).apply {
            user = migration.user
            host = migration.host
            port = migration.port.toInt()
            databaseName = migration.schema
            password = migration.password
        }.build()
    }
}
