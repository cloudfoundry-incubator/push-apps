package unit

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.apache.commons.io.FilenameUtils
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.cloudfoundry.tools.pushapps.FlywayWrapper
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.nio.file.Paths
import javax.sql.DataSource

class FlywayWrapperTest : Spek({
    data class TestContext(
        val flyway: Flyway,
        val flywayWrapper: FlywayWrapper,
        val dataSource: DataSource,
        val placeholders: Map<String, String>
    )

    fun buildTextContext(): TestContext {
        val flyway = mock<Flyway>()
        val dataSource = mock<DataSource>()

        val flywayWrapper = FlywayWrapper({ flyway })

        return TestContext(
            flyway = flyway,
            flywayWrapper = flywayWrapper,
            dataSource = dataSource,
            placeholders = mapOf("foo" to "bar")
        )
    }

    fun findSupportPath(): String {
        val compiledFileLocation = FlywayWrapperTest::class.java
            .getResource("${FlywayWrapperTest::class.simpleName}.class")
        val projectPath = Paths.get(compiledFileLocation.path.toString(), "../../../../../../src/test/kotlin/support")
            .toAbsolutePath()
            .toString()
        return FilenameUtils.normalize(projectPath)
    }

    val supportPath = findSupportPath()
    val validMigrationsPath = "$supportPath/dummyMigrations"

    describe("#migrate") {
        it("migrates the database using the provided datasource, migration location, and placeholders") {
            val (flyway, flywayWrapper, dataSource, placeholders) = buildTextContext()

            flywayWrapper.migrate(
                dataSource = dataSource,
                migrationsLocation = validMigrationsPath,
                repair = false,
                placeholders = placeholders
            ).block()

            verify(flyway).dataSource = dataSource
            verify(flyway).setLocations("filesystem:$validMigrationsPath")
            verify(flyway).placeholders = placeholders
            verify(flyway).validate()
            verify(flyway).migrate()
        }

        it("repairs the schema table when repair is requested") {
            val (flyway, flywayWrapper, dataSource, placeholders) = buildTextContext()

            flywayWrapper.migrate(
                dataSource = dataSource,
                migrationsLocation = validMigrationsPath,
                repair = true,
                placeholders = placeholders
            ).block()

            verify(flyway).repair()
        }

        it("throws exceptions thrown by flyway") {
            val (flyway, flywayWrapper, dataSource, placeholders) = buildTextContext()

            whenever(flyway.migrate()).thenThrow(FlywayException())

            assertThatExceptionOfType(FlywayException::class.java).isThrownBy {
                flywayWrapper.migrate(
                    dataSource = dataSource,
                    migrationsLocation = validMigrationsPath,
                    repair = false,
                    placeholders = placeholders
                ).block()
            }
        }

        it("throws a FlywayException if migrations location does not exist or contain migrations") {
            val (flyway, flywayWrapper, dataSource, placeholders) = buildTextContext()
            val invalidMigrationsPath = "$supportPath/doesNotExist"

            println("valid: $validMigrationsPath")
            println("invalid: $invalidMigrationsPath")

            flywayWrapper.migrate(
                dataSource = dataSource,
                migrationsLocation = validMigrationsPath,
                repair = false,
                placeholders = placeholders
            ).block()

            verify(flyway).setLocations("filesystem:$validMigrationsPath")
            verify(flyway).dataSource = dataSource
            verify(flyway).placeholders = placeholders
            verify(flyway).validate()
            verify(flyway).migrate()

            assertThatExceptionOfType(FlywayException::class.java).isThrownBy {
                flywayWrapper.migrate(
                    dataSource = dataSource,
                    migrationsLocation = invalidMigrationsPath,
                    repair = false,
                    placeholders = placeholders
                ).block()
            }
            verifyNoMoreInteractions(flyway)
        }
    }
})
