package acceptance

import io.damo.aspen.Test
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.client.v2.securitygroups.Protocol
import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import pushapps.SecurityGroup
import support.buildTestContext
import support.cleanupCf
import support.listSecurityGroupResources
import support.runPushApps

class SecurityGroupAcceptanceTest : Test({
    val securityGroup = SecurityGroup(
        name = "some-name",
        destination = "0.0.0.0-255.255.255.255",
        protocol = "all"
    )

    val tc = buildTestContext(
        space = "test",
        securityGroups = listOf(securityGroup)
    )

    after {
        cleanupCf(tc, tc.organization, "test")
    }

    describe("create security groups for given organization and space") {
        test("it creates the security groups") {
            val exitCode = runPushApps(tc.configFilePath)
            Assertions.assertThat(exitCode).isEqualTo(0)

            val reactiveCfClient = (tc.cfOperations as DefaultCloudFoundryOperations).cloudFoundryClient
            val cfSecurityGroups = listSecurityGroupResources(reactiveCfClient, tc.securityGroups!!)

            Assertions.assertThat(cfSecurityGroups).anySatisfy { group ->
                assertThat(group.entity.name).isEqualTo(securityGroup.name)

                assertThat(group.entity.rules).hasOnlyOneElementSatisfying { rule ->
                    assertThat(rule.protocol).isEqualTo(Protocol.ALL)
                    assertThat(rule.destination).isEqualTo(securityGroup.destination)
                }
            }
        }
    }
})