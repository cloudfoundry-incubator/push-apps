package unit

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource
import com.nhaarman.mockito_kotlin.*
import io.pivotal.pushapps.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.postgresql.ds.PGSimpleDataSource
import javax.sql.DataSource

class DataSourceFactoryTest : Spek({
    describe("#buildDataSource") {
        val mysqlDataSourceBuilder = mock<MySqlDataSourceBuilder>()
        val postgresDataSourceBuilder = mock<PostgresDataSourceBuilder>()

        context("when database driver is mysql") {
            it("builds a mysql data source") {
                val mysqlDataSource = mock<DataSource>()
                whenever(mysqlDataSourceBuilder.build()).thenReturn(mysqlDataSource)

                val migration = Migration(
                    driver = DatabaseDriver.MySql(),
                    port = "42",
                    host = "localhost",
                    schema = "metrics",
                    user = "metrics",
                    password = "secret",
                    migrationDir = "/tmp"
                )

                val dataSourceFactory = DataSourceFactory(
                    mysqlDataSourceBuilder,
                    postgresDataSourceBuilder
                )
                val dataSource = dataSourceFactory.buildDataSource(migration)

                verify(mysqlDataSourceBuilder).user = "metrics"
                verify(mysqlDataSourceBuilder).host = "localhost"
                verify(mysqlDataSourceBuilder).port = 42
                verify(mysqlDataSourceBuilder).password = "secret"
                verify(mysqlDataSourceBuilder).build()
                assertThat(dataSource).isEqualTo(mysqlDataSource)
            }
        }

        context("when database driver is postgres") {
            it("builds a postgres data source") {
                val postgresDataSource = mock<PGSimpleDataSource>()
                whenever(postgresDataSourceBuilder.build()).thenReturn(postgresDataSource)

                val migration = Migration(
                    driver = DatabaseDriver.Postgres(),
                    port = "42",
                    host = "localhost",
                    schema = "metrics",
                    user = "metrics",
                    password = "secret",
                    migrationDir = "/tmp"
                )

                val dataSourceFactory = DataSourceFactory(mysqlDataSourceBuilder, postgresDataSourceBuilder)
                val dataSource = dataSourceFactory.buildDataSource(migration)

                verify(postgresDataSourceBuilder).user = "metrics"
                verify(postgresDataSourceBuilder).host = "localhost"
                verify(postgresDataSourceBuilder).port = 42
                verify(postgresDataSourceBuilder).password = "secret"
                verify(postgresDataSourceBuilder).build()
                assertThat(dataSource).isEqualTo(postgresDataSource)
            }
        }
    }
})
