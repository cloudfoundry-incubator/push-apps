package io.pivotal.pushapps

import javax.sql.DataSource

class DataSourceFactory(
    val mySqlDataSourceBuilder: MySqlDataSourceBuilder = mySqlDataSourceBuilder(),
    val postgresDataSourceBuilder: PostgresDataSourceBuilder = postgresDataSourceBuilder()
) {
    fun buildDataSource(migration: Migration): DataSource {
        return when (migration.driver) {
            is DatabaseDriver.MySql -> buildMysqlDataSource(migration)
            is DatabaseDriver.Postgres -> buildPostgresDataSource(migration)
        }
    }

    fun addDatabaseNameToDataSource(dataSource: DataSource, migration: Migration): DataSource {
        val builder = when(migration.driver) {
            is DatabaseDriver.MySql -> mysqlDataSourceFromExisting(dataSource)
            is DatabaseDriver.Postgres -> postgresDataSourceFromExisting(dataSource)
        }

        return builder.apply {
            this.password = migration.password
            this.databaseName = migration.schema
        }.build()
    }

    private fun buildMysqlDataSource(migration: Migration): DataSource {
        return mySqlDataSourceBuilder.apply {
            user = migration.user
            host = migration.host
            port = migration.port.toInt()
            password = migration.password
        }.build()
    }

    private fun buildPostgresDataSource(migration: Migration): DataSource {
        return postgresDataSourceBuilder.apply {
            user = migration.user
            host = migration.host
            port = migration.port.toInt()
            password = migration.password
        }.build()
    }

}
