package acceptance

import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat
import support.buildTestContext
import support.cleanupCf
import support.runPushApps

class SpaceAcceptanceTest : Test({
    val tc = buildTestContext(
        space = "test"
    )

    after {
        cleanupCf(tc, tc.organization, "test")
    }

    describe("pushApps interacts with spaces by") {
        test("creating a space in specified org if it doesn't exist") {
            // Make sure space does not exist
            val organizations = tc.cfClient.listOrganizations()
            assertThat(organizations).doesNotContain(tc.organization)

            val exitCode = runPushApps(tc.configFilePath)

            tc.cfClient.createAndTargetOrganization(tc.organization)
            val spaces = tc.cfClient.listSpaces()

            assertThat(exitCode).isEqualTo(0)
            assertThat(spaces).contains("test")
        }

        test("not creating a space if it already exists") {
            tc.cfClient.createAndTargetOrganization(tc.organization)
            tc.cfClient.createAndTargetSpace("test")

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(0)
        }
    }
})