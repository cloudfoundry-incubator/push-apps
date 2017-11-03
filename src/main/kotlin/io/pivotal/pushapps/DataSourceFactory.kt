package io.pivotal.pushapps

import io.pivotal.pushapps.MySqlDataSourceBuilder.Companion.mySqlDataSourceBuilder
import io.pivotal.pushapps.PostgresDataSourceBuilder.Companion.postgresDataSourceBuilder
import javax.sql.DataSource

class DataSourceFactory(
    val mySqlDataSourceBuilder: MySqlDataSourceBuilder = mySqlDataSourceBuilder(),
    val postgresDataSourceBuilder: PostgresDataSourceBuilder = postgresDataSourceBuilder()
) {
    fun buildDataSource(migration: Migration): DataSource? {
        return when (migration.driver) {
            is DatabaseDriver.MySql -> buildMysqlDataSource(migration)
            is DatabaseDriver.Postgres -> buildPostgresDataSource(migration)
        }
    }

    private fun buildMysqlDataSource(migration: Migration): DataSource {
        return mySqlDataSourceBuilder.apply {
            user = migration.user
            host = migration.host
            port = migration.port.toInt()
            databaseName = migration.schema
            password = migration.password
        }.build()
    }

    private fun buildPostgresDataSource(migration: Migration): DataSource {
        return postgresDataSourceBuilder.apply {
            user = migration.user
            host = migration.host
            port = migration.port.toInt()
            databaseName = migration.schema
            password = migration.password
        }.build()
    }

}
