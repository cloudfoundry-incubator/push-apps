package unit

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.pushapps.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.postgresql.ds.PGSimpleDataSource
import javax.sql.DataSource

class DataSourceFactoryTest : Spek({
    fun buildTextContext(): Pair<MySqlDataSourceBuilder, PostgresDataSourceBuilder> {
        val mySqlDataSourceBuilder = mock<MySqlDataSourceBuilder>()
        val postgresDataSourceBuilder = mock<PostgresDataSourceBuilder>()

        return Pair(mySqlDataSourceBuilder, postgresDataSourceBuilder)
    }

    describe("#buildDataSource") {
        context("when database driver is mysql") {
            it("builds a mysql data source") {
                val (mySqlDataSourceBuilder, postgresDataSourceBuilder) = buildTextContext()

                val mysqlDataSource = mock<DataSource>()
                whenever(mySqlDataSourceBuilder.build()).thenReturn(mysqlDataSource)

                val migration = Migration(
                    driver = DatabaseDriver.MySql(),
                    port = "42",
                    host = "localhost",
                    schema = "metrics",
                    user = "metrics",
                    password = "secret",
                    migrationDir = "/tmp",
                    repair = false
                )

                val dataSourceFactory = DataSourceFactory(
                    { mySqlDataSourceBuilder },
                    { postgresDataSourceBuilder }
                )
                val dataSource = dataSourceFactory.buildDataSource(migration)

                verify(mySqlDataSourceBuilder).user = "metrics"
                verify(mySqlDataSourceBuilder).host = "localhost"
                verify(mySqlDataSourceBuilder).port = 42
                verify(mySqlDataSourceBuilder).password = "secret"
                verify(mySqlDataSourceBuilder).build()
                verifyNoMoreInteractions(mySqlDataSourceBuilder)
                assertThat(dataSource).isEqualTo(mysqlDataSource)
            }
        }

        context("when database driver is postgres") {
            data class PostgresTestContext(
                val migration: Migration,
                val postgresDataSource: PGSimpleDataSource,
                val dataSourceFactory: DataSourceFactory
            )

            fun buildPostgresTextContext(
                mySqlDataSourceBuilder: MySqlDataSourceBuilder,
                postgresDataSourceBuilder: PostgresDataSourceBuilder
            ): PostgresTestContext {
                val postgresDataSource = mock<PGSimpleDataSource>()
                whenever(postgresDataSourceBuilder.build()).thenReturn(postgresDataSource)

                val migration = Migration(
                    driver = DatabaseDriver.Postgres(),
                    port = "42",
                    host = "localhost",
                    schema = "metrics",
                    user = "metrics",
                    password = "secret",
                    migrationDir = "/tmp",
                    repair = false
                )

                val dataSourceFactory = DataSourceFactory(
                    { mySqlDataSourceBuilder },
                    { postgresDataSourceBuilder }
                )

                return PostgresTestContext(
                    migration,
                    postgresDataSource,
                    dataSourceFactory
                )
            }

            it("builds a postgres data source") {
                val (mySqlDataSourceBuilder, postgresDataSourceBuilder) = buildTextContext()
                val (migration, postgresDataSource, dataSourceFactory) =
                    buildPostgresTextContext(mySqlDataSourceBuilder, postgresDataSourceBuilder)

                val dataSource = dataSourceFactory.buildDataSource(migration)

                verify(postgresDataSourceBuilder).user = "metrics"
                verify(postgresDataSourceBuilder).host = "localhost"
                verify(postgresDataSourceBuilder).port = 42
                verify(postgresDataSourceBuilder).password = "secret"
                verify(postgresDataSourceBuilder).build()
                assertThat(dataSource).isEqualTo(postgresDataSource)
            }

            it("adds the database name") {
                val (mySqlDataSourceBuilder, postgresDataSourceBuilder) = buildTextContext()
                val (migration, _, dataSourceFactory) =
                    buildPostgresTextContext(mySqlDataSourceBuilder, postgresDataSourceBuilder)

                dataSourceFactory.buildDataSource(migration)
                verify(postgresDataSourceBuilder).databaseName = "metrics"
            }
        }
    }

    describe("#addDatabaseNameToDataSource") {
        it("creates a new data source builder from existing data source") {
            val (mySqlDataSourceBuilder, postgresDataSourceBuilder) = buildTextContext()

            val dataSource = mock<DataSource>()
            val newDataSource = mock<DataSource>()

            whenever(mySqlDataSourceBuilder.build()).thenReturn(newDataSource)

            val migration = Migration(
                driver = DatabaseDriver.MySql(),
                port = "42",
                host = "localhost",
                schema = "metrics",
                user = "metrics",
                password = "secret",
                migrationDir = "/tmp",
                repair = false
            )

            var passedDataSource: DataSource? = null
            val dataSourceFactory = DataSourceFactory(
                {
                    passedDataSource = it
                    mySqlDataSourceBuilder
                },
                { postgresDataSourceBuilder }
            )

            val dataSourceWithDbName = dataSourceFactory.addDatabaseNameToDataSource(dataSource, migration)

            assertThat(passedDataSource).isEqualTo(dataSource)
            assertThat(dataSourceWithDbName).isEqualTo(newDataSource)
        }

        it("add databaseName and password to the new builder") {
            val (mySqlDataSourceBuilder, postgresDataSourceBuilder) = buildTextContext()

            val dataSource = mock<DataSource>()
            val newDataSource = mock<DataSource>()

            whenever(mySqlDataSourceBuilder.build()).thenReturn(newDataSource)

            val migration = Migration(
                driver = DatabaseDriver.MySql(),
                port = "42",
                host = "localhost",
                schema = "metrics",
                user = "metrics",
                password = "secret",
                migrationDir = "/tmp",
                repair = false
            )

            val dataSourceFactory = DataSourceFactory(
                { mySqlDataSourceBuilder },
                { postgresDataSourceBuilder }
            )

            dataSourceFactory.addDatabaseNameToDataSource(dataSource, migration)
            verify(mySqlDataSourceBuilder).password = "secret"
            verify(mySqlDataSourceBuilder).databaseName = "metrics"
        }
    }
})
