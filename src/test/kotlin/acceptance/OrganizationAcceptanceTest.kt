package acceptance

import support.buildTestContext
import support.cleanupCf
import support.runPushApps
import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat

class OrganizationAcceptanceTest : Test({
    val tc = buildTestContext("dewey", "test", emptyList(), emptyList())

    after {
        cleanupCf(tc, "dewey", "test")
    }

    describe("pushApps interacts with organizations by") {
        test("creating an organization if it doesn't exist") {
            var organizations = tc.cfClient.listOrganizations()
            assertThat(organizations).doesNotContain("dewey")

            val exitCode = runPushApps(tc.configFilePath)
            organizations = tc.cfClient.listOrganizations()

            assertThat(exitCode).isEqualTo(0)
            assertThat(organizations).contains("dewey")
        }

        test("not creating an org if it already exists") {
            tc.cfClient.createOrganizationIfDoesNotExist("dewey")

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(0)
        }
    }
})