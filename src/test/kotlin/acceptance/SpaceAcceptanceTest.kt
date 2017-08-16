package acceptance

import acceptance.support.buildTestContext
import acceptance.support.cleanupCf
import acceptance.support.runPushApps
import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat

class SpaceAcceptanceTest : Test({
    val tc = buildTestContext("dewey", "test", emptyArray())

    after {
        cleanupCf(tc, "dewey", "test")
    }

    describe("pushApps interacts with spaces by") {
        test("creating a space in specified org if it doesn't exist") {
            // Make sure space does not exist
            val organizations = tc.cfClient.listOrganizations()
            assertThat(organizations).doesNotContain("dewey")

            val exitCode = runPushApps(tc.configFilePath)

            tc.cfClient.targetOrganization("dewey")
            val spaces = tc.cfClient.listSpaces()

            assertThat(exitCode).isEqualTo(0)
            assertThat(spaces).contains("test")
        }

        test("not creating a space if it already exists") {
            tc.cfClient.createOrganizationIfDoesNotExist("dewey")
            tc.cfClient.targetOrganization("dewey")
            tc.cfClient.createSpaceIfDoesNotExist("test")

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(0)
        }
    }
})