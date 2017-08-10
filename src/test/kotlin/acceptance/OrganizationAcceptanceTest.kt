package acceptance

import acceptance.support.buildTestContext
import acceptance.support.cleanupCf
import acceptance.support.runPushApps
import io.damo.aspen.Test
import org.assertj.core.api.Assertions

class OrganizationAcceptanceTest : Test({
    val (cfOperations, cf, configFilePath) = buildTestContext("dewey", "test", emptyArray())

    after {
        cleanupCf(cfOperations, cf, "dewey", "test")
    }

    describe("pushApps interacts with organizations by") {
        test("creating an organization if it doesn't exist") {
            var organizations = cf.listOrganizations()
            Assertions.assertThat(organizations).doesNotContain("dewey")

            val exitCode = runPushApps(configFilePath)
            organizations = cf.listOrganizations()

            Assertions.assertThat(exitCode).isEqualTo(0)
            Assertions.assertThat(organizations).contains("dewey")
        }

        test("not creating an org if it already exists") {
            cf.createOrganizationIfDoesNotExist("dewey")

            val exitCode = runPushApps(configFilePath)
            Assertions.assertThat(exitCode).isEqualTo(0)
        }
    }

})