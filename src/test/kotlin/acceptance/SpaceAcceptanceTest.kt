package acceptance

import acceptance.support.buildTestContext
import acceptance.support.cleanupCf
import acceptance.support.runPushApps
import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat

class SpaceAcceptanceTest : Test({
    val (cfOperations, cf, configFilePath) = buildTestContext("dewey", "test", emptyArray())

    after {
        cleanupCf(cfOperations, cf, "dewey", "test")
    }

    describe("pushApps interacts with spaces by") {
        test("creating a space in specified org if it doesn't exist") {
            // Make sure space does not exist
            val organizations = cf.listOrganizations()
            assertThat(organizations).doesNotContain("dewey")

            val exitCode = runPushApps(configFilePath)

            cf.targetOrganization("dewey")
            val spaces = cf.listSpaces()

            assertThat(exitCode).isEqualTo(0)
            assertThat(spaces).contains("test")
        }

        test("not creating a space if it already exists") {
            cf.createOrganizationIfDoesNotExist("dewey")
            cf.targetOrganization("dewey")
            cf.createSpaceIfDoesNotExist("test")

            val exitCode = runPushApps(configFilePath)
            assertThat(exitCode).isEqualTo(0)
        }
    }
})