package integration

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.operations.organizations.OrganizationSummary
import org.cloudfoundry.pushapps.PushApps
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Flux

class OrganizationTest : Spek({
    describe("pushApps interacts with organizations by") {
        it("creating an organization if it doesn't exist") {
            val tc = buildTestContext(
                organization = "foo_bar_org"
            )

            val orgSummary = mock<OrganizationSummary>()
            whenever(orgSummary.name).thenReturn("foo_bar_org")

            whenever(tc.cfOperations.organizations().list())
                .thenReturn(
                    Flux.fromIterable(emptyList()),
                    Flux.fromIterable(listOf(orgSummary)
                    )
                )

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder,
                tc.flyway,
                tc.dataSourceFactory
            )


            val result = pushApps.pushApps()

            assertThat(result).isTrue()

            verify(tc.cfOperations.organizations()).create(
                argForWhich {
                    organizationName == "foo_bar_org"
                }
            )
        }

        it("not creating an org if it already exists") {
            val tc = buildTestContext(
                organization = "foo_bar_org"
            )

            val orgSummary = mock<OrganizationSummary>()

            whenever(orgSummary.name).thenReturn("foo_bar_org")
            whenever(tc.cfOperations.organizations().list()).thenReturn(Flux.fromIterable(listOf(orgSummary)))

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder,
                tc.flyway,
                tc.dataSourceFactory
            )

            val result = pushApps.pushApps()

            assertThat(result).isTrue()

            verify(tc.cfOperations.organizations(), times(5)).list()
            verifyNoMoreInteractions(tc.cfOperations.organizations())
        }
    }
})
