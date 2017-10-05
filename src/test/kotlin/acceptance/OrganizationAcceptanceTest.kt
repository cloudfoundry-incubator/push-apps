package acceptance

import support.buildTestContext
import support.cleanupCf
import support.runPushApps
import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat

class OrganizationAcceptanceTest : Test({
    val tc = buildTestContext(
        space = "test"
    )

    after {
        cleanupCf(tc, tc.organization, "test")
    }

    describe("pushApps interacts with organizations by") {
        test("creating an organization if it doesn't exist") {
            var organizations = tc.cfClient.listOrganizations()
            assertThat(organizations).doesNotContain(tc.organization)

            val exitCode = runPushApps(tc.configFilePath)
            organizations = tc.cfClient.listOrganizations()

            assertThat(exitCode).isEqualTo(0)
            assertThat(organizations).contains(tc.organization)
        }

        test("not creating an org if it already exists") {
            tc.cfClient.createAndTargetOrganization(tc.organization)

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(0)
        }
    }
})