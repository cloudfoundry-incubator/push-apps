package unit

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.pivotal.pushapps.FlywayWrapper
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import javax.sql.DataSource

class FlywayWrapperTest : Spek({
    data class TestContext(
        val flyway: Flyway,
        val flywayWrapper: FlywayWrapper,
        val dataSource: DataSource
    )

    fun buildTextContext(): TestContext {
        val flyway = mock<Flyway>()
        val dataSource = mock<DataSource>()

        val flywayWrapper = FlywayWrapper(flyway)

        return TestContext(
            flyway = flyway,
            flywayWrapper =  flywayWrapper,
            dataSource = dataSource
        )
    }

    describe("#migrate") {
        it("migrates the database using the provided datasource and migration location") {
            val (flyway, flywayWrapper, dataSource) = buildTextContext()

            flywayWrapper.migrate(dataSource, "filesytem:some/path")

            verify(flyway).dataSource = dataSource
            verify(flyway).setLocations("filesytem:some/path")
            verify(flyway).migrate()
        }

        it("throws exceptions thrown by flyway") {
            val (flyway, flywayWrapper, dataSource) = buildTextContext()

            whenever(flyway.migrate()).thenThrow(FlywayException())

            assertThatExceptionOfType(FlywayException::class.java).isThrownBy {
                flywayWrapper.migrate(dataSource, "filesytem:some/path")
            }
        }
    }
})
