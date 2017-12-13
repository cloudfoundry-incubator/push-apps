package integration

import com.nhaarman.mockito_kotlin.*
import org.cloudfoundry.pushapps.PushApps
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.operations.spaces.SpaceSummary
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Flux

class SpaceTest : Spek({
    describe("pushApps interacts with spaces by") {
        it("creating a space in specified org if it doesn't exist") {
            val tc = buildTestContext(
                space = "outer"
            )

            val spaceSummary = mock<SpaceSummary>()
            whenever(spaceSummary.name).thenReturn("outer")

            whenever(tc.cfOperations.spaces().list())
                .thenReturn(
                    Flux.fromIterable(emptyList()),
                    Flux.fromIterable(listOf(spaceSummary))
                )

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder,
                tc.flyway,
                tc.dataSourceFactory
            )

            val result = pushApps.pushApps()

            assertThat(result).isTrue()

            verify(tc.cfOperations.spaces()).create(argForWhich {
                name == "outer"
            })
        }

        it("not creating a space if it already exists") {
            val tc = buildTestContext(
                space = "outer"
            )

            val spaceSummary = mock<SpaceSummary>()
            whenever(spaceSummary.name).thenReturn("outer")
            whenever(tc.cfOperations.spaces().list())
                .thenReturn(Flux.fromIterable(listOf(spaceSummary)))

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder,
                tc.flyway,
                tc.dataSourceFactory
            )

            val result = pushApps.pushApps()

            assertThat(result).isTrue()

            verify(tc.cfOperations.spaces()).get(argForWhich { name == "outer" })
            verify(tc.cfOperations.spaces(), times(5)).list()
            verifyNoMoreInteractions(tc.cfOperations.spaces())
        }
    }
})
