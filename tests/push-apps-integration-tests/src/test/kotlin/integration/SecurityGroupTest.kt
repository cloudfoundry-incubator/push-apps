package integration

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.client.v2.ClientV2Exception
import org.cloudfoundry.client.v2.securitygroups.CreateSecurityGroupRequest
import org.cloudfoundry.client.v2.securitygroups.Protocol
import org.cloudfoundry.operations.spaces.SpaceDetail
import org.cloudfoundry.pushapps.PushApps
import org.cloudfoundry.pushapps.SecurityGroup
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Mono

class SecurityGroupTest : Spek({
    describe("create security groups for given organization and space") {
        val securityGroup = SecurityGroup(
            name = "some-name",
            destination = "0.0.0.0-255.255.255.255",
            protocol = "all"
        )

        it("it creates the security groups") {
            val tc = buildTestContext(
                space = "secret-space",
                securityGroups = listOf(securityGroup)
            )

            val spaceDetail = mock<SpaceDetail>()

            whenever(tc.cfOperations.spaces().get(any())).thenReturn(Mono.just(spaceDetail))
            whenever(spaceDetail.id).thenReturn("def-42")

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder,
                tc.flyway,
                tc.dataSourceFactory
            )

            val result = pushApps.pushApps()
            Assertions.assertThat(result).isTrue()

            verify(tc.cfOperations.spaces()).get(argForWhich {
                name == "secret-space"
            })

            argumentCaptor<CreateSecurityGroupRequest>().apply {
                verify(tc.cfOperations.cloudFoundryClient.securityGroups()).create(capture())
                assertThat(allValues.size).isEqualTo(1)

                val createSecurityGroupRequest = allValues[0]
                val rule = createSecurityGroupRequest.rules[0]

                assertThat(createSecurityGroupRequest.name).isEqualTo("some-name")
                assertThat(createSecurityGroupRequest.spaceIds).contains("def-42")

                assertThat(rule.destination).isEqualTo("0.0.0.0-255.255.255.255")
                assertThat(rule.protocol).isEqualTo(Protocol.from("all"))
            }
        }

        it("it does not fail if the security group already exists") {
            val tc = buildTestContext(
                space = "test",
                securityGroups = listOf(securityGroup)
            )

            val clientV2Exception = ClientV2Exception(500, 500, "security group name is taken", "500")
            whenever(tc.cfOperations.cloudFoundryClient.securityGroups().create(any())).thenReturn(Mono.error(clientV2Exception))

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder,
                tc.flyway,
                tc.dataSourceFactory
            )


            val result = pushApps.pushApps()
            Assertions.assertThat(result).isTrue()
        }
    }
})
